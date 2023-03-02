package com.uci.transformer.generic;

import com.uci.utils.kafka.SimpleProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import messagerosa.core.model.MediaCategory;
import messagerosa.core.model.MessageMedia;
import messagerosa.core.model.XMessage;
import messagerosa.core.model.XMessagePayload;
import messagerosa.xml.XMessageParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.xml.bind.JAXBException;
import java.io.ByteArrayInputStream;
import java.util.UUID;
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

    @KafkaListener(id = "${generic-transformer}", topics = "${generic-transformer}", properties = {"spring.json.value.default.type=java.lang.String"})
    public void onMessage(@Payload String stringMessage) {
        try {
            log.info("Topic generic transformer : " + stringMessage);
            final long startTime = System.nanoTime();
            logTimeTaken(startTime, 0);
            XMessage msg = XMessageParser.parse(new ByteArrayInputStream(stringMessage.getBytes()));
            GenericOutboundMessage genericOutboundMessage = new GenericOutboundMessage();
            WebClient webClient = null;

            // Checking Starting Message
            if (msg.getTransformers().get(0).getMetaData().get("startingMessage").toString().equals(msg.getPayload().getText())) {
                XMessagePayload payload = msg.getPayload();
                MessageMedia messageMedia = null;
                if (payload.getMedia() == null) {
                    messageMedia = new MessageMedia();
                } else {
                    messageMedia = payload.getMedia();
                }
                payload.setText("");
                messageMedia.setCategory(MediaCategory.VIDEO);
                messageMedia.setUrl(videoUrl);
                messageMedia.setText(welcomeMessage);
                payload.setMedia(messageMedia);
                msg.setPayload(payload);
                kafkaProducer.send(processOutbound, msg.toXML());
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
                payload.setText("");
                messageMedia.setCategory(MediaCategory.VIDEO);
                messageMedia.setUrl(videoUrl);
                messageMedia.setText(welcomeMessage);
                payload.setMedia(messageMedia);
                msg.setSessionId(UUID.randomUUID());
                msg.setPayload(payload);
                kafkaProducer.send(processOutbound, msg.toXML());
            } else {
//                if(msg.getPayload().getText()!= null && !msg.getPayload().getText().startsWith("#conf")){
//
//                }
                String msgType = null;
                if (msg.getPayload() != null && msg.getPayload().getMedia() != null && (msg.getPayload().getMedia().getCategory().equals(MediaCategory.IMAGE)
                        || msg.getPayload().getMedia().getCategory().equals(MediaCategory.AUDIO))) {
                    if (msg.getPayload().getMedia().getCategory().equals(MediaCategory.IMAGE)) {
                        msgType = "IMAGE";
                    } else if (msg.getPayload().getMedia().getCategory().equals(MediaCategory.AUDIO)) {
                        msgType = "AUDIO";
                    }
                    genericOutboundMessage.setMessage(msg.getPayload().getMedia().getUrl());
                    webClient = WebClient.builder()
                            .baseUrl(url)
                            .defaultHeader("Message-Type", msgType)
                            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .build();
                } else {
                    msgType = "TEXT";
                    genericOutboundMessage.setMessage(msg.getPayload().getText());
                    webClient = WebClient.builder()
                            .baseUrl(url)
                            .defaultHeader("Message-Type", msgType)
                            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .build();
                }
                log.info(genericOutboundMessage.toString());
                webClient.post()
                        .uri("/v10/questions/ask-tara")
                        .body(Mono.just(genericOutboundMessage), GenericOutboundMessage.class)
                        .retrieve()
                        .bodyToMono(GenericMessageResponse.class)
                        .map(new Function<GenericMessageResponse, Boolean>() {
                            @Override
                            public Boolean apply(GenericMessageResponse response) {
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
                                            payload.setMedia(messageMedia);

                                        } else {
                                            payload.setMedia(null);
                                            payload.setText(doubtnutAnswers.getText());
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
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void logTimeTaken(long startTime, int checkpointID) {
        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1000000;
        log.info(String.format("CP-%d: %d ms", checkpointID, duration));
    }

}
