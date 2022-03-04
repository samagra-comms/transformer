package com.uci.transformer.odk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.uci.transformer.TransformerProvider;
import com.uci.transformer.User.UserService;
import com.uci.transformer.odk.entity.GupshupMessageEntity;
import com.uci.transformer.odk.entity.GupshupStateEntity;
import com.uci.transformer.odk.persistance.FormsDao;
import com.uci.transformer.odk.persistance.JsonDB;
import com.uci.transformer.odk.repository.AssessmentRepository;
import com.uci.transformer.odk.repository.MessageRepository;
import com.uci.transformer.odk.repository.QuestionRepository;
import com.uci.transformer.odk.repository.StateRepository;
import com.uci.transformer.odk.utilities.FormInstanceUpdation;
import com.uci.utils.CampaignService;
import com.uci.utils.kafka.SimpleProducer;
import lombok.extern.slf4j.Slf4j;
import messagerosa.core.model.SenderReceiverInfo;
import messagerosa.core.model.XMessage;
import messagerosa.core.model.XMessagePayload;
import messagerosa.xml.XMessageParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import javax.xml.bind.JAXBException;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static messagerosa.core.model.XMessage.MessageState.NOT_SENT;
import static messagerosa.core.model.XMessage.MessageType.HSM;

@Slf4j
@Component
public class VacancyIntentTransformer extends TransformerProvider {

    private static final String SMS_BROADCAST_IDENTIFIER = "Broadcast";
    public static final String XML_PREFIX = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

    @Value("${outbound}")
    public String outboundTopic;

    @Value("${telemetry}")
    public String telemetryTopic;

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

    @Qualifier("custom")
    @Autowired
    private RestTemplate customRestTemplate;

    @Qualifier("rest")
    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    CampaignService campaignService;

    @Value("${producer.id}")
    private String producerID;
    
    @Autowired
    public RedisTemplate<String, Object> redisTemplate;

    // Listen to all ODK based transformers
    @KafkaListener(id = "vacancyIntent-transformer", topics = {"com.rozgarbot.transformer"})
    public void consumeMessage(String message) throws Exception {
        log.error("Inside rozgarbot consumer");
        long startTime = System.nanoTime();
        logTimeTaken(startTime, 0);
        XMessage xMessage = XMessageParser.parse(new ByteArrayInputStream(message.getBytes()));
        logTimeTaken(startTime, 1);
        if (xMessage.getMessageType() == XMessage.MessageType.BROADCAST_TEXT) {
            this.transformToMany(xMessage).subscribe(new Consumer<List<XMessage>>() {
                @Override
                public void accept(List<XMessage> messages) {
                    messages = (ArrayList<XMessage>) messages;
                    for (XMessage msg : messages) {
                        try {
                            kafkaProducer.send(outboundTopic, msg.toXML());
                        } catch (JAXBException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        } else {
            this.transform(xMessage)
                    .subscribe(new Consumer<XMessage>() {
                        @Override
                        public void accept(XMessage transformedMessage) {
                            logTimeTaken(startTime, 2);
                            if (transformedMessage != null) {
                                try {
                                    kafkaProducer.send(outboundTopic, transformedMessage.toXML());
                                    long endTime = System.nanoTime();
                                    long duration = (endTime - startTime);
                                    log.error("Total time spent in processing form: " + duration / 1000000);
                                } catch (JAXBException e) {
                                    e.printStackTrace();
                                }

                            }
                        }
                    });

        }
    }

    private Mono<FormManagerParams> getPreviousMetadata(XMessage message, String formID) {
        String prevPath = null;
        String prevXMl = null;
        FormManagerParams formManagerParams = new FormManagerParams();

        if (!message.getMessageState().equals(XMessage.MessageState.OPTED_IN)) {
            return stateRepo.findByPhoneNoAndBotFormName(message.getTo().getUserID(), formID)
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
                                if (message.getPayload().getText() == null) {
                                    formManagerParams.setCurrentAnswer(message.getPayload().getMedia().getUrl());
                                } else formManagerParams.setCurrentAnswer(message.getPayload().getText());
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

    @Override
    public Mono<XMessage> transform(XMessage xMessage) throws Exception {
        XMessage[] finalXMsg = new XMessage[1];
        return campaignService
                .getCampaignFromNameTransformer(xMessage.getApp())
                .map(new Function<JsonNode, Mono<Mono<XMessage>>>() {
                    @Override
                    public Mono<Mono<XMessage>> apply(JsonNode campaign) {
                        if (campaign != null) {
                            String formID = "bla";
                            if (formID.equals("")) {
                                log.error("Unable to find form ID from Conversation Logic");
                                return null;
                            }
                            String formPath = getFormPath(formID);
                            boolean isStartingMessage = xMessage.getPayload().getText().equals(campaign.findValue("startingMessage").asText());
                            switchFromTo(xMessage);

                            // Get details of user from database
                            return getPreviousMetadata(xMessage, formID)
                                    .map(new Function<FormManagerParams, Mono<XMessage>>() {
                                        @Override
                                        public Mono<XMessage> apply(FormManagerParams previousMeta) {
                                            final ServiceResponse[] response = new ServiceResponse[1];
                                            MenuManager mm;
                                            if (previousMeta.instanceXMlPrevious == null || previousMeta.currentAnswer.equals("*") || isStartingMessage) {
                                                previousMeta.currentAnswer = "*";
                                                ServiceResponse serviceResponse = new MenuManager(null, null, null, formPath, formID, false, questionRepo, redisTemplate, xMessage.getTo().getUserID()).start();
                                                FormInstanceUpdation ss = FormInstanceUpdation.builder().build();
                                                ss.parse(serviceResponse.currentResponseState);
                                                ss.updateAdapterProperties(xMessage.getChannel(), xMessage.getProvider());
                                                String instanceXMlPrevious = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                                                        ss.getXML();
                                                log.debug("Instance value >> " + instanceXMlPrevious);
                                                mm = new MenuManager(null, null, instanceXMlPrevious, formPath, formID, true, questionRepo, redisTemplate, xMessage.getTo().getUserID());
                                                response[0] = mm.start();
                                            } else {
                                                mm = new MenuManager(previousMeta.previousPath, previousMeta.currentAnswer,
                                                        previousMeta.instanceXMlPrevious, formPath, formID, false, questionRepo, redisTemplate, xMessage.getTo().getUserID());
                                                response[0] = mm.start();
                                            }

                                            if (mm.isGlobal() && response[0].currentIndex.contains("eof__")) {
                                                String nextBotID = mm.getNextBotID(response[0].currentIndex);
                                                return campaignService
                                                        .getFirstFormByBotID(nextBotID)
                                                        .map(new Function<String, XMessage>() {
                                                                 @Override
                                                                 public XMessage apply(String nextFormID) {
                                                                     MenuManager mm2 = new MenuManager(null,
                                                                             null, null, getFormPath(nextFormID),
                                                                             nextFormID, false, questionRepo, redisTemplate, xMessage.getTo().getUserID());
                                                                     ServiceResponse response = mm2.start();
                                                                     finalXMsg[0] = decodeXMessage(xMessage, response, formID);
                                                                     return finalXMsg[0];
                                                                 }
                                                             }
                                                        );
                                            } else {
                                                finalXMsg[0] = decodeXMessage(xMessage, response[0], formID);
                                                return Mono.just(finalXMsg[0]);
                                            }
                                        }
                                    });
                        }
                        else {
                            log.error("Could not find Bot");
                            return Mono.just(null);
                        }
                    }
                }).flatMap(new Function<Mono<Mono<XMessage>>, Mono<XMessage>>() {
                    @Override
                    public Mono<XMessage> apply(Mono<Mono<XMessage>> m) {
                        log.info("Level 1");
                        return m.map(new Function<Mono<XMessage>, XMessage>() {
                            @Override
                            public XMessage apply(Mono<XMessage> n) {
                                log.info("Level 2");
                                return n.block();
                            }
                        });
                    }
                });
    }


    @NotNull
    private Mono<XMessage> globalBotToStateBotSwitch(ServiceResponse[] response, String nextBotID, XMessage xMessage) {
        return Mono.zip(
                campaignService.getFirstFormByBotID(nextBotID),
                campaignService.getBotNameByBotID(nextBotID)
        ).map(new Function<Tuple2<String, String>, XMessage>() {
            @Override
            public XMessage apply(Tuple2<String, String> result) {
                String nextFormID = result.getT1();
                String appName = result.getT2();
                MenuManager mm = new MenuManager(null, null, null,
                        getFormPath(nextFormID), nextFormID, false, questionRepo, redisTemplate, xMessage.getTo().getUserID());
                response[0] = mm.start();
                xMessage.setApp(appName);
                return decodeXMessage(xMessage, response[0], nextFormID);
            }
        });
    }

    private XMessage decodeXMessage(XMessage xMessage, ServiceResponse response, String formID) {
        XMessage nextMessage = getMessageFromResponse(xMessage, response);

        // Update database with new fields.
        appendNewResponse(formID, xMessage, response);
        replaceUserState(formID, xMessage, response);

        if (isEndOfForm(response)) {
            new UploadService().submit(response.currentResponseState, restTemplate, customRestTemplate);
        }

        return getClone(nextMessage);
    }

    private boolean isEndOfForm(ServiceResponse response) {
        return response.getCurrentIndex().equals("endOfForm") || response.currentIndex.contains("eof");
    }

    private String getFormID(JsonNode campaign) {
        try {
            return campaign.findValue("formID").asText();
        } catch (Exception e) {
            return "";
        }
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

    private void switchFromTo(XMessage xMessage) {
        SenderReceiverInfo from = xMessage.getFrom();
        SenderReceiverInfo to = xMessage.getTo();
        xMessage.setFrom(to);
        xMessage.setTo(from);
    }


    @Override
    public Mono<List<XMessage>> transformToMany(XMessage xMessage) {

        ArrayList<XMessage> messages = new ArrayList<>();
        // Get All Users with Data.
        return campaignService.getCampaignFromNameTransformer(xMessage.getCampaign()).map(new Function<JsonNode, List<XMessage>>() {
            @Override
            public List<XMessage> apply(JsonNode campaign) {
                String campaignID = campaign.get("id").asText();
                JSONArray users = UserService.getUsersFromFederatedServers(campaignID);
                String formID = getFormID(campaign);
                String formPath = getFormPath(formID);
                JsonNode firstTransformer = campaign.findValues("transformers").get(0).get(0);
                ArrayNode hiddenFields = (ArrayNode) firstTransformer.findValue("hiddenFields");

                for (int i = 122; i < users.length(); i++) {
                    String userPhone = ((JSONObject) users.get(i)).getString("whatsapp_mobile_number");
                    ServiceResponse response = new MenuManager(null, null, null, formPath, formID, false, questionRepo, redisTemplate, userPhone).start();
                    FormInstanceUpdation ss = FormInstanceUpdation.builder().applicationID(campaignID).phone(userPhone).build();
                    //ss.updateAdapterProperties(xMessage.getChannel(), xMessage.getProvider());
                    ss.parse(response.currentResponseState);
                    // String instanceXMlPrevious = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + ss.updateHiddenFields(hiddenFields, (JSONObject) users.get(i)).getXML();
                    String instanceXMlPrevious = ss.updateHiddenFields(hiddenFields, (JSONObject) users.get(i)).getXML();
                    MenuManager mm = new MenuManager(null, null, instanceXMlPrevious, formPath, formID, true, questionRepo, redisTemplate, userPhone);
                    response = mm.start();
                    log.info("Iteration n={}", i);

                    // Create new xMessage from response
                    XMessage x = getMessageFromResponse(xMessage, response);
                    XMessage nextMessage = getClone(x);

                    // Update user info
                    SenderReceiverInfo to = nextMessage.getTo();
                    to.setUserID(userPhone);
                    nextMessage.setTo(to);

                    nextMessage.setMessageState(NOT_SENT);
                    nextMessage.setMessageType(HSM);

                    // Update database with new fields.
                    appendNewResponse(formID, nextMessage, response);
                    replaceUserState(formID, nextMessage, response);
                    nextMessage.setProvider("gupshup");
                    messages.add(nextMessage);
                }
                return messages;
            }
        });
    }

    private XMessage getMessageFromResponse(XMessage xMessage, ServiceResponse response) {
        // Add payload to the response
        XMessagePayload payload = response.getNextMessage();
        xMessage.setPayload(payload);
        return xMessage;
    }


    public static String getFormPath(String formID) {
        FormsDao dao = new FormsDao(JsonDB.getInstance().getDB());
        return dao.getFormsCursorForFormId(formID).getFormFilePath();
    }

    private void appendNewResponse(String formID, XMessage xMessage, ServiceResponse response) {
        GupshupMessageEntity msgEntity = new GupshupMessageEntity();
        msgEntity.setPhoneNo(xMessage.getTo().getUserID());
        msgEntity.setMessage(xMessage.getPayload().getText());
        msgEntity.setLastResponse(response.getCurrentIndex().equals("endOfForm"));
        msgRepo.save(msgEntity).log().subscribe();
    }

    private void replaceUserState(String formID, XMessage xMessage, ServiceResponse response) {
        stateRepo.findByPhoneNoAndBotFormName(xMessage.getTo().getUserID(), formID)
                .subscribe(new Consumer<GupshupStateEntity>() {
                    @Override
                    public void accept(GupshupStateEntity saveEntity) {
                        if (saveEntity == null) {
                            saveEntity = new GupshupStateEntity();
                        }
                        saveEntity.setPhoneNo(xMessage.getTo().getUserID());
                        saveEntity.setPreviousPath(response.getCurrentIndex());
                        saveEntity.setXmlPrevious(response.getCurrentResponseState());
                        saveEntity.setBotFormName(formID);
                        stateRepo.save(saveEntity).log().subscribe();
                    }
                });

    }

    private void logTimeTaken(long startTime, int checkpointID) {
        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1000000;
        log.info(String.format("CP-%d: %d ms", checkpointID, duration));
    }
}