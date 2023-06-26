package com.uci.transformer.controllers;

import com.uci.dao.models.XMessageDAO;
import com.uci.dao.repository.XMessageRepository;
import lombok.extern.java.Log;
import messagerosa.core.model.XMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Log
@RestController
public class TestController {

    @Autowired
    public XMessageRepository xMessageRepository;
    LocalDateTime yesterday = LocalDateTime.now().minusMonths(1L);

    long totalSuccessCount = 0;

    @GetMapping("/testCass")
    public void getLastMsg(@RequestParam(value = "repeat", required = false) String repeat) throws InterruptedException {
        int count = 10;

        try {
            count = Integer.parseInt(repeat);
        } catch (NumberFormatException ex) {
            ex.printStackTrace();
        }
        for (int i = 0; i < count; i++) {
            int finalI = i;
            getLastSentXMessage("UCI Demo", "9783246247")
                    .doOnError(new Consumer<Throwable>() {
                        @Override
                        public void accept(Throwable throwable) {
                            log.severe("Error in getLastMsg" + throwable.getMessage());
                        }
                    })
                    .subscribe(new Consumer<XMessageDAO>() {
                        @Override
                        public void accept(XMessageDAO xMsgDao) {
                            totalSuccessCount++;
                            log.info("TestController:found xMsgDao : " + xMsgDao.getMessageId() + " count: " + totalSuccessCount);
                        }
                    });
        }

//        for (int i = 0; i < count; i++) {
//
//            int finalI = i;
//            getLatestXMessage("9783246247", yesterday, "SENT").doOnNext(lastMessageID -> {
//                log.info(lastMessageID.getMessageId() + "  Count : " + finalI);
//            }).subscribe();
//            Thread.sleep(1000);
//        }
    }

    private Flux<XMessageDAO> getLastSentXMessage(String appName, String userID) {
        return xMessageRepository.findFirstByAppAndUserIdAndFromIdAndMessageStateOrderByTimestampDesc(appName, userID, "admin", XMessage.MessageState.SENT.name());
    }
}
