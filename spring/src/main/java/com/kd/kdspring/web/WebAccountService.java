package com.kd.kdspring.web;

import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;

import com.kd.kdspring.account.Account;

public class WebAccountService {
    @Autowired
    @LoadBalanced
    protected RestTemplate restTemplate;

    protected String serviceUrl;

    protected Logger logger = Logger.getLogger(WebAccountService.class.getName());

    public WebAccountService(String serviceUrl) {
        this.serviceUrl = serviceUrl.startsWith("http") ? serviceUrl : "http://" + serviceUrl;
        this.serviceUrl += "/api";
    }

    public ResponseEntity<String> getHome() {
        //logger.info("findByNumber() invoked: for " + accountNumber);
        try {
            return restTemplate.getForEntity(serviceUrl + "/", String.class);
        } catch (Exception e) {
            //logger.severe(e.getClass() + ": " + e.getLocalizedMessage());
            return null;
        }
    }

    public Account findByNumber(String accountNumber) {
        logger.info("findByNumber() invoked: for " + accountNumber);
        try {
            // REST call to back-end Account microservice
            return restTemplate.getForObject(serviceUrl + "/accounts/{number}", Account.class, accountNumber);
        } catch (Exception e) {
            logger.severe(e.getClass() + ": " + e.getLocalizedMessage());
            return null;
        }
    }
}
