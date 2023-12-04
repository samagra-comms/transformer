package com.uci.transformer.controllers;

import com.uci.transformer.odk.ODKConsumerReactive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import javax.ws.rs.BadRequestException;

@RestController()
public class XMessageController {

    @Autowired
    private ODKConsumerReactive odkConsumerReactive;

    @PostMapping("/xmsg/processXMessage")
    public Mono<String> processXMessage(@RequestBody String xMessage) {
        if (xMessage == null || xMessage.isEmpty() || xMessage.isBlank()) {
            throw new BadRequestException();
        }
        return odkConsumerReactive.processMessage(xMessage);
    }
}
