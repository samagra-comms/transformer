package com.uci.transformer.generic;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import messagerosa.core.model.ButtonChoice;

import java.util.ArrayList;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DoubtnutAnswers {
    @JsonAlias({"text"})
    private String text;
    @JsonAlias({"image"})
    private String image;

    @JsonAlias({"choices"})
    private ArrayList<ButtonChoice> buttonChoices;

}
