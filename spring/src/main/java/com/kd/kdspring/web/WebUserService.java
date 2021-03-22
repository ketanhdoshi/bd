package com.kd.kdspring.web;

import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.function.client.WebClient;

import com.kd.kdspring.user.UserInfo;

public class WebUserService {
    @Autowired
    private WebClient.Builder webClientBuilder;

    protected String serviceUrl;

    protected Logger logger = Logger.getLogger(WebUserService.class.getName());

    public WebUserService(String serviceUrl) {
        this.serviceUrl = serviceUrl.startsWith("http") ? serviceUrl : "http://" + serviceUrl;
        this.serviceUrl += "/api";
    }

    public UserInfo findByUsername(String username) {
        logger.info("findByUsername() invoked: for " + username);
        try {
            // REST call to back-end User microservice
            UserInfo user = webClientBuilder.build().get()
                .uri(String.join("/", serviceUrl, "users", username))
                .retrieve()
                .bodyToMono(UserInfo.class).block();
            return user;
        } catch (Exception e) {
            logger.severe(e.getClass() + ": " + e.getLocalizedMessage());
            return null;
        }
    }
}
