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
public class GenericMessageResponse {

    @Getter
    @Setter
    public static class Meta {

        @JsonAlias({"code"})
        private String code;
        @JsonAlias({"success"})
        private String success;
        @JsonAlias({"message"})
        private String message;
    }

    private Meta meta;

    @Getter
    @Setter
    public static class Data {
        @JsonAlias({"answers"})
        private DoubtnutAnswers[] answers;
    }

    private  Data data;

    private String code;
    private String message;
}
