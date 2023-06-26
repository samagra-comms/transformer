package com.uci.transformer.generic;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.*;
import messagerosa.core.model.ButtonChoice;

import java.util.ArrayList;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class DoubtnutAnswers {
    @JsonAlias({"text"})
    private String text;
    @JsonAlias({"image"})
    private String image;

    @JsonAlias({"choices"})
    private ArrayList<ButtonChoice> buttonChoices;

}
