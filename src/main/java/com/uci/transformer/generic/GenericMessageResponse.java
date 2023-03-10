package com.uci.transformer.generic;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class GenericMessageResponse {

    @Getter
    @Setter
    @ToString
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
    @ToString
    public static class Data {
        @JsonAlias({"answers"})
        private DoubtnutAnswers[] answers;
    }

    private  Data data;

    private String code;
    private String message;
}
