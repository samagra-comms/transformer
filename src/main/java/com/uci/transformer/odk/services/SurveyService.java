package com.uci.transformer.odk.services;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@Service
public class SurveyService {

    @Value("${nlapp.userurl}")
    private String SURVEY_URL;

    @Value("${nlapp.user.xappid}")
    private String SURVEY_XAPPLICATION_ID;

    @Value("${nlapp.userauth}")
    private String SURVEY_AUTH;


    public JSONObject getUserByPhoneFromFederatedServers(String hiddenFieldsStr, String phone) {

        String baseURL = SURVEY_URL + "?queryString=(username:\"" + phone + "\", mobilePhone: \"" + phone + "\")";
        String hiddenName = null;
        if (hiddenFieldsStr != null) {
            hiddenName = new JSONArray(hiddenFieldsStr).getJSONObject(0).get("name").toString();
        }
        log.info("found hidden name : " + hiddenName);
        OkHttpClient client = new OkHttpClient().newBuilder()
                .connectTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(90, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(baseURL)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .addHeader(HttpHeaders.AUTHORIZATION, SURVEY_AUTH)
                .addHeader("x-application-id", SURVEY_XAPPLICATION_ID)
                .build();
        try {
            Response response = client.newCall(request).execute();
            JSONObject users = new JSONObject(response.body().string());
            try {
                log.info("phone: " + phone + ", users data: " + users.getJSONObject("result"));
//                if (users.getJSONObject("result") != null && users.getJSONObject("result").getJSONArray("users") != null) {
//
//                }
                if (users.getJSONObject("result") != null
                        && users.getJSONObject("result").getJSONArray("users") != null
                        && users.getJSONObject("result").getJSONArray("users").getJSONObject(0) != null
                        && users.getJSONObject("result").getJSONArray("users").getJSONObject(0).get(hiddenName) != null) {
                    String value = users.getJSONObject("result").getJSONArray("users").getJSONObject(0).get(hiddenName).toString();
                    JSONObject user = new JSONObject();
                    user.put(hiddenName, value);
                    return user;
                } else {
                    JSONObject user = new JSONObject();
                    user.put(hiddenName, "");
                    return user;
                }
            } catch (Exception e) {
                log.error("error : " + e.getMessage());
                JSONObject user = new JSONObject();
                user.put(hiddenName, "");
                return user;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}
