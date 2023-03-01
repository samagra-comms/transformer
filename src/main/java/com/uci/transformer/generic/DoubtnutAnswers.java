package com.uci.transformer.generic;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DoubtnutAnswers {
    @JsonAlias({"text"})
    private String text;
    @JsonAlias({"image"})
    private String image;

}
