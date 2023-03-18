package com.uci.transformer.generic;

import com.uci.dao.models.XMessageDAO;
import com.uci.dao.repository.XMessageRepository;
import com.uci.utils.kafka.SimpleProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import messagerosa.core.model.*;
import messagerosa.xml.XMessageParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.cassandra.core.query.CassandraPageRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.xml.bind.JAXBException;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
@Slf4j
public class GenericTransformerConsumer {
    @Autowired
    private SimpleProducer kafkaProducer;

    @Value("${generic-transformer}")
    private String genericTransformer;

    @Value("${processOutbound}")
    private String processOutbound;

    @Value("${doubtnut.baseurl}")
    private String url;
    @Value("${doubtnut.welcome.msg}")
    private String welcomeMessage;
    @Value("${doubtnut.welcome.video}")
    private String videoUrl;
    @Value("${assesment.character.go_to_start}")
    private String assesmentGotostart;

    @Value(("${doubtnut.apikey}"))
    private String doubtnutApikey;

    @Autowired
    private XMessageRepository xMsgRepo;


    @KafkaListener(id = "${generic-transformer}", topics = "${generic-transformer}", properties = {"spring.json.value.default.type=java.lang.String"})
    public void onMessage(@Payload String stringMessage) {
        try {
            log.info("Topic generic transformer : " + stringMessage);
            final long startTime = System.nanoTime();
            logTimeTaken(startTime, 0);
            XMessage msg = XMessageParser.parse(new ByteArrayInputStream(stringMessage.getBytes()));
            GenericOutboundMessage genericOutboundMessage = new GenericOutboundMessage();

            // Checking Starting Message
            if (msg.getTransformers().get(0).getMetaData().get("startingMessage").toString().equals(msg.getPayload().getText())) {
                XMessagePayload payload = msg.getPayload();
                sendWelcomeMessage(payload, msg);
            }
            // Exit Chat or Goto Start
            else if (msg.getPayload().getText().equals(assesmentGotostart)) {
                XMessagePayload payload = msg.getPayload();
                MessageMedia messageMedia = null;
                if (payload.getMedia() == null) {
                    messageMedia = new MessageMedia();
                } else {
                    messageMedia = payload.getMedia();
                }
                payload.setText(welcomeMessage);
                if (videoUrl != null && !videoUrl.isEmpty()) {
                    messageMedia.setCategory(MediaCategory.VIDEO);
                    messageMedia.setUrl(videoUrl);
                    messageMedia.setText(welcomeMessage);
                    payload.setMedia(messageMedia);
                }
                msg.setSessionId(UUID.randomUUID());
                msg.setPayload(payload);
                kafkaProducer.send(processOutbound, msg.toXML());
            } else {

                if (msg.getPayload() != null && msg.getPayload().getMedia() != null && (msg.getPayload().getMedia().getCategory().equals(MediaCategory.IMAGE)
                        || msg.getPayload().getMedia().getCategory().equals(MediaCategory.AUDIO))) {
                    String msgType = null;
                    if (msg.getPayload().getMedia().getCategory().equals(MediaCategory.IMAGE)) {
                        msgType = "IMAGE";
                    } else if (msg.getPayload().getMedia().getCategory().equals(MediaCategory.AUDIO)) {
                        msgType = "AUDIO";
                    }
                    genericOutboundMessage.setMessage(msg.getPayload().getMedia().getUrl());
                    sendDoubtnutAPI(genericOutboundMessage, msg, msgType);
                } else {
                    log.info("received msg : " + msg.getPayload().getText());
                    if (msg.getPayload().getText().equalsIgnoreCase("yes")) {
                        getLatestXMessage(msg.getTo().getUserID(), XMessage.MessageState.SENT).map(new Function<XMessageDAO, String>() {
                            @Override
                            public String apply(XMessageDAO xMessageDAO) {
                                try {
                                    XMessage msg = XMessageParser.parse(new ByteArrayInputStream(xMessageDAO.getXMessage().getBytes()));
                                    String answer = "";
                                    if (msg.getPayload() != null
                                            && msg.getPayload().getText() != null
                                            && !msg.getPayload().getText().isEmpty()
                                            && msg.getPayload().getMedia() == null) {
                                        String msgType = "TEXT";
                                        answer = msg.getPayload().getText();
                                        if (answer.contains("\"")) {
                                            answer = answer.substring(answer.indexOf("\"") + 1, answer.lastIndexOf("\""));
                                        }
                                        log.info("trimed answer :" + answer);
                                        XMessagePayload payload = msg.getPayload();
                                        payload.setText(answer);
                                        msg.setPayload(payload);
                                        msg.setMessageState(XMessage.MessageState.REPLIED);
                                        genericOutboundMessage.setMessage(msg.getPayload().getText());
                                        sendDoubtnutAPI(genericOutboundMessage, msg, msgType);
                                    } else {
                                        log.info("old message not received : " + msg.toXML());
                                    }
                                    return answer;
                                } catch (JAXBException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }).subscribe();
                    } else if (msg.getPayload().getText().equalsIgnoreCase("no")) {
                        getLatestXMessage(msg.getTo().getUserID(), XMessage.MessageState.SENT).map(new Function<XMessageDAO, String>() {
                            @Override
                            public String apply(XMessageDAO xMessageDAO) {
                                try {
                                    XMessage msg = XMessageParser.parse(new ByteArrayInputStream(xMessageDAO.getXMessage().getBytes()));
                                    String answer = "";
                                    if (msg.getPayload() != null
                                            && msg.getPayload().getText() != null
                                            && !msg.getPayload().getText().isEmpty()
                                            && msg.getPayload().getMedia() == null) {

                                        if (msg.getPayload().getButtonChoices() != null) {
                                            msg.getPayload().setButtonChoices(null);
                                            msg.getPayload().setStylingTag(null);
                                            XMessagePayload payload = msg.getPayload();
                                            msg.setMessageState(XMessage.MessageState.REPLIED);
                                            sendWelcomeMessage(payload, msg);
                                        } else {
                                            log.info("invalid input!");
                                        }
                                    } else {
                                        log.info("old message not received : " + msg.toXML());
                                    }
                                    return answer;
                                } catch (JAXBException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }).subscribe();
                    } else {
                        String msgType = "TEXT";
                        genericOutboundMessage.setMessage(msg.getPayload().getText());
                        sendDoubtnutAPI(genericOutboundMessage, msg, msgType);
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void logTimeTaken(long startTime, int checkpointID) {
        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1000000;
        log.info(String.format("CP-%d: %d ms", checkpointID, duration));
    }


    private Mono<XMessageDAO> getLatestXMessage(String userID, XMessage.MessageState messageState) {
        Pageable paging = (Pageable) CassandraPageRequest.of(
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "timestamp")),
                null
        );
//        return xMsgRepo.findAllByUserIdAndMessageStateAndFromId(paging, userID, messageState.name(), "admin").map(xMessageDAOS -> {
//            if(xMessageDAOS != null && xMessageDAOS.getContent() != null && xMessageDAOS.getContent().size() > 0){
//                return xMessageDAOS.getContent().get(0);
//            }
//            return new XMessageDAO();
//        });
        return xMsgRepo
                .findAllByUserId(userID)
                .doOnError(genericError(String.format("Unable to find previous Message for userID %s", userID)))
                .collectList()
                .map(xMessageDAOS -> {
                    if (xMessageDAOS.size() > 0) {
                        List<XMessageDAO> filteredList = new ArrayList<>();
                        for (XMessageDAO xMessageDAO : xMessageDAOS) {
                            if (xMessageDAO.getMessageState().equals(messageState.name())) {
                                filteredList.add(xMessageDAO);
                            }
                        }
                        if (filteredList.size() > 0) {
                            filteredList.sort(Comparator.comparing(XMessageDAO::getTimestamp));
                        }
                        return xMessageDAOS.get(0);
                    }
                    return new XMessageDAO();
                });
    }

    private Consumer<Throwable> genericError(String s) {
        return c -> {
            log.error(s + "::" + c.getMessage());
        };
    }

    private void sendDoubtnutAPI(GenericOutboundMessage genericOutboundMessage, XMessage msg, String msgType) {
        log.info("sending request to doubtnut...");
        WebClient.builder()
                .baseUrl(url)
                .defaultHeader("Message-Type", msgType)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Api-Key", doubtnutApikey)
                .build()
                .post()
                .uri("/")
                .body(Mono.just(genericOutboundMessage), GenericOutboundMessage.class)
                .retrieve()
                .bodyToMono(GenericMessageResponse.class)
                .map(new Function<GenericMessageResponse, Boolean>() {
                    @Override
                    public Boolean apply(GenericMessageResponse response) {
                        log.info("response received from doubtnut : " + response);
                        if (response != null && (response.getMeta() != null && response.getMeta().getCode() != null && response.getMeta().getCode().equals("200"))
                                && (response.getData() != null && response.getData().getAnswers() != null && response.getData().getAnswers().length > 0)) {
                            XMessagePayload payload = msg.getPayload();
                            for (DoubtnutAnswers doubtnutAnswers : response.getData().getAnswers()) {

                                if (doubtnutAnswers.getImage() != null && !doubtnutAnswers.getImage().isEmpty()) {
                                    MessageMedia messageMedia = new MessageMedia();
                                    if (doubtnutAnswers.getImage().endsWith(".png") || doubtnutAnswers.getImage().endsWith(".jpg")
                                            || doubtnutAnswers.getImage().endsWith(".jpeg")) {
                                        messageMedia.setCategory(MediaCategory.IMAGE);
                                    } else {
                                        log.error("Invalid image format found : " + doubtnutAnswers.getImage());
                                    }
                                    messageMedia.setUrl(doubtnutAnswers.getImage());
                                    messageMedia.setText(doubtnutAnswers.getText());
                                    payload.setText(doubtnutAnswers.getText());
                                    payload.setMedia(messageMedia);
                                    payload.setStylingTag(null);
                                    payload.setButtonChoices(null);
                                } else {
                                    payload.setMedia(null);
                                    payload.setText(doubtnutAnswers.getText());
                                    payload.setStylingTag(null);
                                    payload.setButtonChoices(null);
                                }
                                if (doubtnutAnswers.getButtonChoices() != null && doubtnutAnswers.getButtonChoices().size() > 0) {
                                    payload.setStylingTag(StylingTag.QUICKREPLYBTN);
                                    payload.setButtonChoices(doubtnutAnswers.getButtonChoices());
                                }
                                msg.setPayload(payload);
                                try {
                                    kafkaProducer.send(processOutbound, msg.toXML());
                                } catch (JAXBException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                            return true;
                        } else {
                            log.error("Doubtnut Api - Error Resposne : " + response.getMessage());
                            return false;
                        }
                    }
                }).subscribe();
    }

    private void sendWelcomeMessage(XMessagePayload payload, XMessage msg) throws JAXBException {
        MessageMedia messageMedia = null;
        if (payload.getMedia() == null) {
            messageMedia = new MessageMedia();
        } else {
            messageMedia = payload.getMedia();
        }
        payload.setText(welcomeMessage);
        if (videoUrl != null && !videoUrl.isEmpty()) {
            messageMedia.setCategory(MediaCategory.VIDEO);
            messageMedia.setUrl(videoUrl);
            messageMedia.setText(welcomeMessage);
            payload.setMedia(messageMedia);
        }
        msg.setPayload(payload);
        kafkaProducer.send(processOutbound, msg.toXML());
    }
}
