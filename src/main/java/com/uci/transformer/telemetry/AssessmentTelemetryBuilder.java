package com.uci.transformer.telemetry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uci.transformer.odk.entity.Assessment;
import com.uci.transformer.odk.entity.Question;
import com.uci.transformer.odk.entity.Question.QuestionType;
import com.uci.utils.telemetry.dto.*;
import com.uci.utils.telemetry.util.TelemetryEvents;

import io.r2dbc.postgresql.codec.Json;
import messagerosa.core.model.ButtonChoice;
import messagerosa.core.model.XMessagePayload;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AssessmentTelemetryBuilder {

	private static final String TELEMETRY_IMPL_VERSION = "3.0";
	private static final String QUESTION_TELEMETRY_IMPL_VERSION = "1.0";
	private static final String ACTOR_TYPE_USER = "User";
	private static final String DIKSHA_ORG = "DIKSHA";
	private static final String ASSESS_EVENT_MID = "fb3db9abceb578d8acbc812cdbd9c931";
	
	private static final String QUESTION_TYPE_MCQ = "mcq";
	private static final String QUESTION_TYPE_FTP = "ftp";
	private static final String ASSESS_EVENT_MID_PREFIX = "ASSESS:";
	// represents the MD5 Hash of "Individual Question-Responses -
	// Survey/Questionnaire"

	public String build(String botOrg, String channel, String provider, String producerID, String conversationOwnerID,
			Question question, Assessment assessment, XMessagePayload questionPayload, long duration, String encyptedDeviceId,
			String msgid, Boolean formEnd, UUID sessionId) {
//		ArrayList<ButtonChoice> buttonChoices = getQuestionChoices(questionPayload.getButtonChoices());
		ArrayList<ButtonChoice> buttonChoices = questionPayload.getButtonChoices();
		String questionType = getQuestionType(buttonChoices);
		
		String xPath = question != null && question.getXPath() != null ? question.getXPath() : "";
		
		//Context Cdata
		List<Map<String, Object>> cdata = new ArrayList<>();
		cdata.add(setCdataItemMap("ConversationOwner", conversationOwnerID));
		cdata.add(setCdataItemMap("Conversation", assessment.getBotID().toString()));
		cdata.add(setCdataItemMap("XPath", xPath));
		cdata.add(setCdataItemMap("FormEnd", formEnd.toString()));
		cdata.add(setCdataItemMap("SessionId", sessionId.toString()));
		
		
		//Context Rollup
		Map<String, String> rollup = new HashMap<>();
		rollup.put("l1", conversationOwnerID.toString()); //ConversationOwner value
		rollup.put("l2", assessment.getBotID().toString()); //Conversation value
		rollup.put("l3", xPath);
		rollup.put("l4", formEnd.toString());
		rollup.put("l5", sessionId.toString());
		
		String channelName = (botOrg.equalsIgnoreCase("Anonymous")) || botOrg.isEmpty() ? DIKSHA_ORG : botOrg;
		String userID = "";
		try {
			userID = assessment.getUserID().toString();
		} catch (Exception e) {
		}
		
		//Context
		Context context = Context.builder()
								.channel(channelName)
								.env(channel + "." + provider)
								.pdata(Producer.builder()
												.id(getPdataId())
												.pid(producerID)
												.ver(QUESTION_TELEMETRY_IMPL_VERSION)
												.build())
								.did(userID)
								.sid("")
								.cdata(cdata)
								.rollup(rollup)
								.build();

		//Object rollup
		Map<String, String> questionRollup = new HashMap<>();
		questionRollup.put("l1", "BotOwnerID");
		questionRollup.put("l2", "BotID");
		questionRollup.put("l3", "QuestionXPath");
		questionRollup.put("l4", "FormEnd");
		questionRollup.put("l5", "sessionId");
		
		//Object
		Target object = Target.builder().id(question.getId().toString()).type(question.getQuestionType().name())
				.ver(QUESTION_TELEMETRY_IMPL_VERSION).rollup(questionRollup).build();
		
		//Item
		Map<String, Object> itemDetails = new HashMap<>();
		itemDetails.put("id", (question.getId() != null ? question.getId().toString() : ""));
		itemDetails.put("type", questionType);
		itemDetails.put("mmc", new ArrayList());
		itemDetails.put("mc", new ArrayList());
		itemDetails.put("exlength", 0.0);
		itemDetails.put("maxscore", 1.0);
		itemDetails.put("title", questionPayload.getText());
		itemDetails.put("uri", "");
		itemDetails.put("desc", "");
		itemDetails.put("params", getItemParams(questionType, buttonChoices));

		//Edata
		Map<String, Object> edata = new HashMap<>();
		edata.put("duration", duration);
		edata.put("item", itemDetails);
		edata.put("resvalues", getEdataResValues(buttonChoices, assessment.getAnswer()));
		edata.put("score", 1.0);
		edata.put("pass", "Yes");
		edata.put("index", 1.0);
		
		/* Current Date Time */
        LocalDateTime localNow = LocalDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String timestamp = fmt.format(localNow).toString();

		String mid = ASSESS_EVENT_MID_PREFIX+UUID.nameUUIDFromBytes(timestamp.getBytes()).toString().replace("-", "");
		

		//Telemetry
		Telemetry telemetry = Telemetry.builder()
										.eid(TelemetryEvents.ASSESS.getName())
										.ets(System.currentTimeMillis())
										.ver(TELEMETRY_IMPL_VERSION)
										.mid(mid)
										.actor(Actor.builder()
													.type(ACTOR_TYPE_USER)
													.id(userID)
													.build())
										.context(context)
										.object(object)
										.edata(edata)
										.type("event")
										.tags(new ArrayList())
										.flags(new ArrayList())
										.timestamp(timestamp)
										.build();
		
		return Telemetry.getTelemetryRequestData(telemetry);
	}
	
	/**
	 * Get pData id from env else default value
	 * @return String
	 */
	private String getPdataId() {
		if(System.getenv("TELEMETRY_EVENT_PDATA_ID") != null 
				&& !System.getenv("TELEMETRY_EVENT_PDATA_ID").isEmpty()) {
			return System.getenv("TELEMETRY_EVENT_PDATA_ID");
		}
		return "prod.uci.diksha";
	}
	
	/**
	 * Get question type(mcq/string) based on choices present or not
	 * @param questionChoices
	 * @return String
	 */
	private String getQuestionType(ArrayList<ButtonChoice> questionChoices) {
		String questionType;
		if(questionChoices != null && questionChoices.size() > 0) {
			questionType = QUESTION_TYPE_MCQ;
		} else {
			questionType = QUESTION_TYPE_FTP;
		}
		return questionType;
	}
	
	/**
	 * Get edata->item->params(aka: question choices) if exists for telemetry event
	 * @param questionType
	 * @param questionChoices
	 * @return List
	 */
	private List getItemParams(String questionType, ArrayList<ButtonChoice> questionChoices) {
		List params = new ArrayList();
		if(questionChoices != null) {
			for(ButtonChoice questionChoice : questionChoices) {
				Map<String, Object> param = new HashMap();
				Map<String, Object> text = new HashMap();
				text.put("text", questionChoice.getText());
				param.put(questionChoice.getKey(), text);
				params.add(param);
			}
		}
		return params;
	}
	
	/**
	 * Set cdata item map 
	 * @param type
	 * @param id
	 * @return
	 */
	private Map<String, Object> setCdataItemMap(String type, Object id) {
		Map<String, Object> map = new HashMap<>();
		map.put("type", type);
		map.put("id", id);
		return map;
	}
	
	/* Not in use */
	/**
	 * Get Question Choices with correct key
	 * @param questionChoices
	 * @return
	 */
//	private ArrayList<ButtonChoice> getQuestionChoices(ArrayList<ButtonChoice> questionChoices) {
//		questionChoices.forEach(choice -> {
//			String[] a = choice.getText().split(" ");
//			try {
//				if(a[0] != null && !a[0].isEmpty()) {
//					Integer.parseInt(a[0]);
//			        choice.setKey(a[0].toString());
//	    		}
//			} catch (NumberFormatException ex) {
//				String[] b = choice.getText().split(".");
//	    		try {
//	    			if(b[0] != null && !b[0].isEmpty()) {
//		    		    Integer.parseInt(b[0]);
//		    		    choice.setKey(b[0].toString());
//	    			}
//	    		} catch (NumberFormatException exc) {
//	    			// do nothing
//	    		} catch (ArrayIndexOutOfBoundsException exc) {
//	    		    // do nothing
//	    		}
//			} catch (ArrayIndexOutOfBoundsException ex) {
//				// do nothing
//			}
//			
//		});
//		return questionChoices;
//	}
//	
	/**
	 * Get edata res values for mcq/string type questions
	 * @param questionChoices
	 * @param answer
	 * @return
	 */
	private List getEdataResValues(ArrayList<ButtonChoice> questionChoices, String answer) {
		List values = new ArrayList();
		if(questionChoices != null && questionChoices.size() > 0) {
			for(ButtonChoice questionChoice : questionChoices) {
				if(questionChoice.getKey().equals(answer)) {
					Map<String, String> map = new HashMap();
					map.put("text", questionChoice.getText());
					Map<String, Object> map2 = new HashMap();
					map2.put(questionChoice.getKey(), map);
					values.add(map2);
					break;
				}
			}
			if(values.isEmpty()) {
				Map<String, String> value1 = new HashMap<>();
				value1.put("ans1", answer);
				values.add(value1);
			}
		} else {
			Map<String, String> value1 = new HashMap<>();
			value1.put("ans1", answer);
			values.add(value1);
		}
		return values;
	}
}
