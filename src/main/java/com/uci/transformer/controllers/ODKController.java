package com.uci.transformer.controllers;

import com.uci.transformer.odk.FormDownloader;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class ODKController {

    @GetMapping(path = "/odk/updateAll")
    public void updateAllODKForms() {
        log.info("Updating forms....");
        new FormDownloader().downloadFormsDelta();
    }
}
