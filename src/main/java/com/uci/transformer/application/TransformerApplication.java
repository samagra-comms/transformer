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
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories;
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
@EnableReactiveCassandraRepositories("com.uci.dao")
@ComponentScan(basePackages = {"com.uci.transformer", "messagerosa", "com.uci.utils", "com.uci.dao.service"})
@EnableR2dbcRepositories(basePackages = {"com.uci.transformer.odk.repository"})
@EntityScan(basePackages = {"com.uci.transformer.odk.entity", "com.uci.messagerosa", "com.uci.dao"})
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
    }

    private void downloadForms() {
        new FormDownloader().downloadFormsDelta();
    }
}
