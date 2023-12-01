package com.uci.transformer.controllers;

import com.uci.transformer.odk.ODKConsumerReactive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.BadRequestException;

@RestController()
public class XMessageController {

    @Autowired
    private ODKConsumerReactive odkConsumerReactive;

    @PostMapping("/xmsg/processXMessage")
    public void processXMessage(@RequestBody String xMessage) {
        if (xMessage == null || xMessage.isEmpty() || xMessage.isBlank()) {
            throw new BadRequestException();
        }
        odkConsumerReactive.processMessage(xMessage);
    }
}
