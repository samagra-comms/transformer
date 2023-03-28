package com.uci.transformer.odk;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileWriter;
import java.util.Collections;
import java.util.Date;

@Slf4j
@Component
public class UploadService {

    private static HttpHeaders getVerifyHttpHeader4() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static HttpHeaders getVerifyHttpHeader2(String str) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
        headers.add("content-type", "multipart/form-data; boundary=----WebKitFormBoundarydaqjAWcH4XdvwPoz");
        headers.add("cookie", str);
        return headers;
    }

    private static MappingJackson2HttpMessageConverter getMappingJackson2HttpMessageConverter() {
        MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter =
                new MappingJackson2HttpMessageConverter();
        mappingJackson2HttpMessageConverter
                .setSupportedMediaTypes(Collections.singletonList(MediaType.APPLICATION_FORM_URLENCODED));
        return mappingJackson2HttpMessageConverter;
    }


    public boolean submit(String form, RestTemplate restTemplate, RestTemplate customRestTemplate) {
        try {
            String ODK = System.getenv("ODK_URL") + "/Aggregate.html#submissions/filter///";
            String ODK2 = System.getenv("ODK_URL") + "/submission";
            HttpEntity<String> request = new HttpEntity<String>(getVerifyHttpHeader4());
            HttpEntity<String> response = customRestTemplate.exchange(ODK, HttpMethod.GET, request, String.class);
            System.out.println(new ObjectMapper().writeValueAsString(response));
            HttpHeaders headers = response.getHeaders();
            String set_cookie = headers.getFirst(HttpHeaders.SET_COOKIE);
            log.info("set-cookie {}", set_cookie);

            String fileName = "instance-" + new Date().getTime() + ".xml";

            File f = new File(fileName);
            FileWriter myWriter = new FileWriter(fileName);
            myWriter.write(form);
            myWriter.close();

            File myObj = new File(fileName);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("xml_submission_file", new FileSystemResource(myObj));

            HttpEntity<MultiValueMap<String, Object>> request2 = new HttpEntity<>(body, getVerifyHttpHeader2(set_cookie));
            restTemplate.getMessageConverters().add(getMappingJackson2HttpMessageConverter());
            HttpEntity<String> response2 = customRestTemplate.postForEntity(ODK2, request2, String.class);
            myObj.delete();
            log.info("response2 {}", response2);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

    }
}
