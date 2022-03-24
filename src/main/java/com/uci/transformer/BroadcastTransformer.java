package com.uci.transformer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.uci.utils.service.UserService;
import com.uci.utils.CampaignService;
import com.uci.utils.kafka.SimpleProducer;
import io.fusionauth.domain.Application;
import lombok.extern.slf4j.Slf4j;
import messagerosa.core.model.SenderReceiverInfo;
import messagerosa.core.model.XMessage;
import messagerosa.core.model.XMessagePayload;
import messagerosa.xml.XMessageParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.xml.bind.JAXBException;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import com.uci.utils.model.UserWithTemplate;

@Slf4j
@Component
public class BroadcastTransformer extends TransformerProvider {

    @Autowired
    public SimpleProducer kafkaProducer;

//    @Autowired
//    private StateRepository stateRepo;
//
//    @Autowired
//    private MessageRepository msgRepo;

    @Autowired
    private UserService userService;

    @Autowired
    CampaignService campaignService;

    @Override
    public Mono<XMessage> transform(XMessage xMessage) {
        //TODO: Get all the phone numbers for the users and update the userID

        /*
        try {
            StringBuilder userIds = new StringBuilder();
            String ids = "";

            List<String> users = UserService.findUsersForESamwad(campaignService.getCampaignFromName(xMessage.getApp()).name);
            for (String user : users) {
                userIds.append(user).append(",");
            }
            if (userIds != null && userIds.length() > 0) {
                ids = userIds.substring(0, userIds.length() - 1);
            }
            SenderReceiverInfo to = xMessage.getTo();
            to.setUserID(ids);
            xMessage.setTo(to);

        } catch (Exception e) {
            e.printStackTrace();
            log.error("Exception in getting users for this campaign");
        }
         */
        return Mono.just(xMessage);
    }

    private XMessage getClone(XMessage nextMessage) {
        XMessage cloneMessage = null;
        try {
            cloneMessage = XMessageParser.parse(new ByteArrayInputStream(nextMessage.toXML().getBytes()));
        } catch (JAXBException e) {
            e.printStackTrace();
        }
        return cloneMessage;
    }

    @Override
    public Mono<List<XMessage>> transformToMany(XMessage xMessage) {
        ArrayList<XMessage> messages = new ArrayList<>();
        try{
            Application currentApplication = campaignService.getCampaignFromNameESamwad(xMessage.getApp());
            ArrayList<HashMap<String, Object>> d = (ArrayList) currentApplication.data.get("parts");
            if(d.get(0).get("template") != null){
                ArrayList<UserWithTemplate> usersWithTemplate = userService.getUsersAndTemplateFromFederatedServers(xMessage.getApp());
                for(UserWithTemplate u: usersWithTemplate){
                    XMessage clone = getClone(xMessage);
                    XMessagePayload payload = clone.getPayload();
                    payload.setText(u.getText().replace("\\n", "\n"));
                    clone.setPayload(payload);

                    SenderReceiverInfo to = clone.getTo();
                    to.setUserID(u.getUserPhone());
                    clone.setTo(to);

                    clone.setMessageState(XMessage.MessageState.NOT_SENT);
                    messages.add(clone);
                }
            }else{
                List<String> users = userService.getUsersPhoneFromFederatedServers(xMessage.getApp());
                System.out.println("Total users:: " + users.size());
                for (String phone: users){
                    XMessage clone = getClone(xMessage);

                    SenderReceiverInfo to = clone.getTo();
                    to.setUserID(phone);
                    clone.setTo(to);

                    clone.setMessageState(XMessage.MessageState.NOT_SENT);
                    messages.add(clone);
                }
            }
        }catch (Exception e){
            log.error(e.getMessage());
        }

        return Mono.just(messages);

    }

    @KafkaListener(id = "BroadcastTransformer", topics = "BroadcastTransformer")
    public void consumeMessage(String message) throws Exception {

        long startTime = System.nanoTime();
        log.info("BroadcastTransformer Transformer Message: " + message);

        XMessage xMessage = XMessageParser.parse(new ByteArrayInputStream(message.getBytes()));
//        ArrayList<XMessage> messages =
        transformToMany(xMessage).subscribe(new Consumer<List<XMessage>>() {
            @Override
            public void accept(List<XMessage> xMessages) {
                xMessages = (ArrayList<XMessage>)xMessages;
                for (XMessage msg : xMessages) {
                    try {
                        kafkaProducer.send("broadcast", msg.toXML());
                        kafkaProducer.send("PassThrough", msg.toXML());
                    } catch (JAXBException e) {
                        e.printStackTrace();
                    }
                }

                long endTime = System.nanoTime();
                long duration = (endTime - startTime);
                log.error("Total time spent in processing form: " + duration / 1000000);
            }
        });

    }
}
