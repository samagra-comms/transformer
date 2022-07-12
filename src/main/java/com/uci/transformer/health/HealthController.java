package com.uci.transformer.health;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uci.utils.UtilHealthService;

import com.uci.utils.model.ApiResponse;
import com.uci.utils.model.ApiResponseParams;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class HealthController {

	@Autowired
	private UtilHealthService healthService;
	
    @RequestMapping(value = "/health", method = RequestMethod.GET, produces = { "application/json", "text/json" })
    public ResponseEntity<ApiResponse> statusCheck() throws JsonProcessingException, IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode resultNode = mapper.readTree("{\"checks\":[{\"name\":\"kafka\",\"healthy\":false},{\"name\":\"campaign\",\"healthy\":false}],\"healthy\":true}");
        
        /* Kafka health info */
        JsonNode kafkaHealthNode = healthService.getKafkaHealthNode();
        JsonNode kafkaNode = mapper.createObjectNode();
        ((ObjectNode) kafkaNode).put("name", "Kafka");
        ((ObjectNode) kafkaNode).put("healthy", kafkaHealthNode.get("healthy").asBoolean());
        ((ObjectNode) kafkaNode).put("details", kafkaHealthNode.get("details"));
        
        /* create `ArrayNode` object */
        ArrayNode arrayNode = mapper.createArrayNode();
        
        /* add JSON users to array */
        arrayNode.addAll(Arrays.asList(kafkaNode));
        
        ((ObjectNode) resultNode).putArray("checks").addAll(arrayNode);
        
        /* System overall health */
        if(kafkaHealthNode.get("healthy").booleanValue()) {
        	((ObjectNode) resultNode).put("healthy", true);
        } else {
        	((ObjectNode) resultNode).put("healthy", false);
        }

        ApiResponse response = ApiResponse.builder()
                    .id("api.health")
                    .params(ApiResponseParams.builder().build())
                    .responseCode(HttpStatus.OK.name())
                    .result(resultNode)
                    .build();

        return ResponseEntity.ok(response);
    }
}
