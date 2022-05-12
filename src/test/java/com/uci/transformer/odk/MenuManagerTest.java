package com.uci.transformer.odk;

import com.uci.transformer.TransformerTestConfig;
import com.uci.transformer.odk.entity.Question;
import com.uci.transformer.odk.repository.QuestionRepository;
import messagerosa.core.model.XMessagePayload;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.FormIndex;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.form.api.FormEntryModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = TransformerTestConfig.class)
class MenuManagerTest {

    @Autowired
    MenuManager menuManager;

    @MockBean
    QuestionRepository questionRepository;

    String xpath = "question./data/intro_group[1]/preferences[1]";
    FormEntryController controller;

    @BeforeEach
    public void setup(){
        menuManager.questionRepo = questionRepository;
        FormDef formDef = new FormDef();
//        formDef.setTextID("0");
        formDef.setTitle("UCI-demo-1");
        formDef.setName("UCI-demo-1");
        FormEntryModel formEntryModel = new FormEntryModel(formDef);
        formEntryModel.setQuestionIndex(FormIndex.createBeginningOfFormIndex());
        controller = new FormEntryController(formEntryModel);
    }

    @Test
    void start() {
        menuManager.start();
    }

    @Test
    @Disabled("no need to test")
    void setAssesmentCharacters() {
    }

    @Test
    void isGlobal() {
        boolean global = menuManager.isGlobal();
        System.out.println(global);
    }

    @Test
    void getNextBotID() {
//        menuManager.getNextBotID();
    }

    @Test
    void jumpToIndex() {
        int i = menuManager.jumpToIndex(controller, FormIndex.createBeginningOfFormIndex());// need form index
        assertNotNull(i);
    }

    @Test
    void getEvent() {
        int event = menuManager.getEvent(controller);
        System.out.println("Event : " + event);
    }

    @Test
    void getQuestionPayloadFromXPath() {
        XMessagePayload questionPayloadFromXPath = menuManager.getQuestionPayloadFromXPath(xpath);
        assertNotNull(questionPayloadFromXPath);
    }

    @Test
    void getQuestionFromXPath() {
        Question questionFromXPath = menuManager.getQuestionFromXPath(xpath);
        System.out.println(questionFromXPath);
        assertNotNull(questionFromXPath);
    }

    @Test
    @Disabled("need to work on it")
    void addResponseToForm() throws IOException {
        // Todo : fix me
        menuManager.addResponseToForm(FormIndex.createBeginningOfFormIndex(), "2");
    }

    @Test
    void writeFile() {

    }

    @Test
    void importData() {

    }

    //Todo
    @Test
    void getIndexFromXPath() {
        FormIndex indexFromXPath = menuManager.getIndexFromXPath(xpath, controller);
        System.out.println(indexFromXPath);
    }

    @Test
    void getXPath() {
        String xPath = menuManager.getXPath(controller, FormIndex.createBeginningOfFormIndex());
        assertNotNull(xPath);
    }

    @Test
    @Disabled("not being used")
    void getQuestionDefForNode() {
//        menuManager.getQuestionDefForNode(controller);
    }

    @Test
    void loadForm() {
        MenuManager.FECWrapper fecWrapper = menuManager.loadForm("/tmp/forms2/UCI demo 1.xml", null);
        assertNotNull(fecWrapper);
    }
}