package com.uci.transformer.odk.utilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.StaxDriver;
import com.uci.transformer.samagra.MapEntryConverter;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.web.client.RestTemplate;

import java.text.ParseException;
import java.util.*;

@Builder
@Getter
@Setter
@Slf4j
public class FormInstanceUpdation {
    RestTemplate restTemplate;
    String applicationID;
    String phone;
    Map<String, Object> instanceData;

    public FormInstanceUpdation updateAdapterProperties(String channel, String provider){
        updateParams("channel", channel);
        updateParams("provider", provider);
        return this;
    }

    public FormInstanceUpdation updateHiddenFields(ArrayNode hiddenFields, JSONObject user) {
        if(user == null) return this;
        if(hiddenFields == null) return this;
        for(int i=0; i<hiddenFields.size(); i++){
            JsonNode object = hiddenFields.get(i);
            try{
                String userField = JsonPath.parse(user.toString()).read("$." + object.findValue("path").asText(), String.class);
                updateParams(object.findValue("name").asText().replaceAll("__", "_"), userField);
            }catch (PathNotFoundException p){
                log.error("Unable to find key " + object.findValue("path") + " in the object supplied");
            }

        }
        return this;
    }

    public FormInstanceUpdation deleteHiddenFields(ArrayNode hiddenFields) {
        if(hiddenFields == null) return this;
        for(int i=0; i<hiddenFields.size(); i++){
            JsonNode object = hiddenFields.get(i);
            try{
                deleteParams(object.findValue("name").asText().replaceAll("__", "_"));
            }catch (PathNotFoundException p){
                log.error("Unable to find key " + object.findValue("path") + " in the object supplied");
            }
        }
        return this;
    }


    public boolean hashMapper(Map<String, Object> stringObjectMap, String destinationKey, String destinationValue) throws ParseException {
        boolean entryUpdated = false;
        for (Map.Entry<String, Object> entry : stringObjectMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key.equals(destinationKey)) {
                stringObjectMap.put(key, destinationValue);
                return true;
            }else if (value instanceof Map) {
                Map<String, Object> subMap = (Map<String, Object>) value;
                hashMapper(subMap, destinationKey, destinationValue);
            }
        }

        return entryUpdated;
    }

    public void updateParams(String key, String value) {
        try {
           boolean result =  hashMapper(this.instanceData,key,value);
           if(!result) log.error("Could not find key "+ key);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    public void deleteParams(String key) {
        try {
            boolean result =  hashMapper(this.instanceData,key,"");
            if(!result) log.error("Could not find key "+ key);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    public String getXML(){
    	XStream magicApi = new XStream(new StaxDriver()) {
            @Override
            protected void setupConverters() {
            }
        };
        magicApi.allowTypesByWildcard(new String[] {
            "com.your.package.**"
        });
    	magicApi.registerConverter(new MapEntryConverter());
        magicApi.alias("data", Map.class);
        return magicApi.toXML(this.instanceData)
                .replaceAll("__", "_");
    }

    public Map<String, Object> parse(String xml) {
    	System.out.println(xml);
//        XStream magicApi = new XStream();
    	XStream magicApi = new XStream(new StaxDriver()) {
            @Override
            protected void setupConverters() {
            }
        };
        magicApi.allowTypesByWildcard(new String[] {
            "com.your.package.**"
        });
        magicApi.registerConverter(new MapEntryConverter());
        magicApi.alias("data", Map.class);
        this.instanceData = (Map<String, Object>) magicApi.fromXML(xml);
        return this.instanceData;
    }
}