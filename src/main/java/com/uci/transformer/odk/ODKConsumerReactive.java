package com.uci.transformer.odk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Maps;
import com.uci.transformer.TransformerProvider;
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
import com.uci.transformer.telemetry.AssessmentTelemetryBuilder;
import com.uci.utils.CampaignService;
import com.uci.utils.kafka.SimpleProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import messagerosa.core.model.ButtonChoice;
import messagerosa.core.model.LocationParams;
import messagerosa.core.model.SenderReceiverInfo;
import messagerosa.core.model.Transformer;
import messagerosa.core.model.XMessage;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    
    @Autowired
    UserService userService;

    @Value("${producer.id}")
    private String producerID;
    
    @Value("${assesment.character.go_to_start}")
    public String assesGoToStartChar;
    
    public MenuManager menuManager;
    
    public Boolean isStartingMessage;

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
                            if (msg.getMessageType() == XMessage.MessageType.BROADCAST_TEXT) {
                                transformToMany(msg).subscribe(new Consumer<List<XMessage>>() {
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
                                transform(msg)
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
                        } catch (JAXBException e) {
                            e.printStackTrace();
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
    public Mono<List<XMessage>> transformToMany(XMessage xMessage) {

        ArrayList<XMessage> messages = new ArrayList<>();

        // Get All Users with Data.
        return campaignService.getCampaignFromNameTransformer(xMessage.getCampaign()).map(new Function<JsonNode, List<XMessage>>() {
            @Override
            public List<XMessage> apply(JsonNode campaign) {
                String campaignID = campaign.get("id").asText();
                JSONArray users = userService.getUsersFromFederatedServers(campaignID);
                String formID = getFormID(campaign);
                String formPath = getFormPath(formID);
                JsonNode firstTransformer = campaign.findValues("transformers").get(0).get(0);
                ArrayNode hiddenFields = (ArrayNode) firstTransformer.findValue("hiddenFields");

                for (int i = 34; i < users.length(); i++) {
                    String userPhone = ((JSONObject) users.get(i)).getString("whatsapp_mobile_number");
                    ServiceResponse response = new MenuManager(null, null, null, formPath, formID, false, questionRepo, null).start();
                    FormUpdation ss = FormUpdation.builder().applicationID(campaignID).phone(userPhone).build();
                    ss.updateAdapterProperties(xMessage.getChannel(), xMessage.getProvider());
                    ss.parse(response.currentResponseState);
                    String instanceXMlPrevious = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + ss.updateHiddenFields(hiddenFields, (JSONObject) users.get(i)).getXML();
                    MenuManager mm = new MenuManager(null, null, instanceXMlPrevious, formPath, formID, true, questionRepo, null);
                    response = mm.start();

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
                    messages.add(nextMessage);
                }
                return messages;
            }
        });

    }
    
    private Map<String, String> getCampaignAndFormIdFromXMessage(XMessage xMessage) {
    	Map<String, String> result = new HashMap<String, String>();
    	String campaignID = "";
    	String formID = "";
    	if(xMessage.getTransformers() != null && xMessage.getTransformers().size() > 0) {
    		Transformer t = xMessage.getTransformers().get(0);
    		if(!t.getMetaData().isEmpty()) {
    			Map<String, String> metaData = (Map<String, String>) t.getMetaData();
        		formID = (String) metaData.get("currentFormID");
        		campaignID = (String) metaData.get("campaignID");
    		}
    	}
    	
    	result.put("formID", formID);
    	result.put("campaignID", campaignID);
    	
    	return result;
    }
    
    @Override
    public Mono<XMessage> transform(XMessage xMessage) throws Exception {
    	XMessage[] finalXMsg = new XMessage[1];
        return campaignService
                .getCampaignFromNameTransformer(xMessage.getApp())
                .map(new Function<JsonNode, Mono<Mono<Mono<XMessage>>>>() {
                    @Override
                    public Mono<Mono<Mono<XMessage>>> apply(JsonNode campaign) {
                        if (campaign != null) {
                        	log.info("1 To User ID:"+xMessage.getTo().getUserID());
//                        	Map<String, String> data = getCampaignAndFormIdFromXMessage(xMessage);
//                        	
//                            String formID = data.get("formID");
                        	String formID = ODKConsumerReactive.this.getFormID(campaign);
                            
                            if (formID.equals("")) {
                                log.error("Unable to find form ID from Conversation Logic");
                                return null;
                            }
                            
//                            String lastFormID = getCurrentFormIDFromFile(xMessage.getFrom().getUserID(), data.get("campaignID"));
//                            log.info("Previous FormID:"+lastFormID);
//                            
//                            saveCurrentFormIDInFile(xMessage.getFrom().getUserID(), data.get("campaignID"), formID);
                            

                            log.info("current form ID:"+formID);
                            String formPath = getFormPath(formID);
                            log.info("current form path:"+formPath);
                            
                            isStartingMessage = xMessage.getPayload().getText().equals(campaign.findValue("startingMessage").asText());
                            
                            Boolean addOtherOptions = xMessage.getProvider().equals("sunbird") ? true : false;

                            // Get details of user from database
                            return getPreviousMetadata(xMessage, formID)
                                    .map(new Function<FormManagerParams, Mono<Mono<XMessage>>>() {
                                        @Override
                                        public Mono<Mono<XMessage>> apply(FormManagerParams previousMeta) {
                                            final ServiceResponse[] response = new ServiceResponse[1];
                                            MenuManager mm;
                                            String instanceXMlPrevious;
                                            Boolean prefilled;
                                            String answer;
                                            if (previousMeta.instanceXMlPrevious == null || previousMeta.currentAnswer.equals(assesGoToStartChar) || isStartingMessage) {
//                                            if (!lastFormID.equals(formID) || previousMeta.instanceXMlPrevious == null || previousMeta.currentAnswer.equals(assesGoToStartChar) || isStartingMessage) {
                                            	previousMeta.currentAnswer = assesGoToStartChar;
                                                ServiceResponse serviceResponse = new MenuManager(null, null, null, formPath, formID, false, questionRepo, null).start();
                                                FormUpdation ss = FormUpdation.builder().build();
                                                ss.parse(serviceResponse.currentResponseState);
                                                ss.updateAdapterProperties(xMessage.getChannel(), xMessage.getProvider());
//                                                String instanceXMlPrevious = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
//                                                        ss.getXML();
                                                prefilled = true;
                                                instanceXMlPrevious = ss.getXML();
                                                answer = null;
                                                log.debug("Instance value >> " + instanceXMlPrevious);
                                                mm = new MenuManager(null, answer, instanceXMlPrevious, formPath, formID, prefilled, questionRepo, xMessage.getPayload());
                                                response[0] = mm.start();
                                            } else {
                                            	prefilled = false;
                                            	answer = previousMeta.currentAnswer;
                                            	instanceXMlPrevious = previousMeta.instanceXMlPrevious;
                                                mm = new MenuManager(previousMeta.previousPath, answer,
                                                		instanceXMlPrevious, formPath, formID, prefilled, questionRepo, xMessage.getPayload());
                                                response[0] = mm.start();
                                            }
                                            
                                            /* To use with previous question & question payload methods */
                                            menuManager = mm;
                                            
                                            /* Previous Question Data */
                                            Question prevQuestion = null;
                                            if(!isStartingMessage) {
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
                                                            campaign,
                                                            xMessage,
                                                            response[0].question,
                                                            prevQuestion,
                                                            response[0].currentIndex
                                                    );


                                            /* If form contains eof__, then process next bot by id addded with eof__bot_id, else process message */
                                            if (response[0].currentIndex.contains("eof__")) {    
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
                                                                false, questionRepo, null)
                                                                .start();
                                                        FormUpdation ss = FormUpdation.builder().build();
                                                        ss.parse(serviceResponse.currentResponseState);
                                                        ss.updateAdapterProperties(xMessage.getChannel(), xMessage.getProvider());
//                                                        String instanceXMlPrevious = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
//                                                                ss.getXML();
                                                        String instanceXMlPrevious = ss.getXML();
                                                        log.debug("Instance value >> " + instanceXMlPrevious);
                                                        MenuManager mm2 = new MenuManager(null, null,
                                                                instanceXMlPrevious, getFormPath(nextFormID), nextFormID, true,
                                                                questionRepo, null);
                                                        ServiceResponse response = mm2.start();
                                                        xMessage.setApp(nextAppName);
                                                        return decodeXMessage(xMessage, response, nextFormID, updateQuestionAndAssessment);
                                                    }
                                                });
                                            } else {
                                                return Mono.just(decodeXMessage(xMessage, response[0], formID, updateQuestionAndAssessment));
                                            }
                                        }
                                    });
                        } else {
                            log.error("Could not find Bot");
                            return Mono.just(null);
                        }
                    }
                })
                .flatMap(new Function<Mono<Mono<Mono<XMessage>>>, Mono<XMessage>>() {
                    @Override
                    public Mono<XMessage> apply(Mono<Mono<Mono<XMessage>>> m) {
                        log.info("Level 1");
                        return m.flatMap(new Function<Mono<Mono<XMessage>>, Mono<? extends XMessage>>() {
                            @Override
                            public Mono<? extends XMessage> apply(Mono<Mono<XMessage>> n) {
                                log.info("Level 2");
                                return n.flatMap(new Function<Mono<XMessage>, Mono<? extends XMessage>>() {
                                    @Override
                                    public Mono<? extends XMessage> apply(Mono<XMessage> o) {
                                        return o;
                                    }
                                });
                            }
                        });
                    }
                });
    }

    /**
     * Check if form has ended by xpath
     * @param xPath
     * @return
     */
    private Boolean isEndOfForm(String xPath) {
        log.info("xPath for isEndOfForm check: "+xPath);
        return xPath.contains("endOfForm") || xPath.contains("eof");
    }
    
    /**
     * Get current form id set in file for user & campaign 
     * 
     * @param userID
     * @param campaignID
     * @return
     */
    private String getCurrentFormIDFromFile(String userID, String campaignID) {
    	String currentFormID = "";
    	try {
    		File file = getCurrentUserJsonFile();
        	InputStream inputStream = new FileInputStream(file);
        	byte[] bdata = FileCopyUtils.copyToByteArray(inputStream);
            
        	ObjectMapper mapper = new ObjectMapper();
        	JsonNode rootNode = mapper.readTree(bdata);
            log.info("UserCurrentForm file data node:"+rootNode);
            
            if(!rootNode.isEmpty() && rootNode.get(userID) != null 
            		&& rootNode.path(userID).get(campaignID) != null) {
            	currentFormID = rootNode.path(userID).get(campaignID).asText();
            }
        } catch (IOException e) {
        	log.error("Error in getCurrentFormIDFromFile:"+e.getMessage());
        }
        return currentFormID;
    }
    
    /**
     * Save current form id in file for user & campaign
     * 
     * @param userID
     * @param campaignID
     * @param currentFormID
     */
    private void saveCurrentFormIDInFile(String userID, String campaignID, String currentFormID) {
    	try {
    		File file = getCurrentUserJsonFile();
        	InputStream inputStream = new FileInputStream(file);
        	byte[] bdata = FileCopyUtils.copyToByteArray(inputStream);
            
        	ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(bdata);
        	
            if(rootNode.isEmpty()) {
            	rootNode = mapper.createObjectNode();
            }
            
            if(rootNode != null && !rootNode.isEmpty() && rootNode.get(userID) != null) {
            	((ObjectNode) rootNode.path(userID)).put(campaignID, currentFormID);
        	} else {
            	JsonNode campaignNode = mapper.createObjectNode();
            	((ObjectNode) campaignNode).put(campaignID, currentFormID);
            	
            	((ObjectNode) rootNode).put(userID, campaignNode);
            }
              
            log.info("Data saved in userCurrentForm file:"+rootNode.toString());
            
            FileWriter fileWriter = new FileWriter(file);
            fileWriter.write(rootNode.toString());
            fileWriter.close();
        } catch (IOException e) {
        	log.error("Error in saveCurrentFormIDInFile:"+e.getMessage());
        }
    }
	
    /**
	 * Get Current User Json File to get, if not exists create one
	 * 
	 * @return File
	 */
	private File getCurrentUserJsonFile() {
		try {
			File file = new File(getCurrentUserJsonFilePath());
	    	if(!file.exists()) {
	    		file.createNewFile();
	    	}
	    	return file;
		} catch (IOException e) {
			log.error("Error in getCurrentUserJsonFile:"+e.getMessage());
		}
		return null;
	}
    
	/**
	 * Get Path to userCurrentForm file 
	 * 
	 * @return String
	 */
    private String getCurrentUserJsonFilePath() {
    	return "src/main/resources/userCurrentForm.json";
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
                            	if(message.getPayload().getMedia() != null) {
                                	formManagerParams.setCurrentAnswer(message.getPayload().getMedia().getUrl());
                                } else if(message.getPayload().getLocation() != null) {
                                	formManagerParams.setCurrentAnswer(getLocationContentText(message.getPayload().getLocation()));
                                } else  {
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
     * @param location
     * @return
     */
    private String getLocationContentText(LocationParams location) {
    	String text = "";
    	text = location.getLatitude()+" "+location.getLongitude();
    	if(location.getAddress() != null && !location.getAddress().isEmpty()) {
    		text += " "+location.getAddress();
    	}
    	if(location.getName() != null && !location.getName().isEmpty()) {
    		text += " "+location.getName();
    	}
    	if(location.getUrl() != null && !location.getUrl().isEmpty()) {
    		text += " "+location.getUrl();
    	}
    	return text.trim();
    }
    
    @NotNull
    private Mono<Pair<Boolean, List<Question>>> updateQuestionAndAssessment(FormManagerParams previousMeta,
                                                                            Mono<Pair<Boolean, List<Question>>> previousQuestions, String formID,
                                                                            JsonNode campaign, XMessage xMessage, Question question, Question prevQuestion,
                                                                            String currentXPath) {
        return previousQuestions
                .doOnNext(new Consumer<Pair<Boolean, List<Question>>>() {
                    @Override
                    public void accept(Pair<Boolean, List<Question>> existingQuestionStatus) {
                        if (existingQuestionStatus.getLeft()) {
                        	log.info("Found Question id: "+existingQuestionStatus.getRight().get(0).getId()+", xPath: "+existingQuestionStatus.getRight().get(0).getXPath());
                        	saveAssessmentData(
                                    existingQuestionStatus, formID, previousMeta, campaign, xMessage, null, currentXPath).subscribe(new Consumer<Assessment>() {
                                @Override
                                public void accept(Assessment assessment) {
                                    log.info("Assessment Saved Successfully {}", assessment.getId());
                                }
                            });
                        } else {
                        	Question saveQuestion;
                        	if(prevQuestion == null) {
                        		saveQuestion = question;
                        	} else {
                        		saveQuestion = prevQuestion;
                        	}
                            saveQuestion(saveQuestion).subscribe(new Consumer<Question>() {
                                @Override
                                public void accept(Question question) {
                                	log.info("Question Saved Successfully, id: "+question.getId()+", xPath: "+question.getXPath());
                                	saveAssessmentData(
                                            existingQuestionStatus, formID, previousMeta, campaign, xMessage, question, currentXPath).subscribe(new Consumer<Assessment>() {
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
                                                JsonNode campaign, XMessage xMessage, Question question,
                                                String currentXPath) {
        if (question == null) question = existingQuestionStatus.getRight().get(0);
        
        UUID userID = !xMessage.getTo().getDeviceID().isEmpty() && xMessage.getTo().getDeviceID() != null && xMessage.getTo().getDeviceID() != "" ? UUID.fromString(xMessage.getTo().getDeviceID()) : null;
        log.info("User uuid:"+userID);      

        Assessment assessment = Assessment.builder()
                .question(question)
                .deviceID(userID)
                .answer(previousMeta.currentAnswer)
                .botID(UUID.fromString(campaign.findValue("id").asText()))
                .userID(userID)
                .build();
        try {
        	if(question != null) {
        		log.info("In saveAssessmentData, question id: "+question.getId()+", question xpath: "+question.getXPath());
        	}else {
            	log.info("In saveAssessmentData, Question empty: "+question);
            }
        	
        	if(question != null && !isStartingMessage) {
        		
        		XMessagePayload questionPayload = menuManager.getQuestionPayloadFromXPath(question.getXPath());
        		String telemetryEvent = new AssessmentTelemetryBuilder()
                        .build(campaign.findValue("ownerOrgID").asText(),
                                xMessage.getChannel(),
                                xMessage.getProvider(),
                                producerID,
                                campaign.findValue("ownerOrgID").asText(),
                                assessment.getQuestion(),
                                assessment,
                                questionPayload,
                                0,
                                xMessage.getTo().getEncryptedDeviceID(),
                                xMessage.getMessageId().getChannelMessageId(),
                                isEndOfForm(currentXPath));
                System.out.println(telemetryEvent);
                kafkaProducer.send(telemetryTopic, telemetryEvent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        log.info("question xpath:"+question.getXPath()+",answer: "+assessment.getAnswer());
        
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
                        log.info("Assessment Saved by id: "+assessment.getId());
                    }
                });
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

    private XMessage getMessageFromResponse(XMessage xMessage, ServiceResponse response) {
        XMessagePayload payload = response.getNextMessage();
        xMessage.setPayload(payload);
        xMessage.setConversationLevel(response.getConversationLevel());
        return xMessage;
    }

    public static String getFormPath(String formID) {
    	FormsDao dao = new FormsDao(JsonDB.getInstance().getDB());
        return dao.getFormsCursorForFormId(formID).getFormFilePath();
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
}