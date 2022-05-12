package com.uci.transformer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.uci.transformer.odk.MenuManager;
import com.uci.transformer.odk.ODKConsumerReactive;
import com.uci.transformer.odk.entity.GupshupStateEntity;
import com.uci.transformer.odk.entity.Question;
import com.uci.transformer.odk.model.Form;
import com.uci.transformer.odk.persistance.FormsDao;
import com.uci.transformer.odk.repository.AssessmentRepository;
import com.uci.transformer.odk.repository.MessageRepository;
import com.uci.transformer.odk.repository.QuestionRepository;
import com.uci.transformer.odk.repository.StateRepository;
import com.uci.utils.CampaignService;
import com.uci.utils.kafka.SimpleProducer;
import com.uci.utils.service.UserService;
import io.fusionauth.client.FusionAuthClient;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.r2dbc.postgresql.codec.Json;
import messagerosa.core.model.*;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverRecord;

import javax.validation.Payload;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Time;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

@TestPropertySource("classpath:test-application.properties")
public class TransformerTestConfig {

    @Bean
    public SimpleProducer getSimpleProducer(){
        SimpleProducer simpleProducer = Mockito.mock(SimpleProducer.class);
        Mockito.doNothing().when(simpleProducer).send(Mockito.any(), Mockito.any());
        return simpleProducer;
    }

    @Bean
    @Qualifier("rest")
    public RestTemplate getRestTemplate() {
        return new RestTemplate();
    }

    @Bean
    public FusionAuthClient getFAClient() {
        return new FusionAuthClient("FUSIONAUTH_KEY", "FUSIONAUTH_URL");
    }


    @Bean
    public ODKConsumerReactive getOdkConsumerReactive() throws Exception {
/*
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode campaignNode = objectMapper.readTree("{\"id\":\"d655cf03-1f6f-4510-acf6-d3f51b488a5e\",\"name\":\"UCI Demo\",\"startingMessage\":\"Hi UCI\",\"users\":[],\"logicIDs\":[\"e96b0865-5a76-4566-8694-c09361b8ae32\"],\"owners\":null,\"created_at\":\"2021-07-08T18:48:37.740Z\",\"updated_at\":\"2022-02-11T14:09:53.570Z\",\"status\":\"enabled\",\"description\":\"For Internal Demo\",\"startDate\":\"2022-02-01T00:00:00.000Z\",\"endDate\":null,\"purpose\":\"For Internal Demo\",\"ownerOrgID\":\"ORG_001\",\"ownerID\":\"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"userSegments\":[],\"logic\":[{\"id\":\"e96b0865-5a76-4566-8694-c09361b8ae32\",\"transformers\":[{\"id\":\"bbf56981-b8c9-40e9-8067-468c2c753659\",\"meta\":{\"form\":\"https://hosted.my.form.here.com\",\"formID\":\"UCI-demo-1\"}}],\"adapter\":{\"id\":\"44a9df72-3d7a-4ece-94c5-98cf26307324\",\"channel\":\"WhatsApp\",\"provider\":\"gupshup\",\"config\":{\"2WAY\":\"2000193033\",\"phone\":\"9876543210\",\"HSM_ID\":\"2000193031\",\"credentials\":{\"vault\":\"samagra\",\"variable\":\"gupshupSamagraProd\"}},\"name\":\"SamagraProd\",\"updated_at\":\"2021-06-16T06:02:39.125Z\",\"created_at\":\"2021-06-16T06:02:41.823Z\"},\"name\":\"UCI Demo\",\"created_at\":\"2021-07-08T18:47:44.925Z\",\"updated_at\":\"2022-02-03T12:29:32.959Z\",\"description\":null}]}");
        Mockito.when(campaignService.getCampaignFromNameTransformer(Mockito.anyString())).thenReturn(Mono.just(campaignNode));

        GupshupStateEntity gupshupStateEntity = new GupshupStateEntity(123L, "7823807161", "<?xml version='1.0' ?><data id=\"UCI-demo-1\" version=\"1\" xmlns:ev=\"http://www.w3.org/2001/xml-events\" xmlns:orx=\"http://openrosa.org/xforms\" xmlns:odk=\"http://www.opendatakit.org/xforms\" xmlns:h=\"http://www.w3.org/1999/xhtml\" xmlns:jr=\"http://openrosa.org/javarosa\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"><intro_group><intro /><phone_number /><preferences /></intro_group><vacancy_information_count /><meta><instanceID>uuid:21c55cd1-1f63-4eb7-a33b-4a5cf8820291</instanceID></meta></data>", "question./data/intro_group[1]/preferences[1]", "UCI-demo-1");
        Mockito.when(stateRepo.findByPhoneNoAndBotFormName(Mockito.any(), Mockito.any())).thenReturn(Mono.just(gupshupStateEntity));

        Json meta = Json.of("{\"title\": \"Hi! RozgarBot welcomes you!\\n\\nYou may navigate through me to first register yourself as a recruiter and then post vacancies. During the flow, Enter # to go to the previous step and * to go back to the original menu. \\n__ \\n\\nPlease select the number corresponding to the option you want to proceed ahead with. \\n__ \\n\\n\", \"choices\": [\"1 Register as recruiter\", \"2 Post a job vacancy\"]}");
        Question question = Question
                .builder()
                .id(UUID.fromString("daaa3e77-cc64-4a23-8e1e-071c51807f9a"))
                .formID("UCI-demo-1")
                .formVersion("1")
                .XPath("question./data/intro_group[1]/preferences[1]")
                .questionType(Question.QuestionType.STRING)
                .meta(null)
                .updatedOn(LocalDateTime.parse("2021-11-10T17:53:07.649295"))
                .createdOn(LocalDateTime.parse("2021-11-10T17:53:07.649295"))
                .build();


        Mockito
                .when(questionRepository.findQuestionByXPathAndFormIDAndFormVersion(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(Flux.just(question));


        Flux.usingWhen();
*/


        XMessage xMessage = XMessage
                            .builder()
                .app("UCI Demo")
                .messageType(XMessage.MessageType.TEXT)
                .adapterId("44a9df72-3d7a-4ece-94c5-98cf26307324")
                .messageId(new MessageId(null, "ABEGkZlgQyWAAgo-sDVSUOa9jH0z", null))
                .to(SenderReceiverInfo.builder().userID("7823807161").campaignID("UCI Demo").bot(false).broadcast(false).deviceType(DeviceType.PHONE).build())
                .from(SenderReceiverInfo.builder().userID("admin").bot(false).broadcast(false).build())
                .channelURI("WhatsApp")
                .providerURI("Netcore")
                .timestamp(1636621428000l)
                .messageState(XMessage.MessageState.REPLIED)
                .conversationLevel(new ArrayList<Integer>(2))
                .payload(XMessagePayload.builder().text("hello").build())
                .build();


        ODKConsumerReactive odkConsumerReactive = Mockito.spy(new ODKConsumerReactive(reactiveKafkaReceiver()));

        Mockito.doReturn(Mono.just(xMessage)).when(odkConsumerReactive).transform(Mockito.any(XMessage.class));

        return odkConsumerReactive;
    }

    @Bean
    public MenuManager getMenuManager(){
        String formPath = "/tmp/forms2/UCI demo 1.xml";
        String formID = "UCI-demo-1";
        String xpath = "question./data/intro_group[1]/preferences[1]";

        return new MenuManager(xpath, null, null, formPath, formID, false, null, null);
    }

    @Bean
    Flux<ReceiverRecord<String, String>> reactiveKafkaReceiver() {
        ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<String, String >("inbound-processed", 0, 33, null,
                "<?xml version=\"1.0\"?>\n" +
                        "<xMessage>\n" +
                        "    <app>UCI Demo</app>\n" +
                        "    <channel>WhatsApp</channel>\n" +
                        "    <channelURI>WhatsApp</channelURI>\n" +
                        "    <from>\n" +
                        "        <bot>false</bot>\n" +
                        "        <broadcast>false</broadcast>\n" +
                        "        <deviceType>PHONE</deviceType>\n" +
                        "        <userID>7823807161</userID>\n" +
                        "    </from>\n" +
                        "    <messageId>\n" +
                        "        <channelMessageId>ABEGkZlgQyWAAgo-sDVSUOa9jH0z</channelMessageId>\n" +
                        "    </messageId>\n" +
                        "    <messageState>REPLIED</messageState>\n" +
                        "    <messageType>TEXT</messageType>\n" +
                        "    <payload>\n" +
                        "        <text>Hi UCI</text>\n" +
                        "    </payload>\n" +
                        "    <provider>Netcore</provider>\n" +
                        "    <providerURI>Netcore</providerURI>\n" +
                        "    <timestamp>1636621428000</timestamp>\n" +
                        "    <to>\n" +
                        "        <bot>false</bot>\n" +
                        "        <broadcast>false</broadcast>\n" +
                        "        <userID>admin</userID>\n" +
                        "    </to>\n" +
                        "</xMessage>"
        );

        ReceiverOffset receiverOffset = new ReceiverOffset() {
            @Override
            public TopicPartition topicPartition() {
                return new TopicPartition("inbound-processed", 0);
            }

            @Override
            public long offset() {
                return 0;
            }

            @Override
            public void acknowledge() {

            }

            @Override
            public Mono<Void> commit() {
                return null;
            }
        };
        ReceiverRecord<String, String> receiverRecord = new ReceiverRecord<String, String >(consumerRecord, receiverOffset);
        return Flux.just(receiverRecord);
    }

    @Bean
    public WebClient getWebClient(){
        WebClient webClient = WebClient.builder()
                .baseUrl("http://baseurl/")
                .defaultHeader("admin-token", "CAMPAIGN_ADMIN_TOKEN")
                .build();
        return webClient;
    }

    @Bean
    public Cache getCache(){
        Cache cache = Mockito.mock(Cache.class);
        return cache;
    }

    @Bean
    public CampaignService getCampaignService() {

        return new CampaignService(getWebClient(), getFAClient(), getCache());
    }

    @Bean
    public QuestionRepository getQuestionRepository(){
        return Mockito.mock(QuestionRepository.class);
    }

    @Bean
    public AssessmentRepository getAssessmentRepository(){
        return Mockito.mock(AssessmentRepository.class);
    }

    @Bean
    @Qualifier("custom")
    public RestTemplate getCustomTemplate() {
        RestTemplateBuilder builder = new RestTemplateBuilder();
        Credentials credentials = new UsernamePasswordCredentials("ODK_USERNAME","ODK_PASSWORD");
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, credentials);

        HttpClient httpClient = HttpClients
                .custom()
                .disableCookieManagement()
                .setDefaultCredentialsProvider(credentialsProvider)
                .build();

        return builder
                .requestFactory(() -> new HttpComponentsClientHttpRequestFactory(httpClient))
                .build();
    }

    @Bean
    public StateRepository getStateRepository(){
        StateRepository stateRepository = Mockito.mock(StateRepository.class);
        return stateRepository;
    }

    @Bean
    public MessageRepository getMessageRepository(){
        MessageRepository messageRepository = Mockito.mock(MessageRepository.class);
        return messageRepository;
    }

    @Bean
    public UserService getUserService(){
        return new UserService();
    }

}
