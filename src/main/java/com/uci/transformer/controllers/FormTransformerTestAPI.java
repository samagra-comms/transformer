package com.uci.transformer.controllers;


import com.uci.transformer.odk.FormDownloader;
import com.uci.transformer.odk.MenuManager;
import com.uci.transformer.odk.ODKConsumerReactive;
import com.uci.transformer.odk.ServiceResponse;
import com.uci.transformer.odk.model.Form;
import com.uci.transformer.odk.model.FormDetails;
import com.uci.transformer.odk.openrosa.OpenRosaAPIClient;
import com.uci.transformer.odk.openrosa.OpenRosaHttpInterface;
import com.uci.transformer.odk.openrosa.okhttp.OkHttpConnection;
import com.uci.transformer.odk.openrosa.okhttp.OkHttpOpenRosaServerClientProvider;
import com.uci.transformer.odk.persistance.FormsDao;
import com.uci.transformer.odk.persistance.JsonDB;
import com.uci.transformer.odk.utilities.FormListDownloader;
import com.uci.transformer.odk.utilities.WebCredentialsUtils;
import com.uci.utils.cache.service.RedisCacheService;

import lombok.SneakyThrows;
import lombok.extern.java.Log;
import okhttp3.OkHttpClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Log
@RestController
public class FormTransformerTestAPI {

    private static final String template = "Hello, %s!";
    private final AtomicLong counter = new AtomicLong();
    
    @Autowired
    public RedisCacheService redisCacheService;

    private void downloadForms() {
        //Empty the database and folder
        FormsDao dao;
        try{
            File directoryToDelete = new File("/tmp/forms2");
            FileSystemUtils.deleteRecursively(directoryToDelete);
            dao = new FormsDao(JsonDB.getInstance().getDB());
            dao.deleteFormsDatabase();
        }catch (Exception e){}

        //Create a folder /tmp/forms
        new File("/tmp/forms2").mkdirs();

        //Download fresh
        OpenRosaHttpInterface openRosaHttpInterface = new OkHttpConnection(
                new OkHttpOpenRosaServerClientProvider(new OkHttpClient()),
                null,
                "userAgent"
        );
        WebCredentialsUtils webCredentialsUtils = new WebCredentialsUtils();
        OpenRosaAPIClient openRosaAPIClient = new OpenRosaAPIClient(openRosaHttpInterface, webCredentialsUtils);
        FormListDownloader formListDownloader = new FormListDownloader(
                openRosaAPIClient,
                webCredentialsUtils);
        HashMap<String, FormDetails> formList = formListDownloader.downloadFormList(false);
        int count = 0;
        if (formList.size() > 0) {
            ArrayList<FormDetails> forms = new ArrayList<>();
            for (Map.Entry<String, FormDetails> form : formList.entrySet()) {
                forms.add(form.getValue());
                count += 1;
            }
            FormDownloader formDownloader = null;
            dao = new FormsDao(JsonDB.getInstance().getDB());
            formDownloader = new FormDownloader(dao, openRosaAPIClient);
            formDownloader.downloadForms(forms);
            List<Form> downloadedForms =  dao.getForms();
            log.info("Total downloaded forms: " + downloadedForms.size());
        }
    }

    @GetMapping("/odk/updateAll")
    public void updateForms(){
        downloadForms();
    }

    @SneakyThrows
    @GetMapping("/generate-message")
    public ServiceResponse greeting(@RequestParam(value = "previousPath", required = false) String previousPath,
                                    @RequestParam(value = "currentAnswer", required = false) String currentAnswer,
                                    @RequestParam(value = "instanceXMlPrevious", required = false) String instanceXMlPrevious,
                                    @RequestParam(value = "botFormName", required = false) String botFormName) {

        if(previousPath != null) previousPath = URLDecoder.decode(previousPath, "UTF-8");
        if(instanceXMlPrevious != null) instanceXMlPrevious = URLDecoder.decode(instanceXMlPrevious, "UTF-8");
        if(currentAnswer != null) currentAnswer = URLDecoder.decode(currentAnswer, "UTF-8");
        log.info("PreviousPath: " + previousPath);
        log.info("CurrentAnswer" +  currentAnswer);
        log.info("InstanceCurrentXML" + instanceXMlPrevious);
        log.info("botFormName" +  botFormName);
        String formPath = ODKConsumerReactive.getFormPath(botFormName);
        ServiceResponse serviceResponse = new MenuManager(previousPath, currentAnswer, instanceXMlPrevious, formPath, botFormName, redisCacheService, "1234567890").start();
        System.out.println(serviceResponse.getCurrentResponseState());
        return serviceResponse;
    }
}
