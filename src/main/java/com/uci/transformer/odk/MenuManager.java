package com.uci.transformer.odk;

import java.util.Arrays;
import com.uci.transformer.odk.entity.Meta;
import com.uci.transformer.odk.entity.Question;
import com.uci.transformer.odk.repository.QuestionRepository;
import io.r2dbc.postgresql.codec.Json;
import lombok.*;
import lombok.extern.java.Log;
import messagerosa.core.model.ButtonChoice;
import messagerosa.core.model.MediaCategory;
import messagerosa.core.model.StylingTag;
import messagerosa.core.model.XMessagePayload;

import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.javarosa.core.model.*;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.IntegerData;
import org.javarosa.core.model.data.SelectOneData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.model.instance.InstanceInitializationFactory;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.model.instance.utils.DefaultAnswerResolver;
import org.javarosa.core.services.transport.payload.ByteArrayPayload;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.form.api.FormEntryModel;
import org.javarosa.form.api.FormEntryPrompt;
import org.javarosa.model.xform.XFormSerializingVisitor;
import org.javarosa.model.xform.XFormsModule;
import org.javarosa.xform.parse.XFormParser;
import org.javarosa.xform.util.XFormUtils;
import org.javarosa.xpath.XPathTypeMismatchException;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.annotation.PostConstruct;

import static com.uci.transformer.odk.utilities.FileUtils.MEDIA_SUFFIX;
import static org.javarosa.form.api.FormEntryController.ANSWER_OK;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
class SaveStatus {
    String instanceXML;
    int saveStatus;
}

@Log
public class MenuManager {

    FormEntryController formController;
    String xpath;
    String answer;
    String instanceXML;
    String formPath;
    String initialInstanceXML;
    String formID;
    Boolean isSpecialResponse;
    Boolean isPrefilled;
    QuestionRepository questionRepo;
    String assesGoToStartChar;
    String assesOneLevelUpChar;
    Integer formDepth;
    String stylingTag;
    XMessagePayload payload;

    public MenuManager(String xpath, String answer, String instanceXML, String formPath, String formID, XMessagePayload payload) {
        this.xpath = xpath;
        this.answer = answer;
        this.instanceXML = instanceXML;
        this.formPath = formPath;
        this.isSpecialResponse = false;
        this.isPrefilled = false;
        this.formID = formID;
        this.payload = payload;
        
        setAssesmentCharacters();
    }

    public MenuManager(String xpath, String answer, String instanceXML, String formPath, String formID, Boolean isPrefilled, QuestionRepository questionRepo, XMessagePayload payload) {
        this.xpath = xpath;
        this.answer = answer;
        this.instanceXML = instanceXML;
        this.formPath = formPath;
        this.isSpecialResponse = false;
        this.isPrefilled = isPrefilled;
        this.formID = formID;
        this.questionRepo = questionRepo;
        this.payload = payload;
        
        setAssesmentCharacters();
    }
    
    public void setAssesmentCharacters() {
    	String envAssesOneLevelUpChar = System.getenv("ASSESSMENT_ONE_LEVEL_UP_CHAR");
        String envAssesGoToStartChar = System.getenv("ASSESSMENT_GO_TO_START_CHAR");
        
        this.assesOneLevelUpChar = envAssesOneLevelUpChar == "0" || (envAssesOneLevelUpChar != null && !envAssesOneLevelUpChar.isEmpty()) ? envAssesOneLevelUpChar : "#";
        this.assesGoToStartChar = envAssesGoToStartChar == "0" || (envAssesGoToStartChar != null && !envAssesGoToStartChar.isEmpty()) ? envAssesGoToStartChar : "*";
    }

    public boolean isGlobal() {
        return this.formPath.contains("Global Form");
    }

    public String getNextBotID(String xPathName) {
        return xPathName.split("__")[1];
    }

    protected static class FECWrapper {
        FormEntryController controller;
        boolean usedSavepoint;

        protected FECWrapper(FormEntryController controller, boolean usedSavepoint) {
            this.controller = controller;
            this.usedSavepoint = usedSavepoint;
        }

        protected FormEntryController getController() {
            return controller;
        }

        protected boolean hasUsedSavepoint() {
            return usedSavepoint;
        }

        protected void free() {
            controller = null;
        }
    }

    public int jumpToIndex(FormEntryController fec, FormIndex index) {
        return fec.jumpToIndex(index);
    }

    /**
     * returns the event for the current FormIndex.
     */
    public int getEvent(FormEntryController fec) {
        return fec.getModel().getEvent();
    }

    public ServiceResponse start() {
        new XFormsModule().registerModule();
        FECWrapper fecWrapper = loadForm(formPath, xpath); // If instance load from instance (If form is filled load new)
        formController = fecWrapper.controller;
        String currentPath = "";
        String udpatedInstanceXML = "";
        XMessagePayload nextQuestion;
        SaveStatus saveStatus = new SaveStatus();
        
        if (answer != null && answer.equals(assesOneLevelUpChar)) {
            this.isSpecialResponse = true;
            // Get to the note of the previous group

            // Set the current answer as blank
            setBlankAnswer();

            // Skip to previous question
            formController.stepToPreviousEvent();
            // Check for a dynamic question with select and skip 2 questions answer the level
            if (isDynamicQuestion()) {
                setBlankAnswer();
                formController.stepToPreviousEvent();
                SelectOneData s = (SelectOneData) formController.getModel().getForm().getMainInstance().resolveReference(formController.getModel().getFormIndex().getReference()).getValue();
                String value = s.getDisplayText();
                setBlankAnswer();
                try {
                    udpatedInstanceXML = getCurrentInstance();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                xpath = getXPath(formController, formController.getModel().getFormIndex());
                answer = value;
                instanceXML = udpatedInstanceXML;
                return start();

            } else {
                try {
                    // Skip if it is a note
                    if (isIntro())
                        formController.stepToPreviousEvent();
                    formController.getModel().getQuestionPrompt();
                } catch (Exception e) {
                    formController.stepToPreviousEvent();
                }

                // If question if part of a group of just one question, skip that too to the the start of the group.
                formController.stepToPreviousEvent();
                try {
                    // Skip if it is a note
                    if (isIntro())
                        formController.stepToPreviousEvent();

                    // Skip a non question TODO: Should remove all non questions. Right now doing only for one.
                    if (formController.getModel().getEvent() != FormEntryController.EVENT_GROUP) {
                        formController.getModel().getQuestionPrompt();
                        formController.stepToNextEvent();
                    }
                } catch (Exception e) {
                    formController.stepToPreviousEvent();
                }
            }

            try {
                udpatedInstanceXML = getCurrentInstance();
            } catch (IOException e) {
                e.printStackTrace();
            }

            nextQuestion = createView(formController.getModel().getEvent(), "");
            currentPath = getXPath(formController, formController.getModel().getFormIndex());

        } else if (answer != null && answer.equals(assesGoToStartChar)) {
            if (!isPrefilled) instanceXML = null;
            xpath = null;
            answer = null;
            return start();

        } else {
            try {
                if (xpath != null && !xpath.equals("endOfForm")) {
                    saveStatus = addResponseToForm(getIndexFromXPath(xpath, formController), answer);
                    udpatedInstanceXML = saveStatus.getInstanceXML();
                } else {
                    FormInstance formInstance = formController.getModel().getForm().getInstance();
                    XFormSerializingVisitor serializer = new XFormSerializingVisitor();
                    ByteArrayPayload payload = (ByteArrayPayload) serializer.createSerializedPayload(formInstance);
                    udpatedInstanceXML = payload.toString();
                }

                formController.stepToNextEvent();
                nextQuestion = createView(formController.getModel().getEvent(), "");
                log.info(String.format("Current question is %s with %d choices", nextQuestion.getText(), (nextQuestion.getButtonChoices() != null ? nextQuestion.getButtonChoices().size() : 0)));

                if (instanceXML != null) {
                    if (!udpatedInstanceXML.equals(instanceXML) || saveStatus.getSaveStatus() == ANSWER_OK) {
                        currentPath = getXPath(formController, formController.getModel().getFormIndex());
                    } else {
                        if (xpath.equals("endOfForm")) {
                            currentPath = xpath;
                            nextQuestion = XMessagePayload.builder().text("---------End of Form---------").build();
                        } else {
                            currentPath = xpath;
                            udpatedInstanceXML = instanceXML;
                            String constraintText;
                            FormIndex formIndex = getIndexFromXPath(currentPath, formController);
                            constraintText = formController.getModel().getQuestionPrompt(formIndex).getConstraintText();
                            if (constraintText == null) {
                                constraintText = formController.getModel().getQuestionPrompt(formIndex).getSpecialFormQuestionText("constraintMsg");
                                if (constraintText == null) {
                                    constraintText = "Invalid Input!!! Please try again.";
                                }
                            }
                            nextQuestion = XMessagePayload.builder().text(constraintText).build();
                        }
                    }
                } else {
                    currentPath = getXPath(formController, formController.getModel().getFormIndex());
                }
                // Jump to the location where it is not filled.
            } catch (IOException e) {
                nextQuestion = new XMessagePayload();
                e.printStackTrace();
            }
        }
        
        // check if currentPath is persisted in the DB. If not, insert it with all the things.
        String formVersion = formController.getModel().getForm().getInstance().formVersion;
        Question question = new Question();
        question.setQuestionType(Question.QuestionType.STRING);
        question.setFormID(formID);
        question.setFormVersion(formVersion);
        question.setXPath(currentPath);
        ArrayList<String> choices = new ArrayList<>();
        if (nextQuestion.getButtonChoices() != null) {
            for (ButtonChoice buttonChoice : nextQuestion.getButtonChoices()) {
                choices.add(buttonChoice.getText());
            }
        }
        
        question.setMeta(Json.of(new Meta(nextQuestion.getText(), choices).toString()));

        FormIndex formIndex = formController.getModel().getFormIndex();
        ArrayList<Integer> conversationLevel = new ArrayList();
		
        Integer previousIndex = formIndex.getLocalIndex();
		conversationLevel.add(previousIndex);
		if(formIndex.getNextLevel() != null) {
			Integer nextIndex = formIndex.getNextLevel().getLocalIndex();
			conversationLevel.add(nextIndex);
		}
        
		return new ServiceResponse(currentPath, nextQuestion, udpatedInstanceXML, formVersion, formID, question, conversationLevel);
    }
    
    /**
     * Get Question XMessage Payload with text & button choices from question xPath
     * 
     * @return XMessagePayload
     */
    public XMessagePayload getQuestionPayloadFromXPath(String xpathStr) {
    	new XFormsModule().registerModule();
        FECWrapper fecWrapper = loadForm(formPath, xpathStr); // If instance load from instance (If form is filled load new)
        formController = fecWrapper.controller;
        
        /* Previous Question */
        ArrayList<ButtonChoice> choices = new ArrayList();
        choices = getChoices(choices);
        String questionText = renderQuestion(formController);
        
        XMessagePayload payload = XMessagePayload.builder()
        								.text(questionText)
        								.buttonChoices(choices)
        								.build();
        
        try {
        	if(formController.getModel().getQuestionPrompt().getBindAttributes() != null) {
        		payload = getPayloadWithBindTags(payload, formController.getModel().getQuestionPrompt().getBindAttributes());
            }
        } catch (Exception e) {
        	log.info("Exception in getQuestionPayloadFromXPath for bind attributes: "+e.getMessage());
        }
        
        return payload;
    }
    
    /**
     * Get Question Object with xPath
     * 
     * @return Question
     */
    public Question getQuestionFromXPath(String xpathStr) {
    	new XFormsModule().registerModule();
        FECWrapper fecWrapper = loadForm(formPath, xpathStr); // If instance load from instance (If form is filled load new)
        formController = fecWrapper.controller;
        
    	String formVersion = formController.getModel().getForm().getInstance().formVersion;
        Question question = new Question();
        question.setQuestionType(Question.QuestionType.STRING);
        question.setFormID(formID);
        question.setFormVersion(formVersion);
        question.setXPath(xpathStr);
        
        return question;
    }
    
    private boolean isDynamicQuestion() {
        try {
            return formController.getModel().getEvent() == 4 &&
                    formController.getModel().getQuestionPrompt().getControlType() == Constants.CONTROL_SELECT_ONE &&
                    formController.getModel().getQuestionPrompt().getQuestion().getDynamicChoices().getChoices() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private void setBlankAnswer() {
        try {
            IAnswerData answerData = new StringData("");
            FormIndex fi = formController.getModel().getFormIndex();
            int saveData = formController.answerQuestion(fi, answerData, true);
            if (saveData != ANSWER_OK) {
                TreeElement t = formController.getModel().getForm().getMainInstance().resolveReference(fi.getReference());
                formController.getModel().getForm().setValue(answerData, t.getRef(), true);
            }
        } catch (Exception e) {

        }
    }

    private String getCurrentInstance() throws IOException {
        FormInstance formInstance = formController.getModel().getForm().getInstance();
        XFormSerializingVisitor serializer = new XFormSerializingVisitor();
        ByteArrayPayload payload = (ByteArrayPayload) serializer.createSerializedPayload(formInstance);
        return payload.toString();
    }

    public SaveStatus addResponseToForm(FormIndex formIndex, String value) throws IOException {
        int saveStatus = -1;
        if (value != null) {
            // Works with name but you get Label
            try {
            	log.info("Question control type: "+formController.getModel().getQuestionPrompt().getControlType()
            			+", control datatype: "+formController.getModel().getQuestionPrompt().getDataType()
            			+", for xpath: "+this.xpath);
            	if (formController.getModel().getQuestionPrompt().getControlType() == Constants.CONTROL_SELECT_ONE) {
                    List<SelectChoice> items = formController.getModel().getQuestionPrompt().getSelectChoices();
                    boolean found = false;
                    if (items != null) {
                        for (int i = 0; i < items.size(); i++) {
                            if (value.equals(items.get(i).getLabelInnerText()) ||
                                    checkForSpaceInOptions(value, items, i) ||
                                    checkForDotInOptions(value, items, i) ||
                                    this.isSpecialResponse
                            ) {
                                found = true;
                                IAnswerData answerData;
                                if (!this.isSpecialResponse) answerData = new StringData(items.get(i).getValue());
                                else answerData = new StringData(value);
                                saveStatus = formController.answerQuestion(formIndex, answerData, true);
                                break;
                            }
                        }
                        if (!found) { //Checking for labels with indexes as part of the text only
                            for (int i = 0; i < items.size(); i++) {
                                if (value.equals(items.get(i).getLabelInnerText().split(" ")[0]) ||
                                        value.equals(items.get(i).getLabelInnerText().split(". ")[0])
                                ) {
                                    found = true;
                                    IAnswerData answerData;
                                    if (!this.isSpecialResponse) answerData = new StringData(items.get(i).getValue());
                                    else answerData = new StringData(value);
                                    saveStatus = formController.answerQuestion(formIndex, answerData, true);
                                    break;
                                }
                            }
                        }
                    }
                } else if(formController.getModel().getQuestionPrompt().getControlType() == Constants.CONTROL_IMAGE_CHOOSE) { 
                	 TreeElement t = formController.getModel().getForm().getMainInstance().resolveReference(formIndex.getReference());
                     try {
                    	 if(this.payload != null && this.payload.getMedia() != null
                                && this.payload.getMedia().getMessageMediaError() == null
                     			&& this.payload.getMedia().getCategory() != null 
                     			&& this.payload.getMedia().getCategory().equals(MediaCategory.IMAGE)) {
                    		 IAnswerData answerData = new StringData(value);
                             saveStatus = formController.answerQuestion(formIndex, answerData, true);
                    	 }
                     } catch (Exception e) {
                        log.severe("Exception in addResponseToForm for image type.");
                        e.printStackTrace();
                     }
                } else if(formController.getModel().getQuestionPrompt().getControlType() == Constants.CONTROL_AUDIO_CAPTURE) { 
                	TreeElement t = formController.getModel().getForm().getMainInstance().resolveReference(formIndex.getReference());
                    try {
                    	if(this.payload != null && this.payload.getMedia() != null
                                && this.payload.getMedia().getMessageMediaError() == null
                                && this.payload.getMedia().getCategory() != null
                    			&& this.payload.getMedia().getCategory().equals(MediaCategory.AUDIO)) {
                   		 	IAnswerData answerData = new StringData(value);
                   		 	saveStatus = formController.answerQuestion(formIndex, answerData, true);
                    	}
                    } catch (Exception e) {
                       log.severe("Exception in addResponseToForm for audio type.");
                       e.printStackTrace();
                    }
                } else if(formController.getModel().getQuestionPrompt().getControlType() == Constants.CONTROL_VIDEO_CAPTURE) { 
                	TreeElement t = formController.getModel().getForm().getMainInstance().resolveReference(formIndex.getReference());
                    try {
                    	if(this.payload != null && this.payload.getMedia() != null
                                && this.payload.getMedia().getMessageMediaError() == null
                                && this.payload.getMedia().getCategory() != null
                    			&& this.payload.getMedia().getCategory().equals(MediaCategory.VIDEO)) {
                   		 	IAnswerData answerData = new StringData(value);
                   		 	saveStatus = formController.answerQuestion(formIndex, answerData, true);
                    	}
                    } catch (Exception e) {
                       log.severe("Exception in addResponseToForm for video type.");
                       e.printStackTrace();
                    }
                } else if(formController.getModel().getQuestionPrompt().getControlType() == Constants.CONTROL_FILE_CAPTURE) { 
                	TreeElement t = formController.getModel().getForm().getMainInstance().resolveReference(formIndex.getReference());
                    try {
                    	log.info("category: "+this.payload.getMedia().getCategory());
                    	if(this.payload != null && this.payload.getMedia() != null
                                && this.payload.getMedia().getMessageMediaError() == null
                                && this.payload.getMedia().getCategory() != null
                    			&& this.payload.getMedia().getCategory().equals(MediaCategory.FILE)) {
                   		 	IAnswerData answerData = new StringData(value);
                   		 	saveStatus = formController.answerQuestion(formIndex, answerData, true);
                    	}
                    } catch (Exception e) {
                       log.severe("Exception in addResponseToForm for file type.");
                       e.printStackTrace();
                    }
                } else if(formController.getModel().getQuestionPrompt().getDataType() == Constants.DATATYPE_GEOPOINT) { 
                	TreeElement t = formController.getModel().getForm().getMainInstance().resolveReference(formIndex.getReference());
                    try {
                    	if(this.payload != null && this.payload.getLocation() != null) {
                    		log.info("location found with value: "+value);
                   		 	IAnswerData answerData = new StringData(value);
                   		 	saveStatus = formController.answerQuestion(formIndex, answerData, true);
                    	}
                    } catch (Exception e) {
                       log.severe("Exception in addResponseToForm for video type.");
                       e.printStackTrace();
                    }
                } else {
                    try {
                        TreeElement t = formController.getModel().getForm().getMainInstance().resolveReference(formIndex.getReference());
                        try {
                            IAnswerData answerData = new IntegerData(Integer.parseInt(value));
                            saveStatus = formController.answerQuestion(formIndex, answerData, true);
                        } catch (Exception e) {
                            IAnswerData answerData = new StringData(value);
                            saveStatus = formController.answerQuestion(formIndex, answerData, true);
                        }
                    } catch (Exception e) {
                        log.severe("Error in filling form response");
                        saveStatus = ANSWER_OK;
                    }
                }
            } catch (Exception e) {
                log.severe("Error in filling form response");
                IAnswerData answerData = new IntegerData(Integer.parseInt(value));
                saveStatus = formController.answerQuestion(formIndex, answerData, true);
            }
            if (saveStatus != ANSWER_OK) {
                return new SaveStatus(instanceXML, saveStatus);
            } else {
                FormInstance formInstance = formController.getModel().getForm().getInstance();
                XFormSerializingVisitor serializer = new XFormSerializingVisitor();
                ByteArrayPayload payload = (ByteArrayPayload) serializer.createSerializedPayload(formInstance);
                return new SaveStatus(payload.toString(), saveStatus);
            }
        }
        return new SaveStatus(instanceXML, saveStatus);
    }

    private boolean checkForSpaceInOptions(String value, List<SelectChoice> items, int i) {
        // Example 1 Option1
        try {
            return value.equals(items.get(i).getLabelInnerText().split(" ")[0]);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkForDotInOptions(String value, List<SelectChoice> items, int i) {
        // Example 1.
        try {
            return value.equals(items.get(i).getLabelInnerText().split(" ")[0].split(".")[0]);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Writes payload contents to the disk.
     */
    static void writeFile(ByteArrayPayload payload, String path) throws IOException {
        File file = new File(path);
        if (file.exists() && !file.delete()) {
            throw new IOException("Cannot overwrite " + path + ". Perhaps the file is locked?");
        }

        // create data stream
        InputStream is = payload.getPayloadStream();
        int len = (int) payload.getLength();

        // read from data stream
        byte[] data = new byte[len];
        int read = is.read(data, 0, len);
        if (read > 0) {
            // Make sure the directory path to this file exists.
            file.getParentFile().mkdirs();
            // write xml file
            RandomAccessFile randomAccessFile = null;
            try {
                randomAccessFile = new RandomAccessFile(file, "rws");
                randomAccessFile.write(data);
            } finally {
                if (randomAccessFile != null) {
                    try {
                        randomAccessFile.close();
                    } catch (IOException e) {
                        log.severe(String.format("Error closing RandomAccessFile: %s", path));
                    }
                }
            }
        }
    }

    public static void importData(String instanceXML, FormEntryController fec) throws IOException, RuntimeException {
        // convert files into a byte array
        //byte[] fileBytes = org.apache.commons.io.FileUtils.readFileToByteArray(instanceFile);
        byte[] fileBytes = instanceXML.getBytes();

        // get the root of the saved and template instances
        TreeElement savedRoot = XFormParser.restoreDataModel(fileBytes, null).getRoot();
        TreeElement templateRoot = fec.getModel().getForm().getInstance().getRoot().deepCopy(true);

        // weak check for matching forms
        if (!savedRoot.getName().equals(templateRoot.getName()) || savedRoot.getMult() != 0) {
            log.severe("Saved form instance does not match template form definition");
            return;
        }

        // populate the data model
        TreeReference tr = TreeReference.rootRef();
        tr.add(templateRoot.getName(), TreeReference.INDEX_UNBOUND);

        // Here we set the Collect's implementation of the IAnswerResolver.
        // We set it back to the default after select choices have been populated.
        XFormParser.setAnswerResolver(new ExternalAnswerResolver());
        templateRoot.populate(savedRoot, fec.getModel().getForm());
        XFormParser.setAnswerResolver(new DefaultAnswerResolver());

        // populated model to current form
        fec.getModel().getForm().getInstance().setRoot(templateRoot);

        // fix any language issues
        // http://bitbucket.org/javarosa/main/issue/5/itext-n-appearing-in-restored-instances
        if (fec.getModel().getLanguages() != null) {
            fec.getModel().getForm()
                    .localeChanged(fec.getModel().getLanguage(),
                            fec.getModel().getForm().getLocalizer());
        }
        log.info("Done importing data");
    }

    public FormIndex getIndexFromXPath(String xpath, FormEntryController fec) {
        switch (xpath) {
            case "beginningOfForm":
                return FormIndex.createBeginningOfFormIndex();
            case "endOfForm":
                return FormIndex.createEndOfFormIndex();
            case "unexpected":
                log.severe("Unexpected string from XPath");
                throw new IllegalArgumentException("unexpected string from XPath");
            default:
                FormIndex returned = null;
                FormIndex saved = fec.getModel().getFormIndex();
                // the only way I know how to do this is to step through the entire form
                // until the XPath of a form entry matches that of the supplied XPath
                try {
                    jumpToIndex(fec, FormIndex.createBeginningOfFormIndex());
                    int event = fec.stepToNextEvent();
                    while (event != FormEntryController.EVENT_END_OF_FORM) {
                        String candidateXPath = getXPath(fec, fec.getModel().getFormIndex());
                        //log.info("xpath: " + candidateXPath);
                        if (candidateXPath.equals(xpath)) {
                            returned = fec.getModel().getFormIndex();
                            break;
                        }
                        event = fec.stepToNextEvent();
                    }
                } finally {
                    jumpToIndex(fec, saved);
                }
                return returned;
        }
    }

    /**
     * For logging purposes...
     *
     * @return xpath value for this index
     */
    public String getXPath(FormEntryController fec, FormIndex index) {
        String value;
        switch (getEvent(fec)) {
            case FormEntryController.EVENT_BEGINNING_OF_FORM:
                value = "beginningOfForm";
                break;
            case FormEntryController.EVENT_END_OF_FORM:
                value = "endOfForm";
                break;
            case FormEntryController.EVENT_GROUP:
                value = "group." + index.getReference().toString();
                break;
            case FormEntryController.EVENT_QUESTION:
                value = "question." + index.getReference().toString();
                break;
            case FormEntryController.EVENT_PROMPT_NEW_REPEAT:
                value = "promptNewRepeat." + index.getReference().toString();
                break;
            case FormEntryController.EVENT_REPEAT:
                value = "repeat." + index.getReference().toString();
                break;
            case FormEntryController.EVENT_REPEAT_JUNCTURE:
                value = "repeatJuncture." + index.getReference().toString();
                break;
            default:
                value = "unexpected";
                break;
        }
        return value;
    }

    /**
     * Steps to the next screen and creates a view for it. Always sets {@code advancingPage} to true
     * to auto-play media.
     */
    private XMessagePayload createViewForFormBeginning(FormEntryController formController) {
        formController.stepToNextEvent(); // To start the form
        if (formController.getModel().getEvent() == FormEntryController.EVENT_GROUP)
            formController.stepToPreviousEvent();
        String prompt = renderQuestion(formController);
        return createView(formController.stepToNextEvent(), prompt); //To render the first question.
    }

    private String cleanText(String s) {
        if (s.equals("")) return "";
        return s;
        //return s.replace("\r", "*/\n*").replaceAll("\\s+", " ");
    }

    private String renderQuestion(FormEntryController formController) {
        try {
            System.out.println("test");
            if(cleanText(getHelpText(formController)).equals("")){
                return "" + cleanText(getQuestionText(formController)) + "" + " \n\n";
            }else{
                return "" + cleanText(getQuestionText(formController)) + "" + " \n" +
                        "_" + cleanText(getHelpText(formController)) + "_" + " \n\n";
            }
            //return "*" + cleanText(getQuestionText(formController)) + "*" + " \n" + "_" + cleanText(getHelpText(formController)) + "_" + " \n\n";
        } catch (Exception e) {
            return "";
        }
    }

    private String getHelpText(FormEntryController formController) {
        String helpText = formController.getModel().getQuestionPrompt().getHelpText();
        if (helpText == null) return "";
        return helpText;
    }

    private String getQuestionText(FormEntryController formController) {
        return formController.getModel().getQuestionPrompt().getQuestionText();
    }

    private boolean isQuestionChoiceType(FormEntryController formController) {
        try {
            return formController.getModel().getQuestionPrompt().getControlType() == Constants.CONTROL_SELECT_ONE;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Creates and returns a new view based on the event type passed in. The view returned is
     * of type if the event passed in represents the end of the form.
     *
     * @return newly created View
     */
    private XMessagePayload createView(int event, String previousPrompt) {
        log.info("xPath: " + getXPath(formController, formController.getModel().getFormIndex()));
        log.info("Event: " + getEvent(formController));

        switch (event) {
            case FormEntryController.EVENT_BEGINNING_OF_FORM:
                return createViewForFormBeginning(formController);
            case FormEntryController.EVENT_END_OF_FORM:
                return createViewForFormEnd(formController);
            case FormEntryController.EVENT_QUESTION:
            case FormEntryController.EVENT_GROUP:
            case FormEntryController.EVENT_REPEAT:
                // Check for rendered Types
                ArrayList<ButtonChoice> choices = new ArrayList<>();
                try {
                    if (formController.getModel().getEvent() == FormEntryController.EVENT_REPEAT) {
//                        formController.stepToNextEvent();
                        return createView(formController.stepToNextEvent(), previousPrompt);
                    }
                    if (formController.getModel().getEvent() == FormEntryController.EVENT_GROUP) {
                        formController.stepToNextEvent();
                    }
                    // Check for note and add
                    if (isIntro() && !isQuestionChoiceType(formController)) {
                        previousPrompt = renderQuestion(formController);
                        return createView(formController.stepToNextEvent(), previousPrompt);
                    }
                    
                	choices = getChoices(choices);
                    
                    //Check this
                    return getPayloadWithBindTags(
                    		XMessagePayload.builder().text(previousPrompt + renderQuestion(formController)).buttonChoices(choices).build(), 
                    		formController.getModel().getQuestionPrompt().getBindAttributes());
                } catch (Exception e) {
                	e.printStackTrace();
                    log.info("Non Question data type");
                    formController.stepToNextEvent();
                    String currentQuestionString = renderQuestion(formController);
                    if (previousPrompt != null && previousPrompt != "")
                        return XMessagePayload.builder().text(previousPrompt + currentQuestionString).build();
                    XMessagePayload nextQuestionString = createView(formController.stepToNextEvent(), "");
                    return XMessagePayload.builder().text(currentQuestionString + nextQuestionString.getText()).build();
                }

            case FormEntryController.EVENT_PROMPT_NEW_REPEAT:
                return null;
            default:
                return createView(event, "");
        }
    }

    private boolean isIntro() {
        String xPath = getXPath(formController, formController.getModel().getFormIndex());
        return xPath.contains("intro");
    }

    private boolean isNote() {
        String xPath = getXPath(formController, formController.getModel().getFormIndex());
        return xPath.contains("note");
    }

    private ArrayList<ButtonChoice> getChoices(ArrayList<ButtonChoice> choices) {
        ArrayList<ButtonChoice> buttonChoices = new ArrayList<>();
        try {
            switch (formController.getModel().getQuestionPrompt().getControlType()) {
                case Constants.CONTROL_SELECT_ONE:
                    List<SelectChoice> items = formController.getModel().getQuestionPrompt().getSelectChoices();
                    if (items != null) {
                        for (int i = 0; i < items.size(); i++) {
                            //Check
                            buttonChoices.add(ButtonChoice.builder().key(items.get(i).getValue()).text(items.get(i).getLabelInnerText()).build());
                        }
                    }
            }
        } catch (Exception e) {
        }
        return getQuestionsChoiceWithKey(buttonChoices);
    }
    
    /**
	 * Get Question Choices with correct key
	 * @param questionChoices
	 * @return
	 */
	private ArrayList<ButtonChoice> getQuestionsChoiceWithKey(ArrayList<ButtonChoice> questionChoices) {
		if(questionChoices != null) {
			try {
				questionChoices.forEach(choice -> {
					try {
						String[] a = choice.getText().split(" ");
						if(a[0] != null && !a[0].isEmpty()) {
							Integer.parseInt(a[0]);
					        choice.setKey(a[0].toString());
			    		}
					} catch (NumberFormatException ex) {
						String[] b = choice.getText().split(".");
			    		try {
			    			if(b[0] != null && !b[0].isEmpty()) {
				    		    Integer.parseInt(b[0]);
				    		    choice.setKey(b[0].toString());
			    			}
			    		} catch (NumberFormatException exc) {
			    			// do nothing
			    		} catch (ArrayIndexOutOfBoundsException exc) {
			    			// do nothing
			    		}
					} catch (ArrayIndexOutOfBoundsException ex) {
						// do nothing
					} catch (Exception ex) {
						log.info("Exception in getQuestionsChoiceWithKey-2: "+ex.getMessage());
					}
				});
			} catch (Exception e) {
				log.info("Exception in getQuestionsChoiceWithKey: "+e.getMessage());
			}
			
		}
		return questionChoices;
	}

    private XMessagePayload createViewForFormEnd(FormEntryController formController) {

        return XMessagePayload.builder().text("").build();
    }

    private boolean initializeForm(FormDef formDef, FormEntryController fec) throws IOException {
        final InstanceInitializationFactory instanceInit = new InstanceInitializationFactory();
        boolean usedSavepoint = false;

        if (instanceXML != null && !instanceXML.isEmpty()) {
            // This order is important. Import data, then initialize.
            try {
                log.info("Importing data");
                importData(instanceXML, fec);
                formDef.initialize(false, instanceInit);
            } catch (IOException | RuntimeException e) {
                log.severe(e.getMessage());

                // Skip a savepoint file that is corrupted or 0-sized
                if (usedSavepoint && !(e.getCause() instanceof XPathTypeMismatchException)) {
                    usedSavepoint = false;
                    formDef.initialize(true, instanceInit);
                } else {
                    // The saved instance is corrupted.
                    throw e;
                }
            }
        } else {
            formDef.initialize(true, instanceInit);
        }
        return usedSavepoint;
    }

    private FormDef createFormDefFromCacheOrXml(String formPath, File formXml) {

        FileInputStream fis = null;
        // no binary, read from xml
        try {
            // log.info(String.format("Attempting to load from: %s", formXml.getAbsolutePath()));
            Path path = FileSystems.getDefault().getPath("CensusBot.xml");
            fis = new FileInputStream(formXml);
            FormDef fd = XFormUtils.getFormFromInputStream(fis);
            return fd;
        } catch (Exception e) {
            log.severe("CP-2" + e.getMessage());
        } finally {
            IOUtils.closeQuietly(fis);
        }
        return null;
    }

    public QuestionDef getQuestionDefForNode(FormEntryController fec, TreeElement t) {
        return FormDef.findQuestionByRef(t.getRef(), fec.getModel().getForm());
    }

    public FECWrapper loadForm(String formPath, String xpath) {

        if (formPath == null) {
            System.out.println("formPath is null");
            return null;
        }
        log.info("Current form path :: " + formPath);

        File formXml;
        formXml = new File(formPath);
        if(!formXml.exists()){
            String[] filePathParts = formPath.split("/");
            String filePathLast = filePathParts[filePathParts.length-1];
            log.info(filePathLast);
            String mediaFilePath = "/tmp/forms2/" + filePathLast.split(".xml")[0] + MEDIA_SUFFIX + "/" + filePathLast;
            log.info("Media Path ::" + mediaFilePath);
            formXml = new File(mediaFilePath);
        }

        FormDef formDef = null;
        try {
            formDef = createFormDefFromCacheOrXml(formPath, formXml);
            log.info("Got formDef");
        } catch (StackOverflowError e) {
            log.severe("CP 1" + e.getMessage());
        }

        if (formDef == null) {
            return null;
        }

        final FormEntryModel fem = new FormEntryModel(formDef);
        FormEntryController fec = new FormEntryController(fem);

        boolean usedSavepoint = false;

        try {
            log.info("Initializing form.");
            final long start = System.currentTimeMillis();
            usedSavepoint = initializeForm(formDef, fec);
            log.info("Form initialized in %.3f seconds." + (System.currentTimeMillis() - start) / 1000F);
        } catch (RuntimeException e) {
            log.severe(e.getMessage());
            if (e.getCause() instanceof XPathTypeMismatchException) {
                // this is a case of
                // https://bitbucket.org/m
                // .sundt/javarosa/commits/e5d344783e7968877402bcee11828fa55fac69de
                // the data are imported, the survey will be unusable
                // but we should give the option to the user to edit the form
                // otherwise the survey will be TOTALLY inaccessible.
                log.severe("We have a syntactically correct instance, but the data threw an "
                        + "exception inside JR. We should allow editing.");
            } else {
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (xpath != null && !xpath.isEmpty()) {
            FormIndex idx = getIndexFromXPath(xpath, fec);
            if (idx == null) {
                log.severe("Unable to evaluate the formIndex");
                log.severe("xpath ::" + xpath);
                log.severe(instanceXML);
                log.severe(answer);
                log.severe("__________________________________");
            } else {
                fec.jumpToIndex(idx);
            }
        }
        return new FECWrapper(fec, usedSavepoint);
    }
    
    /**
     * Get XMessage payload with bind attributes added to it
     * 
     * @param payload
     * @param bindAttributes
     * @return XMessagePayload
     */
    private XMessagePayload getPayloadWithBindTags(XMessagePayload payload, List<TreeElement> bindAttributes) {
    	log.info("Bind Attributes: "+bindAttributes);
    	try {
    		bindAttributes.forEach(attribute -> {
        		if(attribute.getName().equals("stylingTags")) {
        			String tagText = attribute.getAttributeValue().toString();
        			StylingTag tag = StylingTag.getEnumByText(tagText);
        			if(tag != null) {
        				payload.setStylingTag(tag);
        			}
        		} else if(attribute.getName().equals("flow")) {
        			payload.setFlow(attribute.getAttributeValue().toString());
        		} else if(attribute.getName().equals("index")) {
        			try {
        				payload.setQuestionIndex(Integer.parseInt(attribute.getAttributeValue()));
        			} catch (IllegalArgumentException e) {
        				log.info("Exception in getPayloadWithBindTags for parse int: "+e.getMessage());
        			}
        		} else if(attribute.getName().equals("caption")) {
        			payload.setMediaCaption(attribute.getAttributeValue().toString());
        		} 
        	});
    	} catch (Exception e) {
    		log.info("Exception in getPayloadWithBindTags: "+e.getMessage());
    	}
    	
    	return payload;
    }
}
