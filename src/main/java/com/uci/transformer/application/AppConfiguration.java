package com.uci.transformer.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.uci.utils.CampaignService;
import com.uci.utils.kafka.ReactiveProducer;
import io.fusionauth.client.FusionAuthClient;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Configuration
@EnableAutoConfiguration
public class AppConfiguration {

    @Value("${campaign.url}")
    public String CAMPAIGN_URL;
    
    @Value("${campaign.admin.token}")
	public String CAMPAIGN_ADMIN_TOKEN;
    
    @Value("${spring.redis.database}")
	private String redisDb;
	
	@Value("${spring.redis.host}")
	private String redisHost;
	
	@Value("${spring.redis.port}")
	private String redisPort;

    @Bean
    @Qualifier("rest")
    public RestTemplate getRestTemplate() {
        return new RestTemplate();
    }

    @Value("${fusionauth.url}")
    public String FUSIONAUTH_URL;

    @Value("${fusionauth.key}")
    public String FUSIONAUTH_KEY;

    @Value("${odk.username}")
    public String ODK_USERNAME;

    @Value("${odk.password}")
    public String ODK_PASSWORD;
    
    @Autowired
    public Cache<Object, Object> cache;

    @Value("${spring.kafka.bootstrap-servers}")
    private String BOOTSTRAP_SERVERS;

    private final String GROUP_ID = "transformer";

    @Bean
    public FusionAuthClient getFAClient() {
        return new FusionAuthClient(FUSIONAUTH_KEY, FUSIONAUTH_URL);
    }

    @Bean
    public CampaignService getCampaignService() {
        System.out.println("Inside getCampaignService :: " + CAMPAIGN_ADMIN_TOKEN + " :: " + CAMPAIGN_URL);
        WebClient webClient = WebClient.builder()
                .baseUrl(CAMPAIGN_URL)
                .defaultHeader("admin-token", CAMPAIGN_ADMIN_TOKEN)
                .build();
        return new CampaignService(webClient, getFAClient(), cache);
    }

    @Bean
    @Qualifier("custom")
    public RestTemplate getCustomTemplate() {
        RestTemplateBuilder builder = new RestTemplateBuilder();
        Credentials credentials = new UsernamePasswordCredentials(ODK_USERNAME,ODK_PASSWORD);
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
    Map<String, Object> kafkaConsumerConfiguration() {
        Map<String, Object> configuration = new HashMap<>();
        configuration.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        configuration.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        configuration.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, org.springframework.kafka.support.serializer.JsonSerializer.class);
        configuration.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, org.springframework.kafka.support.serializer.JsonSerializer.class);
        configuration.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        return configuration;
    }

    @Bean
    Map<String, Object> kafkaProducerConfiguration() {
        Map<String, Object> configuration = new HashMap<>();
        configuration.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        configuration.put(ProducerConfig.CLIENT_ID_CONFIG, "sample-producer");
        configuration.put(ProducerConfig.ACKS_CONFIG, "all");
        configuration.put(org.springframework.kafka.support.serializer.JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        configuration.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, org.springframework.kafka.support.serializer.JsonSerializer.class);
        configuration.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, org.springframework.kafka.support.serializer.JsonSerializer.class);
        return configuration;
    }

    @Bean
    ReceiverOptions<String, String> kafkaReceiverOptions(@Value("${odk-topic-pattern}") String[] inTopicName) {
        ReceiverOptions<String, String> options = ReceiverOptions.create(kafkaConsumerConfiguration());
        return options.subscription(Pattern.compile(inTopicName[0]))
                .withKeyDeserializer(new JsonDeserializer<>())
                .withValueDeserializer(new JsonDeserializer(String.class));
    }

    @Bean
    SenderOptions<Integer, String> kafkaSenderOptions() {
        return SenderOptions.create(kafkaProducerConfiguration());
    }

    @Bean
    Flux<ReceiverRecord<String, String>> reactiveKafkaReceiver(ReceiverOptions<String, String> kafkaReceiverOptions) {
        return KafkaReceiver.create(kafkaReceiverOptions).receive();
    }

    @Bean
    KafkaSender<Integer, String> reactiveKafkaSender(SenderOptions<Integer, String> kafkaSenderOptions) {
        return KafkaSender.create(kafkaSenderOptions);
    }
    
    @Bean
    ProducerFactory<String, String> producerFactory(){
    	ProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory(kafkaProducerConfiguration());
    	return producerFactory;
    }
    
    @Bean
    KafkaTemplate<String, String> kafkaTemplate() {
    	KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(producerFactory());
    	return (KafkaTemplate<String, String>) kafkaTemplate;
    }
    
    @Bean
	JedisConnectionFactory jedisConnectionFactory() {
		JedisConnectionFactory jedisConFactory
	      = new JedisConnectionFactory();
	    jedisConFactory.setHostName(redisHost);
	    Integer port = Integer.parseInt(redisPort);
	    jedisConFactory.setPort(port);
	    Integer dbIndex = Integer.parseInt(redisDb);
	    jedisConFactory.setDatabase(dbIndex);
//		jedisConFactory.getPoolConfig().setMaxIdle(30);
//		jedisConFactory.getPoolConfig().setMinIdle(10);
	    return jedisConFactory;
	}

	@Bean
	public RedisTemplate<String, Object> redisTemplate() {
	    RedisTemplate<String, Object> template = new RedisTemplate<>();
	    template.setConnectionFactory(jedisConnectionFactory());
	    template.setKeySerializer(new StringRedisSerializer());
	    template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
//	    template.setEnableTransactionSupport(true);
	    return template;
	}

//    @Bean
//    ReactiveProducer kafkaReactiveProducer() {
//        return new ReactiveProducer();
//    }
}
