package com.uci.transformer.odk.utilities;

import android.net.Uri;
import com.uci.transformer.odk.FormDownloader;
import com.uci.transformer.odk.model.Form;
import com.uci.transformer.odk.model.FormDetails;
import com.uci.transformer.odk.openrosa.OpenRosaAPIClient;
import com.uci.transformer.odk.persistance.FormsDao;
import com.uci.transformer.odk.persistance.JsonDB;
import lombok.extern.slf4j.Slf4j;
import org.javarosa.xform.parse.XFormParser;
import org.kxml2.kdom.Element;
import org.springframework.lang.Nullable;

import java.io.File;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


@Slf4j
public class FormListDownloader {

    private void clearTemporaryCredentials(@Nullable String url) {
        if (url != null) {
            String host = Uri.parse(url).getHost();

            if (host != null) {
                webCredentialsUtils.clearCredentials(url);
            }
        }
    }

    private boolean isThisFormAlreadyDownloaded(String formId) {
        return false;
    }

    // used to store error message if one occurs
    public static final String DL_ERROR_MSG = "dlerrormessage";
    public static final String DL_AUTH_REQUIRED = "dlauthrequired";

    private static final String NAMESPACE_OPENROSA_ORG_XFORMS_XFORMS_LIST =
            "http://openrosa.org/xforms/xformsList";

    private final WebCredentialsUtils webCredentialsUtils;
    private final OpenRosaAPIClient openRosaAPIClient;

    public FormListDownloader(
            OpenRosaAPIClient openRosaAPIClient,
            WebCredentialsUtils webCredentialsUtils
    ) {
        this.openRosaAPIClient = openRosaAPIClient;
        this.webCredentialsUtils = webCredentialsUtils;
    }

    public HashMap<String, FormDetails> downloadFormList(boolean alwaysCheckMediaFiles) {
        HashMap<String, FormDetails> dd = downloadFormList(null, null, null, alwaysCheckMediaFiles);
        log.debug(dd.toString());
        return dd;
    }

    public HashMap<String, FormDetails> downloadFormList(@Nullable String url, @Nullable String username,
                                                         @Nullable String password, boolean alwaysCheckMediaFiles) {

        String downloadListUrl =  System.getenv("ODK_URL") + "/formList";

        // We populate this with available forms from the specified server.
        // <formname, details>
        HashMap<String, FormDetails> formList = new HashMap<>();

        if (url != null) {

            if (username != null && password != null) {
                webCredentialsUtils.saveCredentials(url, username, password);
            } else {
                webCredentialsUtils.clearCredentials(url);
            }
        }

        DocumentFetchResult result = openRosaAPIClient.getXML(downloadListUrl);

        clearTemporaryCredentials(url);

        // If we can't get the document, return the error, cancel the task
        if (result.errorMessage != null) {
            if (result.responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                formList.put(DL_AUTH_REQUIRED, new FormDetails(result.errorMessage));
            } else {
                formList.put(DL_ERROR_MSG, new FormDetails(result.errorMessage));
            }
            return formList;
        }

        if (result.isOpenRosaResponse) {
            // Attempt OpenRosa 1.0 parsing
            Element xformsElement = result.doc.getRootElement();
            if (!xformsElement.getName().equals("xforms")) {
                String error = "root element is not <xforms> : " + xformsElement.getName();
                log.error("Parsing OpenRosa reply -- %s", error);
                formList.put(
                        DL_ERROR_MSG,
                        new FormDetails(error));
                return formList;
            }
            String namespace = xformsElement.getNamespace();
            if (!isXformsListNamespacedElement(xformsElement)) {
                String error = "root element namespace is incorrect:" + namespace;
                log.error("Parsing OpenRosa reply -- %s", error);
                formList.put(
                        DL_ERROR_MSG,
                        new FormDetails(error));
                return formList;
            }
            int elements = xformsElement.getChildCount();
            for (int i = 0; i < elements; ++i) {
                if (xformsElement.getType(i) != Element.ELEMENT) {
                    // e.g., whitespace (text)
                    continue;
                }
                Element xformElement = xformsElement.getElement(i);
                if (!isXformsListNamespacedElement(xformElement)) {
                    // someone else's extension?
                    continue;
                }
                String name = xformElement.getName();
                if (!name.equalsIgnoreCase("xform")) {
                    // someone else's extension?
                    continue;
                }

                // this is something we know how to interpret
                String formId = null;
                String formName = null;
                String version = null;
                String majorMinorVersion = null;
                String description = null;
                String downloadUrl = null;
                String manifestUrl = null;
                String hash = null;
                // don't process descriptionUrl
                int fieldCount = xformElement.getChildCount();
                for (int j = 0; j < fieldCount; ++j) {
                    if (xformElement.getType(j) != Element.ELEMENT) {
                        // whitespace
                        continue;
                    }
                    Element child = xformElement.getElement(j);
                    if (!isXformsListNamespacedElement(child)) {
                        // someone else's extension?
                        continue;
                    }
                    String tag = child.getName();
                    switch (tag) {
                        case "formID":
                            formId = XFormParser.getXMLText(child, true);
                            if (formId != null && formId.length() == 0) {
                                formId = null;
                            }
                            break;
                        case "name":
                            formName = XFormParser.getXMLText(child, true);
                            if (formName != null && formName.length() == 0) {
                                formName = null;
                            }
                            break;
                        case "version":
                            version = XFormParser.getXMLText(child, true);
                            if (version != null && version.length() == 0) {
                                version = null;
                            }
                            break;
                        case "majorMinorVersion":
                            majorMinorVersion = XFormParser.getXMLText(child, true);
                            if (majorMinorVersion != null && majorMinorVersion.length() == 0) {
                                majorMinorVersion = null;
                            }
                            break;
                        case "descriptionText":
                            description = XFormParser.getXMLText(child, true);
                            if (description != null && description.length() == 0) {
                                description = null;
                            }
                            break;
                        case "downloadUrl":
                            downloadUrl = XFormParser.getXMLText(child, true);
                            if (downloadUrl != null && downloadUrl.length() == 0) {
                                downloadUrl = null;
                            }
                            break;
                        case "manifestUrl":
                            manifestUrl = XFormParser.getXMLText(child, true);
                            if (manifestUrl != null && manifestUrl.length() == 0) {
                                manifestUrl = null;
                            }
                            break;
                        case "hash":
                            hash = XFormParser.getXMLText(child, true);
                            if (hash != null && hash.length() == 0) {
                                hash = null;
                            }
                            break;
                    }
                }
                if (formId == null || downloadUrl == null || formName == null) {
                    String error =
                            "Forms list entry " + Integer.toString(i)
                                    + " has missing or empty tags: formID, name, or downloadUrl";
                    log.error("Parsing OpenRosa reply -- %s", error);
                    formList.clear();
                    formList.put(
                            DL_ERROR_MSG,
                            new FormDetails(error));
                    return formList;
                }
                boolean isNewerFormVersionAvailable = false;
                boolean areNewerMediaFilesAvailable = false;
                ManifestFile manifestFile = null;
                if (isThisFormAlreadyDownloaded(formId)) {
                    isNewerFormVersionAvailable = isNewerFormVersionAvailable(FormDownloader.getMd5Hash(hash));
                    if ((!isNewerFormVersionAvailable || alwaysCheckMediaFiles) && manifestUrl != null) {
                        manifestFile = getManifestFile(manifestUrl);
                        if (manifestFile != null) {
                            List<MediaFile> newMediaFiles = manifestFile.getMediaFiles();
                            if (newMediaFiles != null && !newMediaFiles.isEmpty()) {
                                areNewerMediaFilesAvailable = areNewerMediaFilesAvailable(formId, version, newMediaFiles);
                            }
                        }
                    }
                }
                formList.put(formId, new FormDetails(formName, downloadUrl, manifestUrl, formId,
                        (version != null) ? version : majorMinorVersion, hash,
                        manifestFile != null ? manifestFile.getHash() : null,
                        isNewerFormVersionAvailable, areNewerMediaFilesAvailable));
            }
        } else {
            // Aggregate 0.9.x mode...
            // populate HashMap with form names and urls
            Element formsElement = result.doc.getRootElement();
            int formsCount = formsElement.getChildCount();
            String formId = null;
            for (int i = 0; i < formsCount; ++i) {
                if (formsElement.getType(i) != Element.ELEMENT) {
                    // whitespace
                    continue;
                }
                Element child = formsElement.getElement(i);
                String tag = child.getName();
                if (tag.equals("formID")) {
                    formId = XFormParser.getXMLText(child, true);
                    if (formId != null && formId.length() == 0) {
                        formId = null;
                    }
                }
                if (tag.equalsIgnoreCase("form")) {
                    String formName = XFormParser.getXMLText(child, true);
                    if (formName != null && formName.length() == 0) {
                        formName = null;
                    }
                    String downloadUrl = child.getAttributeValue(null, "url");
                    downloadUrl = downloadUrl.trim();
                    if (downloadUrl.length() == 0) {
                        downloadUrl = null;
                    }
                    if (formName == null) {
                        String error =
                                "Forms list entry " + Integer.toString(i)
                                        + " is missing form name or url attribute";
                        log.error("Parsing OpenRosa reply -- %s", error);
                        formList.clear();
                        formList.put(
                                DL_ERROR_MSG,
                                new FormDetails(String.format("The server has not provided an available-forms document compatible with Aggregate 0.9.x. : %s", error)));
                        return formList;
                    }
                    formList.put(formName,
                            new FormDetails(formName, downloadUrl, null, formId, null, null, null, false, false));

                    formId = null;
                }
            }
        }
        return formList;
    }

    private ManifestFile getManifestFile(String manifestUrl) {
        if (manifestUrl == null) {
            return null;
        }

        DocumentFetchResult result = openRosaAPIClient.getXML(manifestUrl);

        if (result.errorMessage != null) {
            return null;
        }

        String errMessage = String.format("Error while accessing %s: ", manifestUrl);

        if (!result.isOpenRosaResponse) {
            errMessage += "Manifest reply does not report an OpenRosa version — bad server?";
            log.error(errMessage);
            return null;
        }

        // Attempt OpenRosa 1.0 parsing
        Element manifestElement = result.doc.getRootElement();
        if (!manifestElement.getName().equals("manifest")) {
            errMessage +=
                    String.format("Root element is not &lt;manifest\\&gt; — was %s",
                            manifestElement.getName());
            log.error(errMessage);
            return null;
        }
        String namespace = manifestElement.getNamespace();
        if (!FormDownloader.isXformsManifestNamespacedElement(manifestElement)) {
            errMessage += String.format("Root element Namespace is incorrect: %s", namespace);
            log.error(errMessage);
            return null;
        }
        int elements = manifestElement.getChildCount();
        List<MediaFile> files = new ArrayList<>();
        for (int i = 0; i < elements; ++i) {
            if (manifestElement.getType(i) != Element.ELEMENT) {
                // e.g., whitespace (text)
                continue;
            }
            Element mediaFileElement = manifestElement.getElement(i);
            if (!FormDownloader.isXformsManifestNamespacedElement(mediaFileElement)) {
                // someone else's extension?
                continue;
            }
            String name = mediaFileElement.getName();
            if (name.equalsIgnoreCase("mediaFile")) {
                String filename = null;
                String hash = null;
                String downloadUrl = null;
                // don't process descriptionUrl
                int childCount = mediaFileElement.getChildCount();
                for (int j = 0; j < childCount; ++j) {
                    if (mediaFileElement.getType(j) != Element.ELEMENT) {
                        // e.g., whitespace (text)
                        continue;
                    }
                    Element child = mediaFileElement.getElement(j);
                    if (!FormDownloader.isXformsManifestNamespacedElement(child)) {
                        // someone else's extension?
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
                    return null;
                }
                files.add(new MediaFile(filename, hash, downloadUrl));
            }
        }

        return new ManifestFile(result.getHash(), files);
    }

    private boolean isNewerFormVersionAvailable(String md5Hash) {
        if (md5Hash == null) {
            return false;
        }
        List<Form> formList = null;
        formList = new FormsDao(JsonDB.getInstance().getDB()).getFormsCursorForMd5Hash(md5Hash);
        return formList != null && formList.size() == 0;
    }

    private boolean areNewerMediaFilesAvailable(String formId, String formVersion, List<MediaFile> newMediaFiles) {
        String mediaDirPath = null;
        mediaDirPath = new FormsDao(JsonDB.getInstance().getDB()).getFormMediaPath(formId, formVersion);
        if (mediaDirPath != null) {
            File[] localMediaFiles = new File(mediaDirPath).listFiles();
            if (localMediaFiles != null) {
                for (MediaFile newMediaFile : newMediaFiles) {
                    if (!isMediaFileAlreadyDownloaded(localMediaFiles, newMediaFile)) {
                        return true;
                    }
                }
            } else if (!newMediaFiles.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private static boolean isMediaFileAlreadyDownloaded(File[] localMediaFiles, MediaFile newMediaFile) {
        // TODO Zip files are ignored we should find a way to take them into account too
        if (newMediaFile.getFilename().endsWith(".zip")) {
            return true;
        }

        String mediaFileHash = newMediaFile.getHash();
        mediaFileHash = mediaFileHash.substring(4, mediaFileHash.length());
        for (File localMediaFile : localMediaFiles) {
            if (mediaFileHash.equals(FileUtils.getMd5Hash(localMediaFile))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isXformsListNamespacedElement(Element e) {
        return e.getNamespace().equalsIgnoreCase(NAMESPACE_OPENROSA_ORG_XFORMS_XFORMS_LIST);
    }
}