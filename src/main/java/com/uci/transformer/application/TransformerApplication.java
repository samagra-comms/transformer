package com.uci.transformer.application;

import com.uci.transformer.odk.FormDownloader;
import com.uci.transformer.odk.FormManager;
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
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.util.FileSystemUtils;
import reactor.blockhound.BlockHound;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EnableKafka
@EnableAsync
@EnableCaching
@ComponentScan(basePackages = {"com.uci.transformer", "messagerosa", "com.uci.utils"})
@EnableR2dbcRepositories(basePackages = {"com.uci.transformer.odk.repository"})
@EntityScan(basePackages = {"com.uci.transformer.odk.entity"})
@SpringBootApplication
@Slf4j
public class TransformerApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                TransformerApplication.class, args);
    }

    @PostConstruct
    private void postConstruct() {
    	String downloadFormsFlag = System.getenv("DOWNLOAD_TRANSFORMER_FORMS");
        if(!(downloadFormsFlag != null && downloadFormsFlag.equalsIgnoreCase("False"))) {
        	downloadForms();        	
        }
    	// testFormManager();
    }

    private void testFormManager() {
        String formPath = ODKConsumerReactive.getFormPath("samagra_workflows_form");
        ServiceResponse response1 = new FormManager(null, null, null, formPath).start();
        log.debug("First response");
        log.debug(response1.getCurrentIndex(), response1.getNextMessage().getText());
    }

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
//    @Bean
//    CommandLineRunner executeOnStartup(Scheduler scheduler, Task<Void> sampleOneTimeTask) {
//        log.info("Scheduling one time task to now!");
//
//        return ignored -> scheduler.schedule(
//                sampleOneTimeTask.instance("command-line-runner"),
//                Instant.now()
//        );
//    }
}

