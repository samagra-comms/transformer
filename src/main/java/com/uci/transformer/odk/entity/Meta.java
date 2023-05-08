package com.uci.transformer.odk.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.*;
import messagerosa.core.model.ButtonChoice;
import messagerosa.core.model.ContactCard;
import messagerosa.core.model.LocationParams;
import messagerosa.core.model.MessageMedia;

import java.io.Serializable;
import java.util.ArrayList;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Meta implements Serializable {

    private static final ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();

    private String title;
    private ArrayList<String> choices;

    @Override
    public String toString() {
        try {
            return ow.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return "";
    }

}