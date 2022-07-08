package com.uci.transformer.odk;

import java.util.ArrayList;
import java.util.Map;

import org.javarosa.core.model.FormIndex;

import com.uci.transformer.odk.entity.Question;
import messagerosa.core.model.XMessagePayload;

public class ServiceResponse {
    String currentIndex;
    XMessagePayload nextMessage; // Next question message
    String currentResponseState;
    boolean shouldSwitchToNextForm;
    String formVersion;
    String formID;
    Question question;
    ArrayList<Integer> conversationLevel;

    public ServiceResponse(String currentIndex, XMessagePayload nextMessage, String currentResponseState, String formVersion) {
        this.currentIndex = currentIndex;
        this.nextMessage = nextMessage;
        this.currentResponseState = currentResponseState;
        this.formVersion = formVersion;
    }
    
    public ServiceResponse(String currentIndex, XMessagePayload nextMessage, String currentResponseState, String formVersion, String formID, Question question, ArrayList<Integer> conversationLevel) {
        this.currentIndex = currentIndex;
        this.nextMessage = nextMessage;
        this.currentResponseState = currentResponseState;
        this.formVersion = formVersion;
        this.formID = formID;
        this.question = question;
        this.conversationLevel = conversationLevel;
    }

    public String getCurrentIndex() {
        return currentIndex;
    }

    public XMessagePayload getNextMessage() {
        return nextMessage;
    }

    public String getCurrentResponseState() {
        return currentResponseState;
    }
    
    public ArrayList<Integer> getConversationLevel() {
        return conversationLevel;
    }
}
