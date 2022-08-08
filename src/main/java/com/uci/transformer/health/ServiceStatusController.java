package com.uci.transformer.health;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uci.utils.UtilHealthService;
import com.uci.utils.model.ApiResponse;
import com.uci.utils.model.ApiResponseParams;
import com.uci.utils.telemetry.LogTelemetryMessage;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping(value = "/service")
public class ServiceStatusController {
	
	@Autowired
	private UtilHealthService healthService;

    /**
     * In use by sunbird team - to check service liveliness & readliness
     * @return
     * @throws JsonProcessingException
     */
    @RequestMapping(value = "/health", method = RequestMethod.GET, produces = { "application/json", "text/json" })
    public ResponseEntity<ApiResponse> statusCheck() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode resultNode = mapper.readTree("{\"healthy\":true}");

        ApiResponse response = ApiResponse.builder()
                .id("api.service.health.cassandra")
                .params(ApiResponseParams.builder().build())
                .responseCode(HttpStatus.OK.name())
                .result(resultNode)
                .build();

        return ResponseEntity.ok(response);
    }

	@RequestMapping(value = "/health/kafka", method = RequestMethod.GET, produces = { "application/json", "text/json" })
    public ResponseEntity<JsonNode> kafkaStatusCheck() throws IOException, JsonProcessingException {
    	JsonNode jsonNode = getResponseJsonNode();
    	((ObjectNode) jsonNode).put("result", healthService.getKafkaHealthNode());
        
        return ResponseEntity.ok(jsonNode);
    }
    
    @RequestMapping(value = "/health/campaign", method = RequestMethod.GET, produces = { "application/json", "text/json" })
    public ResponseEntity<JsonNode> campaignUrlStatusCheck() throws JsonProcessingException, IOException {
    	JsonNode jsonNode = getResponseJsonNode();
        ((ObjectNode) jsonNode).put("result", healthService.getCampaignUrlHealthNode());
        
        return ResponseEntity.ok(jsonNode);
    }
    
    /**
     * Returns json node for service response
     * 
     * @return JsonNode
     * @throws JsonMappingException
     * @throws JsonProcessingException
     */
    private JsonNode getResponseJsonNode() throws JsonMappingException, JsonProcessingException {
    	ObjectMapper mapper = new ObjectMapper();
    	JsonNode jsonNode = mapper.readTree("{\"id\":\"api.content.service.health\",\"ver\":\"3.0\",\"ts\":null,\"params\":{\"resmsgid\":null,\"msgid\":null,\"err\":null,\"status\":\"successful\",\"errmsg\":null},\"responseCode\":\"OK\",\"result\":{\"healthy\":false}}");
        return jsonNode;
    }
}
