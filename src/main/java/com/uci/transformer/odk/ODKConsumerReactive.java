package com.uci.transformer.odk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.uci.adapter.cdn.FileCdnFactory;
import com.uci.dao.models.XMessageDAO;
import com.uci.dao.repository.XMessageRepository;
import com.uci.transformer.TransformerProvider;
import com.uci.transformer.odk.services.SurveyService;
import com.uci.utils.service.UserService;
import com.uci.transformer.odk.entity.Assessment;
import com.uci.transformer.odk.entity.GupshupMessageEntity;
import com.uci.transformer.odk.entity.GupshupStateEntity;
import com.uci.transformer.odk.entity.Question;
import com.uci.transformer.odk.persistance.FormsDao;
import com.uci.transformer.odk.persistance.JsonDB;
import com.uci.transformer.odk.repository.AssessmentRepository;
import com.uci.transformer.odk.repository.MessageRepository;
import com.uci.transformer.odk.repository.QuestionRepository;
import com.uci.transformer.odk.repository.StateRepository;
import com.uci.transformer.odk.utilities.FormUpdation;
import com.uci.transformer.odk.utilities.FormInstanceUpdation;
import com.uci.transformer.telemetry.AssessmentTelemetryBuilder;
import com.uci.utils.CampaignService;
import com.uci.utils.cache.service.RedisCacheService;
import com.uci.utils.kafka.SimpleProducer;
import com.uci.utils.telemetry.service.PosthogService;

import io.fusionauth.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import messagerosa.core.model.ButtonChoice;
import messagerosa.core.model.LocationParams;
import messagerosa.core.model.SenderReceiverInfo;
import messagerosa.core.model.Transformer;
import messagerosa.core.model.XMessage;
import messagerosa.core.model.XMessage.MessageState;
import messagerosa.core.model.XMessagePayload;
import messagerosa.xml.XMessageParser;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.client.RestTemplate;
import reactor.blockhound.BlockHound;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.util.function.Tuple2;

import javax.xml.bind.JAXBException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static messagerosa.core.model.XMessage.MessageState.NOT_SENT;
import static messagerosa.core.model.XMessage.MessageType.HSM;

@Component
@RequiredArgsConstructor
@Slf4j
public class ODKConsumerReactive extends TransformerProvider {

    private final Flux<ReceiverRecord<String, String>> reactiveKafkaReceiver;

    private static final String SMS_BROADCAST_IDENTIFIER = "Broadcast";
    public static final String XML_PREFIX = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

    @Value("${outbound}")
    public String outboundTopic;

    @Value("${processOutbound}")
    private String processOutboundTopic;

    @Value("${telemetry}")
    public String telemetryTopic;

    @Value("${exhaust.telemetry.enabled}")
    public String exhaustTelemetryEnabled;

    @Value("${posthog.event.enabled}")
    public String posthogEventEnabled;

    @Autowired
    public SimpleProducer kafkaProducer;

    @Autowired
    QuestionRepository questionRepo;

    @Autowired
    AssessmentRepository assessmentRepo;

    @Autowired
    private StateRepository stateRepo;

    @Autowired
    private MessageRepository msgRepo;

    @Autowired
    XMessageRepository xMsgRepo;

    @Qualifier("custom")
    @Autowired
    private RestTemplate customRestTemplate;

    @Qualifier("rest")
    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    CampaignService campaignService;

    @Autowired
    UserService userService;

    @Value("${producer.id}")
    private String producerID;

    @Value("${assesment.character.go_to_start}")
    public String assesGoToStartChar;

    public MenuManager menuManager;

    public Boolean isStartingMessage;

    @Autowired
    public PosthogService posthogService;

    @Autowired
    public RedisCacheService redisCacheService;

    @Autowired
    public FileCdnFactory fileCdnFactory;

    @Value("${generic-transformer}")
    private String genericTransformer;

    @Autowired
    private SurveyService surveyService;

    @EventListener(ApplicationStartedEvent.class)
    public void onMessage() {
        reactiveKafkaReceiver
                .doOnNext(new Consumer<ReceiverRecord<String, String>>() {
                    @Override
                    public void accept(ReceiverRecord<String, String> stringMessage) {
                        final long startTime = System.nanoTime();
                        try {
                            XMessage msg = XMessageParser.parse(new ByteArrayInputStream(stringMessage.value().getBytes()));
                            logTimeTaken(startTime, 1);
                            transform(msg)
                                    .subscribe(new Consumer<XMessage>() {
                                        @Override
                                        public void accept(XMessage transformedMessage) {
                                            logTimeTaken(startTime, 2);
                                            if (transformedMessage != null) {
                                                try {
                                                    if (transformedMessage.getTransformers() != null && transformedMessage.getTransformers().get(0) != null
                                                            && transformedMessage.getTransformers().get(0).getMetaData() != null && transformedMessage.getTransformers().get(0).getMetaData().get("type") != null
                                                            && transformedMessage.getTransformers().get(0).getMetaData().get("type").equals("generic")) {
                                                        kafkaProducer.send(genericTransformer, transformedMessage.toXML());

                                                    } else {
                                                        kafkaProducer.send(processOutboundTopic, transformedMessage.toXML());
                                                    }
                                                    long endTime = System.nanoTime();
                                                    long duration = (endTime - startTime);
                                                    log.error("Total time spent in processing form: " + duration / 1000000);
                                                } catch (JAXBException e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                        }
                                    });
                        } catch (JAXBException e) {
                            e.printStackTrace();
                        } catch (NullPointerException e) {
                            log.error("An error occured : " + e.getMessage() + " at line no : " + e.getStackTrace()[0].getLineNumber()
                                    + " in class : " + e.getStackTrace()[0].getClassName());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                })
                .doOnError(new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable e) {
                        System.out.println(e.getMessage());
                        log.error("KafkaFlux exception", e);
                    }
                }).subscribe();

    }

    @Override
    public Mono<XMessage> transform(XMessage xMessage) throws Exception {
        XMessage[] finalXMsg = new XMessage[1];
        ArrayList<Transformer> transformers = xMessage.getTransformers();
        Transformer transformer = transformers.get(0);

        log.info("1 To User ID:" + xMessage.getTo().getUserID());
        String formID = ODKConsumerReactive.this.getTransformerMetaDataValue(transformer, "formID");

        if (formID.equals("")) {
            log.error("Unable to find form ID from Conversation Logic");
            return null;
        }

        log.info("current form ID:" + formID);
        String formPath = getFormPath(formID);
        log.info("current form path:" + formPath);
        if (formPath == null) {
            return null;
        }
        isStartingMessage = xMessage.getPayload().getText() == null ? false : xMessage.getPayload().getText().equals(getTransformerMetaDataValue(transformer, "startingMessage"));
        Boolean addOtherOptions = xMessage.getProvider().equals("sunbird") ? true : false;

        // Get details of user from database
        return getPreviousMetadata(xMessage, formID)
                .map(new Function<FormManagerParams, Mono<Mono<XMessage>>>() {
                    @Override
                    public Mono<Mono<XMessage>> apply(FormManagerParams previousMeta) {
                        final ServiceResponse[] response = new ServiceResponse[1];
                        MenuManager mm;
                        ObjectMapper mapper = new ObjectMapper();
                        JSONObject camp = null; //  is not being used in menumanager, only being added in constructor
                        // Remove camp from MenuManager construction
                        String hiddenFieldsStr = getTransformerMetaDataValue(transformer, "hiddenFields");


                        String serviceClass = getTransformerMetaDataValue(transformer, "serviceClass");
                        JSONObject user = null;
                        if (serviceClass.equalsIgnoreCase(SurveyService.class.getSimpleName())) {
                            user = surveyService.getUserByPhoneFromFederatedServers(hiddenFieldsStr, xMessage.getTo().getUserID());
                        } else {
                            user = userService.getUserByPhoneFromFederatedServers(
                                    getTransformerMetaDataValue(transformer, "botId"),
                                    xMessage.getTo().getUserID()
                            );
                        }

                        log.info("Federated User by phone : " + user);
//                        try {
//                            camp = new JSONObject(mapper.writeValueAsString(campaign));
//                        } catch (JsonProcessingException e) {
//                            e.printStackTrace();
//                        }
//                        String hiddenFieldsStr = getTransformerMetaDataValue(transformer, "hiddenFields");
                        ArrayNode hiddenFields = null;
                        try {
                            hiddenFields = (ArrayNode) mapper.readTree(hiddenFieldsStr);
                            log.info("hiddenFields: " + hiddenFields);
                        } catch (Exception ex) {
                            log.error("Exception in hidden fields read: " + ex.getMessage());
//                            ex.printStackTrace();
                        }

                        String instanceXMlPrevious = "";
                        Boolean prefilled;
                        String answer;
                        if (previousMeta.instanceXMlPrevious == null || previousMeta.currentAnswer.equals(assesGoToStartChar) || isStartingMessage) {
//                                            if (!lastFormID.equals(formID) || previousMeta.instanceXMlPrevious == null || previousMeta.currentAnswer.equals(assesGoToStartChar) || isStartingMessage) {
                            /* If bot restarted - create new session id */
                            if (previousMeta.currentAnswer.equals(assesGoToStartChar)) {
                                xMessage.setSessionId(UUID.randomUUID());
                            }
                            previousMeta.currentAnswer = assesGoToStartChar;
                            ServiceResponse serviceResponse = new MenuManager(null,
                                    null, null, formPath, formID, false,
                                    questionRepo, redisCacheService, xMessage.getTo().getUserID(), xMessage.getApp(), null).start();
                            FormInstanceUpdation ss = FormInstanceUpdation.builder().build();
                            ss.parse(serviceResponse.currentResponseState);
                            ss.updateAdapterProperties(xMessage.getChannel(), xMessage.getProvider());
                            ss.updateParams("phone_number", xMessage.getTo().getUserID());
                            instanceXMlPrevious = ss.updateHiddenFields(hiddenFields, (JSONObject) user).getXML();
                            prefilled = false;
                            answer = null;
                            log.info("Condition 1 - xpath: null, answer: null, instanceXMlPrevious: "
                                    + instanceXMlPrevious + ", formPath: " + formPath + ", formID: " + formID);
                            mm = new MenuManager(null, null, instanceXMlPrevious,
                                    formPath, formID, redisCacheService, xMessage.getTo().getUserID(), xMessage.getApp(), xMessage.getPayload(), fileCdnFactory.getFileCdnProvider());
                            response[0] = mm.start();
                        } else {
                            FormInstanceUpdation ss = FormInstanceUpdation.builder().build();
                            if (previousMeta.previousPath.equals("question./data/group_matched_vacancies[1]/initial_interest[1]")) {
                                ss.parse(previousMeta.instanceXMlPrevious);
                                ss.updateAdapterProperties(xMessage.getChannel(), xMessage.getProvider());

                                JSONObject vacancyDetails = null;
                                for (int j = 0; j < user.getJSONArray("matched").length(); j++) {
                                    String vacancyID = String.valueOf(user.getJSONArray("matched").getJSONObject(j).getJSONObject("vacancy_detail").getInt("id"));
                                    if (previousMeta.currentAnswer.equals(vacancyID)) {
                                        vacancyDetails = user.getJSONArray("matched").getJSONObject(j).getJSONObject("vacancy_detail");
                                    }
                                }
                                ss.updateHiddenFields(hiddenFields, user);
                                int idToBeDeleted = -1;
                                for (int i = 0; i < hiddenFields.size(); i++) {
                                    JsonNode object = hiddenFields.get(i);
                                    String userField = object.findValue("name").asText();
                                    if (userField.equals("candidate_id")) {
                                        idToBeDeleted = i;
                                    }
                                }
                                if (idToBeDeleted > -1) hiddenFields.remove(idToBeDeleted);
                                instanceXMlPrevious = instanceXMlPrevious + ss.updateHiddenFields(hiddenFields, (JSONObject) vacancyDetails).getXML();
                                prefilled = false;
                                answer = previousMeta.currentAnswer;
                                log.info("Condition 1 - xpath: " + previousMeta.previousPath + ", answer: " + answer + ", instanceXMlPrevious: "
                                        + instanceXMlPrevious + ", formPath: " + formPath + ", formID: " + formID + ", prefilled: " + prefilled
                                        + ", questionRepo: " + questionRepo + ", user: " + user + ", shouldUpdateFormXML: true, campaign: " + camp);
                                mm = new MenuManager(previousMeta.previousPath, answer,
                                        instanceXMlPrevious, formPath, formID,
                                        prefilled, questionRepo, user, true, redisCacheService, xMessage, fileCdnFactory.getFileCdnProvider());
                            } else {
                                prefilled = false;
                                answer = previousMeta.currentAnswer;
                                instanceXMlPrevious = previousMeta.instanceXMlPrevious;
                                log.info("Condition 1 - xpath: " + previousMeta.previousPath + ", answer: " + answer + ", instanceXMlPrevious: "
                                        + instanceXMlPrevious + ", formPath: " + formPath + ", formID: " + formID + ", prefilled: " + prefilled
                                        + ", questionRepo: " + questionRepo + ", user: " + user + ", shouldUpdateFormXML: true, campaign: " + camp);
                                mm = new MenuManager(previousMeta.previousPath, answer,
                                        instanceXMlPrevious, formPath, formID,
                                        prefilled, questionRepo, user, true, redisCacheService, xMessage, fileCdnFactory.getFileCdnProvider());
                            }
                            response[0] = mm.start();
                        }

                        log.info("next question xpath:" + response[0].question.getXPath());

                        /* To use with previous question & question payload methods */
//                                            log.info("menu manager instanceXMlPrevious: "+instanceXMlPrevious);
                        menuManager = mm;

                        /* Previous Question Data */
                        Question prevQuestion = null;
                        if (!isStartingMessage) {
                            prevQuestion = menuManager.getQuestionFromXPath(previousMeta.previousPath);
                        }

                        // Save answerData => PreviousQuestion + CurrentAnswer
                        Mono<Pair<Boolean, List<Question>>> updateQuestionAndAssessment =
                                updateQuestionAndAssessment(
                                        previousMeta,
                                        getPreviousQuestions(
                                                previousMeta.previousPath,
                                                formID,
                                                response[0].formVersion),
                                        formID,
                                        transformer,
                                        xMessage,
                                        response[0].question,
                                        prevQuestion,
                                        response[0].currentIndex,
                                        response[0].validResponse
                                );


                        /**
                         *  This is for doubtnut hop bot hardcode
                         **/
                        if (response[0].currentIndex.contains("eof__") && response[0].currentIndex.contains("doubtnut")) {
                            String nextBotID = mm.getNextBotID(response[0].currentIndex);
                            return campaignService.getCampaignFromID(nextBotID).map(new Function<JsonNode, Mono<XMessage>>() {
                                @Override
                                public Mono<XMessage> apply(JsonNode jsonNode) {
                                    JsonNode data = jsonNode.get("data");
                                    ArrayList<Transformer> transformers = new ArrayList<Transformer>();
                                    ArrayList transformerList = (ArrayList) data.findValues("transformers");

                                    transformerList.forEach(transformerTmp -> {
                                        JsonNode transformerNode = (JsonNode) transformerTmp;
                                        int i = 0;
                                        while (transformerNode.get(i) != null) {
                                            JsonNode transformer = transformerNode.get(i);
                                            log.info("transformer:" + transformer);

                                            HashMap<String, String> metaData = new HashMap<String, String>();
                                            if (data.findValue("ownerID").asText().equals("null")) {
                                                metaData.put("botOwnerID", "");
                                            } else {
                                                metaData.put("botOwnerID", data.findValue("ownerID").asText());
                                            }
                                            if (data.findValue("ownerOrgID").asText().equals("null")) {
                                                metaData.put("botOwnerOrgID", "");
                                            } else {
                                                metaData.put("botOwnerOrgID", data.findValue("ownerOrgID").asText());
                                            }
                                            metaData.put("startingMessage", data.findValue("startingMessage").asText());
                                            metaData.put("type", "generic");

                                            Transformer transf = new Transformer();
                                            transf.setId(transformer.get("id").asText());
                                            transf.setMetaData(metaData);
                                            transformers.add(transf);
                                            i++;
                                        }
                                    });
                                    xMessage.setTransformers(transformers);
                                    XMessagePayload payload = xMessage.getPayload();
                                    payload.setText(data.path("startingMessage").asText());
                                    xMessage.setPayload(payload);
                                    xMessage.setApp(data.path("name").asText());
                                    if (data.findValue("ownerID") != null && !data.findValue("ownerID").asText().equals("null")) {
                                        xMessage.setOwnerId(data.findValue("ownerID").asText());
                                    } else {
                                        xMessage.setOwnerId("");
                                    }
                                    if (data.findValue("ownerOrgID") != null && !data.findValue("ownerOrgID").asText().equals("null")) {
                                        xMessage.setOwnerOrgId(data.findValue("ownerOrgID").asText());
                                    } else {
                                        xMessage.setOwnerOrgId("");
                                    }
                                    xMessage.setBotId(UUID.fromString(data.path("id").asText()));
                                    xMessage.setSessionId(UUID.randomUUID());
                                    return Mono.just(xMessage);
                                }
                            });
                        }
                        /* If form contains eof__, then process next bot by id addded with eof__bot_id, else process message */
                        else if (response[0].currentIndex.contains("eof__")) {
                            String nextBotID = mm.getNextBotID(response[0].currentIndex);

                            return Mono.zip(
                                    campaignService.getBotNameByBotID(nextBotID),
                                    campaignService.getFirstFormByBotID(nextBotID)
                            ).map(new Function<Tuple2<String, String>, Mono<XMessage>>() {
                                @Override
                                public Mono<XMessage> apply(Tuple2<String, String> objects) {
                                    String nextFormID = objects.getT2();
                                    String nextAppName = objects.getT1();

                                    ServiceResponse serviceResponse = new MenuManager(
                                            null, null, null,
                                            getFormPath(nextFormID), nextFormID,
                                            false, questionRepo, redisCacheService, xMessage.getTo().getUserID(), xMessage.getApp(), null)
                                            .start();
                                    FormInstanceUpdation ss = FormInstanceUpdation.builder().build();
                                    ss.parse(serviceResponse.currentResponseState);
                                    ss.updateAdapterProperties(xMessage.getChannel(), xMessage.getProvider());
//                                                        String instanceXMlPrevious = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
//                                                                ss.getXML();
                                    String instanceXMlPrevious = ss.getXML();
                                    log.debug("Instance value >> " + instanceXMlPrevious);
                                    MenuManager mm2 = new MenuManager(null, null,
                                            instanceXMlPrevious, getFormPath(nextFormID), nextFormID, true,
                                            questionRepo, redisCacheService, xMessage.getTo().getUserID(), xMessage.getApp(), null);
                                    ServiceResponse response = mm2.start();
                                    xMessage.setApp(nextAppName);
                                    return decodeXMessage(xMessage, response, nextFormID, updateQuestionAndAssessment);
                                }
                            });
                        } else {
                            return Mono.just(decodeXMessage(xMessage, response[0], formID, updateQuestionAndAssessment));
                        }
                    }
                }).flatMap(new Function<Mono<Mono<XMessage>>, Mono<XMessage>>() {
                    @Override
                    public Mono<XMessage> apply(Mono<Mono<XMessage>> m) {
                        log.info("Level 1");
                        return m.flatMap(new Function<Mono<XMessage>, Mono<? extends XMessage>>() {
                            @Override
                            public Mono<? extends XMessage> apply(Mono<XMessage> n) {
                                log.info("Level 2");
                                return n;
                            }
                        });
                    }
                });
    }

    /**
     * Check if form has ended by xpath
     *
     * @param xPath
     * @return
     */
    private Boolean isEndOfForm(String xPath) {
        log.info("xPath for isEndOfForm check: " + xPath);
        return xPath.contains("endOfForm") || xPath.contains("eof");
    }

    private Mono<FormManagerParams> getPreviousMetadata(XMessage message, String formID) {
        String prevPath = null;
        String prevXMl = null;
        FormManagerParams formManagerParams = new FormManagerParams();

        if (!message.getMessageState().equals(XMessage.MessageState.OPTED_IN)) {
            return stateRepo.findByPhoneNoAndBotFormName(message.getTo().getUserID(), formID)
                    .defaultIfEmpty(new GupshupStateEntity())
                    .map(new Function<GupshupStateEntity, FormManagerParams>() {
                        @Override
                        public FormManagerParams apply(GupshupStateEntity stateEntity) {
                            String prevXMl = null, prevPath = null;
                            if (stateEntity != null && message.getPayload() != null) {
                                prevXMl = stateEntity.getXmlPrevious();
                                prevPath = stateEntity.getPreviousPath();
                            }

                            // Handle image responses to a question
                            if (message.getPayload() != null) {
                                if (message.getPayload().getMedia() != null) {
                                    formManagerParams.setCurrentAnswer(message.getPayload().getMedia().getUrl());
                                } else if (message.getPayload().getLocation() != null) {
                                    formManagerParams.setCurrentAnswer(getLocationContentText(message.getPayload().getLocation()));
                                } else {
                                    formManagerParams.setCurrentAnswer(message.getPayload().getText());
                                }
                            } else {
                                formManagerParams.setCurrentAnswer("");
                            }
                            formManagerParams.setPreviousPath(prevPath);
                            formManagerParams.setInstanceXMlPrevious(prevXMl);
                            return formManagerParams;
                        }
                    })
                    .doOnError(e -> log.error(e.getMessage()));
        } else {
            formManagerParams.setCurrentAnswer("");
            formManagerParams.setPreviousPath(prevPath);
            formManagerParams.setInstanceXMlPrevious(prevXMl);
            return Mono.just(formManagerParams);
        }
    }

    /**
     * Get location content text
     *
     * @param location
     * @return
     */
    private String getLocationContentText(LocationParams location) {
        String text = "";
        text = location.getLatitude() + " " + location.getLongitude();
        if (location.getAddress() != null && !location.getAddress().isEmpty()) {
            text += " " + location.getAddress();
        }
        if (location.getName() != null && !location.getName().isEmpty()) {
            text += " " + location.getName();
        }
        if (location.getUrl() != null && !location.getUrl().isEmpty()) {
            text += " " + location.getUrl();
        }
        return text.trim();
    }

    @NotNull
    private Mono<Pair<Boolean, List<Question>>> updateQuestionAndAssessment(FormManagerParams previousMeta,
                                                                            Mono<Pair<Boolean, List<Question>>> previousQuestions, String formID,
                                                                            Transformer transformer, XMessage xMessage, Question question, Question prevQuestion,
                                                                            String currentXPath, Boolean validResponse) {
        return previousQuestions
                .doOnNext(new Consumer<Pair<Boolean, List<Question>>>() {
                    @Override
                    public void accept(Pair<Boolean, List<Question>> existingQuestionStatus) {
                        if (existingQuestionStatus.getLeft()) {
                            log.info("Found Question id: " + existingQuestionStatus.getRight().get(0).getId() + ", xPath: " + existingQuestionStatus.getRight().get(0).getXPath());
                            saveAssessmentData(
                                    existingQuestionStatus, formID, previousMeta, transformer, xMessage, null, currentXPath, validResponse).subscribe(new Consumer<Assessment>() {
                                @Override
                                public void accept(Assessment assessment) {
                                    log.info("Assessment Saved Successfully {}", assessment.getId());
                                }
                            });
                        } else {
                            Question saveQuestion;
                            if (prevQuestion == null) {
                                saveQuestion = question;
                            } else {
                                saveQuestion = prevQuestion;
                            }
                            saveQuestion(saveQuestion).subscribe(new Consumer<Question>() {
                                @Override
                                public void accept(Question question) {
                                    log.info("Question Saved Successfully, id: " + question.getId() + ", xPath: " + question.getXPath());
                                    saveAssessmentData(
                                            existingQuestionStatus, formID, previousMeta, transformer, xMessage, question, currentXPath, validResponse).subscribe(new Consumer<Assessment>() {
                                        @Override
                                        public void accept(Assessment assessment) {
                                            log.info("Assessment Saved Successfully {}", assessment.getId());
                                        }
                                    });
                                }
                            });
                        }
                    }
                });
    }

    private Mono<Pair<Boolean, List<Question>>> getPreviousQuestions(String previousPath, String formID, String formVersion) {
        return questionRepo
                .findQuestionByXPathAndFormIDAndFormVersion(previousPath, formID, formVersion)
                .collectList()
                .flatMap(new Function<List<Question>, Mono<Pair<Boolean, List<Question>>>>() {
                    @Override
                    public Mono<Pair<Boolean, List<Question>>> apply(List<Question> questions) {
                        Pair<Boolean, List<Question>> response = Pair.of(false, new ArrayList<Question>());
                        if (questions != null && questions.size() > 0) {
                            response = Pair.of(true, questions);
                        }
                        return Mono.just(response);
                    }
                });
    }

    private Mono<Question> saveQuestion(Question question) {
        return questionRepo.save(question);
    }

    private Mono<Assessment> saveAssessmentData(Pair<Boolean, List<Question>> existingQuestionStatus,
                                                String formID, FormManagerParams previousMeta,
                                                Transformer transformer, XMessage xMessage, Question question,
                                                String currentXPath, Boolean validResponse) {
        if (question == null) question = existingQuestionStatus.getRight().get(0);

        UUID userID = xMessage.getTo().getDeviceID() != null && !xMessage.getTo().getDeviceID().isEmpty() && xMessage.getTo().getDeviceID() != "" ? UUID.fromString(xMessage.getTo().getDeviceID()) : null;
        log.info("User uuid:" + userID);

        Assessment assessment = Assessment.builder()
                .question(question)
                .deviceID(userID)
                .answer(previousMeta.currentAnswer)
                .botID(UUID.fromString(getTransformerMetaDataValue(transformer, "botId")))
                .userID(userID)
                .build();
        try {
            if (question != null) {
                log.info("In saveAssessmentData, question id: " + question.getId() + ", question xpath: " + question.getXPath());
            } else {
                log.info("In saveAssessmentData, Question empty: " + question);
            }

            if (question != null && !isStartingMessage) {

                XMessagePayload questionPayload = menuManager.getQuestionPayloadFromXPath(question.getXPath());

                sendEvents(xMessage, questionPayload, assessment, transformer, currentXPath, validResponse);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        log.info("question xpath:" + question.getXPath() + ",answer: " + assessment.getAnswer());

        return assessmentRepo.save(assessment)
                .doOnError(new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {
                        log.error(throwable.getMessage());
                    }
                })
                .doOnNext(new Consumer<Assessment>() {
                    @Override
                    public void accept(Assessment assessment) {
                        log.info("Assessment Saved by id: " + assessment.getId());
                    }
                });
    }

    private void sendEvents(XMessage xMessage, XMessagePayload questionPayload, Assessment assessment, Transformer transformer,
                            String currentXPath, Boolean validResponse) throws Exception {
        log.info("find xmessage by app: " + xMessage.getApp() + ", userId: " + xMessage.getTo().getUserID() + ", fromId: admin, status: " + MessageState.SENT.name());

        /* Get Previous question XMessage */
        getLastSentXMessage(xMessage.getApp(), xMessage.getTo().getUserID())
                .subscribe(new Consumer<XMessageDAO>() {
                    @Override
                    public void accept(XMessageDAO xMsgDao) {
                        log.info("found xMsgDao");

                        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                        LocalDateTime repliedTimestamp = null;
                        /* Convert replied timestamp to local date time format */
                        if (xMessage.getTimestamp() != null) {
                            try {
                                LocalDateTime repliedAt = new Timestamp(new Date(xMessage.getTimestamp()).getTime()).toLocalDateTime();
                                String repliedAtTime = fmt.format(repliedAt).toString();
                                repliedTimestamp = LocalDateTime.parse(repliedAtTime, fmt);
                            } catch (Exception ex) {
                                log.error("Exception when conversatin replied timestamp: " + ex.getMessage());
                            }
                        }
                        /* If replied timestamp is null, use current date time as replied timestamp */
                        if (repliedTimestamp == null) {
                            LocalDateTime localNow = LocalDateTime.now();
                            String current = fmt.format(localNow).toString();
                            repliedTimestamp = LocalDateTime.parse(current, fmt);
                        }

                        /* Last sent message timestamp  */
                        LocalDateTime sentTimestamp = xMsgDao.getTimestamp();
                        long diff_milis = ChronoUnit.MILLIS.between(sentTimestamp, repliedTimestamp);
                        long diff_secs = ChronoUnit.SECONDS.between(sentTimestamp, repliedTimestamp);
                        String telemetryEvent = new AssessmentTelemetryBuilder()
                                .build(getTransformerMetaDataValue(transformer, "botOwnerOrgID"),
                                        xMessage.getChannel(),
                                        xMessage.getProvider(),
                                        producerID,
                                        getTransformerMetaDataValue(transformer, "botOwnerID"),
                                        assessment.getQuestion(),
                                        assessment,
                                        questionPayload,
                                        diff_secs,
                                        xMessage.getTo().getEncryptedDeviceID(),
                                        xMessage.getMessageId().getChannelMessageId(),
                                        isEndOfForm(currentXPath),
                                        xMessage.getSessionId(),
                                        validResponse);
                        log.info("Telemetry Event: " + telemetryEvent);
                        if (exhaustTelemetryEnabled.equalsIgnoreCase("true")) {
                            try {
                                sendExhaustEvent(telemetryEvent);
                            } catch (Exception e) {
                                log.error("Exception in exhaust event: " + e.getMessage());
                            }
                        }
                        if (posthogEventEnabled.equalsIgnoreCase("true")) {
                            try {
                                sendPosthogEvent(xMessage, telemetryEvent, questionPayload, diff_milis);
                            } catch (Exception e) {
                                log.error("Exception in posthog event: " + e.getMessage());
                            }
                        }
                    }
                });
    }

    /**
     * Send the telemetry event for exhaust
     *
     * @param telemetryEvent
     * @throws Exception
     */
    private void sendExhaustEvent(String telemetryEvent) throws Exception {
        kafkaProducer.send(telemetryTopic, telemetryEvent);
    }

    /**
     * Send the telemetry & dropoff event to posthog
     *
     * @param xMessage
     * @param telemetryEvent
     * @param questionPayload
     * @param diff_milis
     * @throws Exception
     */
    private void sendPosthogEvent(XMessage xMessage, String telemetryEvent, XMessagePayload questionPayload, Long diff_milis) throws Exception {
        /* Send Telemetry event to posthog */
        posthogService.sendTelemetryEvent(xMessage.getTo().getUserID(), telemetryEvent).subscribe(new Consumer<String>() {
            @Override
            public void accept(String t) {
                // TODO Auto-generated method stub
                log.info("Posthog telemetry event response: " + t);
            }
        });

        /* Send drop off event to posthog, if flow & question index exists */
        if (questionPayload.getFlow() != null
                && !questionPayload.getFlow().isEmpty()
                && questionPayload.getQuestionIndex() != null) {
            posthogService.sendDropOffEvent(
                            xMessage.getTo().getUserID(), questionPayload.getFlow().toString(),
                            questionPayload.getQuestionIndex(), diff_milis)
                    .subscribe(new Consumer<String>() {
                        @Override
                        public void accept(String t) {
                            // TODO Auto-generated method stub
                            log.info("Posthog dropoff event response: " + t);
                        }
                    });
        }
    }

    /**
     * Get Last XMessage sent to user from admin
     *
     * @param appName
     * @param userID
     * @return
     */
    private Flux<XMessageDAO> getLastSentXMessage(String appName, String userID) {
        return xMsgRepo.findFirstByAppAndUserIdAndFromIdAndMessageStateOrderByTimestampDesc(appName, userID, "admin", MessageState.SENT.name());
    }

    private Mono<XMessage> decodeXMessage(XMessage xMessage, ServiceResponse response, String formID, Mono<Pair<Boolean, List<Question>>> updateQuestionAndAssessment) {
        XMessage nextMessage = getMessageFromResponse(xMessage, response);
        if (isEndOfForm(response)) {
            return Mono.zip(
                            appendNewResponse(formID, xMessage, response),
                            replaceUserState(formID, xMessage, response),
                            updateQuestionAndAssessment,
                            Mono.just(new UploadService().submit(response.currentResponseState, restTemplate, customRestTemplate))
                    )
                    .then(Mono.just(getClone(nextMessage)));
        } else {
            return Mono.zip(
                            appendNewResponse(formID, xMessage, response),
                            replaceUserState(formID, xMessage, response),
                            updateQuestionAndAssessment
                    )
                    .then(Mono.just(getClone(nextMessage)));
        }
    }

    private boolean isEndOfForm(ServiceResponse response) {
        return response.getCurrentIndex().equals("endOfForm") || response.currentIndex.contains("eof");
    }

    /**
     * Get Meta data value by key in a transformer
     *
     * @param transformer
     * @param key
     * @return meta data value
     */
    private String getTransformerMetaDataValue(Transformer transformer, String key) {
        Map<String, String> metaData = transformer.getMetaData();
        if (metaData.get(key) != null && !metaData.get(key).toString().isEmpty()) {
            return metaData.get(key).toString();
        }
        return "";
    }

    @Nullable
    private XMessage getClone(XMessage nextMessage) {
        XMessage cloneMessage = null;
        try {
            cloneMessage = XMessageParser.parse(new ByteArrayInputStream(nextMessage.toXML().getBytes()));
        } catch (JAXBException e) {
            e.printStackTrace();
        }
        return cloneMessage;
    }

    private XMessage getMessageFromResponse(XMessage xMessage, ServiceResponse response) {
        XMessagePayload payload = response.getNextMessage();
        xMessage.setPayload(payload);
        xMessage.setConversationLevel(response.getConversationLevel());
        return xMessage;
    }

    public static String getFormPath(String formID) {
        FormsDao dao = new FormsDao(JsonDB.getInstance().getDB());
        try {
            return dao.getFormsCursorForFormId(formID).getFormFilePath();
        } catch (NullPointerException ex) {
            log.info("ODK form not found '" + formID + "'");
            return null;
        }
    }

    private Mono<GupshupMessageEntity> appendNewResponse(String formID, XMessage xMessage, ServiceResponse response) {
        GupshupMessageEntity msgEntity = new GupshupMessageEntity();
        msgEntity.setPhoneNo(xMessage.getTo().getUserID());
        msgEntity.setMessage(xMessage.getPayload().getText());
        msgEntity.setLastResponse(response.getCurrentIndex().equals("endOfForm"));
        return msgRepo.save(msgEntity);
    }

    private Mono<GupshupStateEntity> replaceUserState(String formID, XMessage xMessage, ServiceResponse response) {
        log.info("Saving State");
        return stateRepo.findByPhoneNoAndBotFormName(xMessage.getTo().getUserID(), formID)
                .defaultIfEmpty(new GupshupStateEntity())
                .map(new Function<GupshupStateEntity, Mono<GupshupStateEntity>>() {
                    @Override
                    public Mono<GupshupStateEntity> apply(GupshupStateEntity saveEntity) {
                        log.info("Saving the ", xMessage.getTo().getUserID());
                        saveEntity.setPhoneNo(xMessage.getTo().getUserID());
                        saveEntity.setPreviousPath(response.getCurrentIndex());
                        saveEntity.setXmlPrevious(response.getCurrentResponseState());
                        saveEntity.setBotFormName(formID);
                        return stateRepo.save(saveEntity)
                                .doOnError(new Consumer<Throwable>() {
                                    @Override
                                    public void accept(Throwable throwable) {
                                        log.error("Unable to persist state entity {}", throwable.getMessage());
                                    }
                                }).doOnNext(new Consumer<GupshupStateEntity>() {
                                    @Override
                                    public void accept(GupshupStateEntity gupshupStateEntity) {
                                        log.info("Successfully persisted state entity");
                                    }
                                });
                    }
                }).flatMap(new Function<Mono<GupshupStateEntity>, Mono<? extends GupshupStateEntity>>() {
                    @Override
                    public Mono<? extends GupshupStateEntity> apply(Mono<GupshupStateEntity> gupshupStateEntityMono) {
                        return gupshupStateEntityMono;
                    }
                });

    }

    private void logTimeTaken(long startTime, int checkpointID) {
        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1000000;
        log.info(String.format("CP-%d: %d ms", checkpointID, duration));
    }

    private String redisKeyWithPrefix(String key) {
        return System.getenv("ENV") + "-" + key;
    }
}
