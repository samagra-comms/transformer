package com.uci.transformer.odk;

import android.net.Uri;

import com.uci.transformer.odk.model.Form;
import com.uci.transformer.odk.model.FormDetails;
import com.uci.transformer.odk.openrosa.OpenRosaAPIClient;
import com.uci.transformer.odk.persistance.FormsDao;
import com.uci.transformer.odk.utilities.DocumentFetchResult;
import com.uci.transformer.odk.utilities.FileUtils;
import com.uci.transformer.odk.utilities.MediaFile;
import lombok.extern.slf4j.Slf4j;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.RootTranslator;
import org.javarosa.xform.parse.XFormParser;
import org.kxml2.kdom.Element;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.uci.transformer.odk.utilities.FileUtils.*;

@Slf4j
public class FormDownloader {

    private static final String MD5_COLON_PREFIX = "md5:";
    private static final String TEMP_DOWNLOAD_EXTENSION = ".tempDownload";

    private FormsDao formsDao;

    OpenRosaAPIClient openRosaAPIClient;

    public FormDownloader(OpenRosaAPIClient openRosaAPIClient) {
        this.openRosaAPIClient = openRosaAPIClient;
    }
    private static final String NAMESPACE_OPENROSA_ORG_XFORMS_XFORMS_MANIFEST =
            "http:openrosa.org/xforms/xformsManifest";

    public FormDownloader(FormsDao formsDao, OpenRosaAPIClient openRosaAPIClient) {
        this.formsDao = formsDao;
        this.openRosaAPIClient = openRosaAPIClient;
    }

    public static boolean isXformsManifestNamespacedElement(Element e) {
        return e.getNamespace().equalsIgnoreCase(NAMESPACE_OPENROSA_ORG_XFORMS_XFORMS_MANIFEST);
    }

    private static class TaskCancelledException extends Exception {
        private final File file;

        TaskCancelledException(File file) {
            super("Task was cancelled during processing of " + file);
            this.file = file;
        }

        TaskCancelledException() {
            super("Task was cancelled");
            this.file = null;
        }
    }

    public void downloadForms(List<FormDetails> toDownload) {
        int total = toDownload.size();
        int count = 1;
        for (FormDetails fd : toDownload) {
            try {
                processOneForm(total, count++, fd);
            } catch (TaskCancelledException cd) {
                break;
            }
        }
    }

    /**
     * Processes one form download.
     *
     * @param total the total number of forms being downloaded by this task
     * @param count the number of this form
     * @param fd    the FormDetails
     * @return an empty string for success, or a nonblank string with one or more error messages
     * @throws TaskCancelledException to signal that form downloading is to be canceled
     */
    private void processOneForm(int total, int count, FormDetails fd) throws TaskCancelledException {


        String tempMediaPath = new File("/tmp/forms2").getAbsolutePath();
        final String finalMediaPath;
        FileResult fileResult = null;
        try {
            fileResult = downloadXform(fd.getFormName(), fd.getDownloadUrl());

            if (fd.getManifestUrl() != null) {
                finalMediaPath = FileUtils.constructMediaPath(
                        fileResult.getFile().getAbsolutePath());
                String error = downloadManifestAndMediaFiles(tempMediaPath, finalMediaPath, fd,
                        count, total);
            } else {
                log.info("No Manifest for: %s", fd.getFormName());
            }
        } catch (TaskCancelledException e) {
            log.info(e.getMessage());
            cleanUp(fileResult, e.file, tempMediaPath);
            throw e;
        } catch (Exception e) {
            log.debug(getExceptionMessage(e));
        }

        if (fileResult == null) {
            log.debug("Downloading Xform failed.");
        }

        Map<String, String> parsedFields = null;
        if (fileResult.isNew) {
            try {
                final long start = System.currentTimeMillis();
                log.error("Parsing document %s", fileResult.file.getAbsolutePath());

                File tmpLastSaved = new File(tempMediaPath, LAST_SAVED_FILENAME);
                write(tmpLastSaved, STUB_XML.getBytes(Charset.forName("UTF-8")));
                ReferenceManager.instance().reset();
//                ReferenceManager.instance().addReferenceFactory(new FileReferenceFactory(tempMediaPath));
                ReferenceManager.instance().addSessionRootTranslator(new RootTranslator("jr:file-csv/", "jr:file/"));

                parsedFields = FileUtils.getMetadataFromFormDefinition(fileResult.file);
                ReferenceManager.instance().reset();
                FileUtils.deleteAndReport(tmpLastSaved);

                log.info("Parse finished in %.3f seconds.",
                        (System.currentTimeMillis() - start) / 1000F);
                installEverything(tempMediaPath, fileResult, parsedFields);
            } catch (RuntimeException e) {
                log.debug(e.getMessage());
            }
        }
    }

    boolean installEverything(String tempMediaPath, FileResult fileResult, Map<String, String> parsedFields) {
        Form uriResult = null;
        try {
            uriResult = findExistingOrCreateNewUri(fileResult.file, parsedFields);
            if (uriResult != null) {
//                log.error("Form uri = %s, isNew = %b", uriResult.getUri().toString(), uriResult.isNew());

                if (tempMediaPath != null) {
                    File formMediaPath = new File(uriResult.getFormMediaPath());
                    FileUtils.moveMediaFiles(tempMediaPath, formMediaPath);
                }
                return true;
            } else {
                log.error("Form uri = null");
            }
        } catch (IOException e) {
            log.error("CP-41 => " + e.getMessage());
//
//            if (uriResult.isNew() && fileResult.isNew()) {
//                Uri uri = uriResult.getUri();
//                log.error("The form is new. We should delete the entire form.");
////                int deletedCount = Collect.getInstance().getContentResolver().delete(uri,
////                        null, null);
////                log.error("Deleted %d rows using uri %s", deletedCount, uri.toString());
//            }
        }
        return false;
    }

    private void cleanUp(FileResult fileResult, File fileOnCancel, String tempMediaPath) {
        if (fileResult == null) {
            log.error("The user cancelled (or an exception happened) the download of a form at the "
                    + "very beginning.");
        } else {
            String md5Hash = FileUtils.getMd5Hash(fileResult.file);
            if (md5Hash != null) {
                formsDao.deleteFormsFromMd5Hash(md5Hash);
            }
            FileUtils.deleteAndReport(fileResult.getFile());
        }

        FileUtils.deleteAndReport(fileOnCancel);

        if (tempMediaPath != null) {
            FileUtils.purgeMediaPath(tempMediaPath);
        }
    }

    private String getExceptionMessage(Exception e) {
        String msg = e.getMessage();
        if (msg == null) {
            msg = e.toString();
        }
        log.error("CP-6" + msg);

        if (e.getCause() != null) {
            msg = e.getCause().getMessage();
            if (msg == null) {
                msg = e.getCause().toString();
            }
        }
        return msg;
    }

    /**
     * Creates a new form in the database, if none exists with the same absolute path. Returns
     * information with the URI, media path, and whether the form is new.
     *
     * @param formFile the form definition file
     * @param formInfo certain fields extracted from the parsed XML form, such as title and form ID
     * @return a {@link UriResult} object
     */
    private Form findExistingOrCreateNewUri(File formFile, Map<String, String> formInfo) {
        final Uri uri;
        final String formFilePath = formFile.getAbsolutePath();
        String mediaPath = FileUtils.constructMediaPath(formFilePath);
        final boolean isNew;

        FileUtils.checkMediaPath(new File(mediaPath));


        List<Form> forms = formsDao.getFormsCursorForFormFilePath(formFile.getAbsolutePath());
        isNew = forms.size() <= 0;
        Form form;
        if (isNew) {
            form = saveNewForm(formInfo, formFile, mediaPath);
        } else {
            form = forms.get(0);

            //TODO: Add Media Path (relevant)
            //uri = Uri.withAppendedPath(Uri.parse("content://" + "org.odk.collect.android.provider.odk.forms" + "/forms"),"");
            //mediaPath = "";
            //mediaPath = new StoragePathProvider().getAbsoluteFormFilePath(cursor.getString(cursor.getColumnIndex(FormsColumns.FORM_MEDIA_PATH)));
        }
        return form;
    }

    private Form saveNewForm(Map<String, String> formInfo, File formFile, String mediaPath) {

        return formsDao.saveForm(formInfo, formFile, mediaPath);
    }

    /**
     * Takes the formName and the URL and attempts to download the specified file. Returns a file
     * object representing the downloaded file.
     */
    FileResult downloadXform(String formName, String url) throws Exception {
        String rootName = FormNameUtils.formatFilenameFromFormName(formName);

        String path = "/tmp/forms2" + File.separator + rootName + ".xml";
        int i = 2;
        File f = new File(path);
        while (f.exists()) {
            path = "/tmp/forms2" + File.separator + rootName + "_" + i + ".xml";
            f = new File(path);
            i++;
        }

        downloadFile(f, url);
        boolean isNew = true;
        List<Form> form = formsDao.getFormsCursorForMd5Hash(FileUtils.getMd5Hash(f));
        if (form.size() > 0) {
            Form form1 = form.get(0);
            isNew = false;
            log.error("A duplicate file has been found, we need to remove the downloaded file "
                    + "and return the other one.");
            FileUtils.deleteAndReport(f);
            f = new File(form1.getFormFilePath());
            log.error("Will use %s", form1.getFormFilePath());
        }
        return new FileResult(f, isNew);
    }

    /**
     * Common routine to download a document from the downloadUrl and save the contents in the file
     * 'file'. Shared by media file download and form file download.
     * <p>
     * SurveyCTO: The file is saved into a temp folder and is moved to the final place if everything
     * is okay, so that garbage is not left over on cancel.
     *
     * @param file        the final file
     * @param downloadUrl the url to get the contents from.
     */
    private void downloadFile(File file, String downloadUrl) throws IOException, TaskCancelledException, URISyntaxException, Exception {

        File tempFile = File.createTempFile(file.getName(), TEMP_DOWNLOAD_EXTENSION, new File("/tmp/forms2"));
        boolean success = false;
        int attemptCount = 0;
        final int MAX_ATTEMPT_COUNT = 2;
        while (!success && ++attemptCount <= MAX_ATTEMPT_COUNT) {
            log.info(String.format("Started downloading to %s from %s", tempFile.getAbsolutePath(), downloadUrl));

            InputStream is = null;
            OutputStream os = null;

            try {
                is = openRosaAPIClient.getFile(downloadUrl, null);
                os = new FileOutputStream(tempFile);

                byte[] buf = new byte[4096];
                int len;
                while ((len = is.read(buf)) > 0) {
                    os.write(buf, 0, len);
                }
                os.flush();
                success = true;

            } catch (Exception e) {
                log.error("CP-2" + e.toString());
                FileUtils.deleteAndReport(tempFile);
                if (attemptCount == MAX_ATTEMPT_COUNT) {
                    throw e;
                }
            } finally {
                if (os != null) {
                    try {
                        os.close();
                    } catch (Exception e) {
                        log.error("CP-4" + e.getMessage());
                    }
                }
                if (is != null) {
                    try {
                        final long count = 1024L;
                        while (is.skip(count) == count) {
                        }
                    } catch (Exception e) {
                    }
                    try {
                        is.close();
                    } catch (Exception e) {
                        log.error("CP-3" + e.getMessage());
                    }
                }
            }

        }

        log.error(String.format("Completed downloading of %s. It will be moved to the proper path...",
                tempFile.getAbsolutePath()));

        FileUtils.deleteAndReport(file);

        String errorMessage = FileUtils.copyFile(tempFile, file);

        if (file.exists()) {
            log.error(String.format("Copied %s over %s", tempFile.getAbsolutePath(), file.getAbsolutePath()));
            // FileUtils.deleteAndReport(tempFile);
        } else {
            String msg = String.format("Could not copy \\'%1$s\\' over \\'%2$s\\'. Reason: %3$s",
                    tempFile.getAbsolutePath(), file.getAbsolutePath(), errorMessage);
            log.error(msg);
            throw new RuntimeException(msg);
        }
    }

    private static class UriResult {

        private final Uri uri;
        private final String mediaPath;
        private final boolean isNew;

        private UriResult(Uri uri, String mediaPath, boolean isNew) {
            this.uri = uri;
            this.mediaPath = mediaPath;
            this.isNew = isNew;
        }

        private Uri getUri() {
            return uri;
        }

        private String getMediaPath() {
            return mediaPath;
        }

        private boolean isNew() {
            return isNew;
        }
    }

    static class FileResult {

        private final File file;
        private final boolean isNew;

        FileResult(File file, boolean isNew) {
            this.file = file;
            this.isNew = isNew;
        }

        private File getFile() {
            return file;
        }

        private boolean isNew() {
            return isNew;
        }
    }

    String downloadManifestAndMediaFiles(String tempMediaPath, String finalMediaPath,
                                         FormDetails fd, int count,
                                         int total) throws Exception {
        if (fd.getManifestUrl() == null) {
            return null;
        }

        List<MediaFile> files = new ArrayList<>();

        DocumentFetchResult result = openRosaAPIClient.getXML(fd.getManifestUrl());

        if (result.errorMessage != null) {
            return result.errorMessage;
        }

        String errMessage = String.format("Error while accessing %s: ", fd.getManifestUrl());

        if (!result.isOpenRosaResponse) {
            errMessage += "Manifest reply does not report an OpenRosa version — bad server?";
            log.error(errMessage);
            return errMessage;
        }

        Element manifestElement = result.doc.getRootElement();
        if (!manifestElement.getName().equals("manifest")) {
            errMessage +=
                   String.format("Root element is not &lt;manifest\\&gt; — was %s",
                            manifestElement.getName());
            log.error(errMessage);
            return errMessage;
        }
        String namespace = manifestElement.getNamespace();
        if (!isXformsManifestNamespacedElement(manifestElement)) {
            errMessage += String.format("Root element Namespace is incorrect: %s", namespace);
            log.error(errMessage);
            return errMessage;
        }
        int elements = manifestElement.getChildCount();
        for (int i = 0; i < elements; ++i) {
            if (manifestElement.getType(i) != Element.ELEMENT) {
                continue;
            }
            Element mediaFileElement = manifestElement.getElement(i);
            if (!isXformsManifestNamespacedElement(mediaFileElement)) {
                continue;
            }
            String name = mediaFileElement.getName();
            if (name.equalsIgnoreCase("mediaFile")) {
                String filename = null;
                String hash = null;
                String downloadUrl = null;
                int childCount = mediaFileElement.getChildCount();
                for (int j = 0; j < childCount; ++j) {
                    if (mediaFileElement.getType(j) != Element.ELEMENT) {
                        continue;
                    }
                    Element child = mediaFileElement.getElement(j);
                    if (!isXformsManifestNamespacedElement(child)) {
                        continue;
                    }
                    String tag = child.getName();
                    switch (tag) {
                        case "filename":
                            filename = XFormParser.getXMLText(child, true);
                            if (filename != null && filename.length() == 0) {
                                filename = null;
                            }
                            break;
                        case "hash":
                            hash = XFormParser.getXMLText(child, true);
                            if (hash != null && hash.length() == 0) {
                                hash = null;
                            }
                            break;
                        case "downloadUrl":
                            downloadUrl = XFormParser.getXMLText(child, true);
                            if (downloadUrl != null && downloadUrl.length() == 0) {
                                downloadUrl = null;
                            }
                            break;
                    }
                }
                if (filename == null || downloadUrl == null || hash == null) {
                    errMessage +=
                           String.format("Manifest entry %s is missing one or more tags: filename, hash, or downloadUrl",
                                    Integer.toString(i));
                    log.error(errMessage);
                    return errMessage;
                }
                files.add(new MediaFile(filename, hash, downloadUrl));
            }
        }

        log.info("Downloading %d media files.", files.size());
        int mediaCount = 0;
        if (!files.isEmpty()) {
            File tempMediaDir = new File(tempMediaPath);
            File finalMediaDir = new File(finalMediaPath);

            FileUtils.checkMediaPath(tempMediaDir);
            FileUtils.checkMediaPath(finalMediaDir);

            for (MediaFile toDownload : files) {
                ++mediaCount;


                try {
                    File finalMediaFile = new File(finalMediaDir, toDownload.getFilename());
                    File tempMediaFile = new File(tempMediaDir, toDownload.getFilename());

                    if (!finalMediaFile.exists()) {
                        downloadFile(tempMediaFile, toDownload.getDownloadUrl());
                    } else {
                        String currentFileHash = FileUtils.getMd5Hash(finalMediaFile);
                        String downloadFileHash = getMd5Hash(toDownload.getHash());

                        if (currentFileHash != null && downloadFileHash != null && !currentFileHash.contentEquals(downloadFileHash)) {
                                 FileUtils.deleteAndReport(finalMediaFile);
                            downloadFile(tempMediaFile, toDownload.getDownloadUrl());
                        } else {

                            log.info("Skipping media file fetch -- file hashes identical: %s",
                                    finalMediaFile.getAbsolutePath());
                        }
                    }
                } catch (Exception e) {
                    return e.getLocalizedMessage();
                }
            }
        }
        return null;
    }

    public static String getMd5Hash(String hash) {
        return hash == null || hash.isEmpty() ? null : hash.substring(MD5_COLON_PREFIX.length());
    }
}


