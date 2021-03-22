package com.kd.kdspring.web;

import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.web.client.RestTemplate;

import com.kd.kdspring.user.UserInfo;

public class WebUserService {
    @Autowired
    @LoadBalanced
    protected RestTemplate restTemplate;

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
            return restTemplate.getForObject(serviceUrl + "/users/{username}", UserInfo.class, username);
        } catch (Exception e) {
            logger.severe(e.getClass() + ": " + e.getLocalizedMessage());
            return null;
        }
    }
}
