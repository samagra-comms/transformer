package com.uci.transformer.generic;

import com.uci.utils.BotService;
import com.uci.utils.kafka.SimpleProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import messagerosa.core.model.*;
import messagerosa.xml.XMessageParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverRecord;

import javax.xml.bind.JAXBException;
import java.io.ByteArrayInputStream;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
@Slf4j
public class GenericTransformerConsumer {
    private final Flux<ReceiverRecord<String, String>> reactiveKafkaReceiver;

    private static final String SMS_BROADCAST_IDENTIFIER = "Generic";
    public static final String XML_PREFIX = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

    @Autowired
    public SimpleProducer kafkaProducer;

    @Value("${generic-transformer}")
    public String genericTransformer;

    @Value("${processOutbound}")
    public String processOutbound;

    @Autowired
    public BotService botService;

    @Value("${doubtnut.baseurl}")
    public String url;


    @KafkaListener(id = "${generic-transformer}", topics = "${generic-transformer}", properties = {"spring.json.value.default.type=java.lang.String"})
    public void onMessage(@Payload String stringMessage) {
        try {
            log.info("Topic generic transformer : " + stringMessage);
            final long startTime = System.nanoTime();
            logTimeTaken(startTime, 0);
            XMessage msg = XMessageParser.parse(new ByteArrayInputStream(stringMessage.getBytes()));
            GenericOutboundMessage genericOutboundMessage = new GenericOutboundMessage();
            WebClient webClient = null;
            if (msg.getPayload() != null && msg.getPayload().getMedia() != null && (msg.getPayload().getMedia().getCategory().equals(MediaCategory.IMAGE)
                    || msg.getPayload().getMedia().getCategory().equals(MediaCategory.AUDIO))) {
                String msgType = null;
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
                genericOutboundMessage.setMessage(msg.getPayload().getText());
                webClient = WebClient.builder()
                        .baseUrl(url)
                        .defaultHeader("Message-Type", "TEXT")
                        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .build();
            }

            webClient.post()
                    .uri("/v10/questions/ask-tara")
                    .body(Mono.just(genericOutboundMessage), GenericOutboundMessage.class)
                    .retrieve()
                    .bodyToMono(GenericMessageResponse.class)
                    .map(new Function<GenericMessageResponse, Boolean>() {
                        @Override
                        public Boolean apply(GenericMessageResponse response) {
                            if (response != null && response.getMeta().getCode().equals("200") && !response.getData().getText().isEmpty()) {
                                log.info("Response : " + response.getData().getText());
                                XMessagePayload payload = msg.getPayload();

                                if (msg.getPayload() != null && msg.getPayload().getMedia() != null && (msg.getPayload().getMedia().getCategory().equals(MediaCategory.IMAGE) ||
                                        msg.getPayload().getMedia().getCategory().equals(MediaCategory.AUDIO))) {
                                    payload.setMedia(null);
                                    payload.setText(response.getData().getText());
                                } else {
                                    payload.setText(response.getData().getText());
                                }
                                msg.setPayload(payload);
                                try {
                                    kafkaProducer.send(processOutbound, msg.toXML());
                                } catch (JAXBException e) {
                                    throw new RuntimeException(e);
                                }
                                return true;
                            } else {
                                log.error("Doubtnut Api - Error Resposne : " + response.getMessage());
                                return false;
                            }
                        }
                    }).subscribe();
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
