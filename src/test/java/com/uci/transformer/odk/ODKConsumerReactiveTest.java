package com.uci.transformer.odk;

import com.uci.transformer.TransformerTestConfig;
import com.uci.transformer.odk.persistance.FormsDao;
import com.uci.transformer.odk.repository.QuestionRepository;
import com.uci.transformer.odk.repository.StateRepository;
import com.uci.utils.CampaignService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import javax.xml.bind.JAXBException;
import java.io.FileNotFoundException;

@SpringBootTest(classes = TransformerTestConfig.class)
@ExtendWith(MockitoExtension.class)
@TestPropertySource("classpath:test-application.properties")
class ODKConsumerReactiveTest {

    @Autowired
    ODKConsumerReactive odkConsumerReactive;

    @MockBean
    CampaignService campaignService;

    @MockBean
    StateRepository stateRepo;

    @MockBean
    QuestionRepository questionRepository;

    @MockBean
    FormsDao formsDao;

    @Test
    void onMessage() {
        odkConsumerReactive.onMessage();
    }

    //Todo
    @Test
    void transformToMany() throws FileNotFoundException, JAXBException {
//        InputStream inputStream = new FileInputStream();
//        XMessage xMessage = XMessageParser.parse(inputStream);
//        odkConsumerReactive.transformToMany(xMessage);
    }

    //Todo
    @Test
    void transform() {
    }

}