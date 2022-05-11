package com.uci.transformer;

import lombok.*;
import messagerosa.core.model.XMessage;
import reactor.core.publisher.Mono;

import java.util.List;

@NoArgsConstructor
public abstract class TransformerProvider {
    int id;
    String name;

    String description;

    XMessage initialState;
    XMessage finalState;


    public abstract Mono<XMessage> transform(XMessage xMessage) throws Exception;

//    public abstract Mono<List<XMessage>> transformToMany(XMessage xMessage);

}


