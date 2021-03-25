package com.kd.kdspring.web;

import java.util.logging.Logger;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;

import com.kd.kdspring.account.Account;

public class WebAccountService {
    @Autowired
    private WebClient.Builder webClientBuilder;

    protected String serviceUrl;

    protected Logger logger = Logger.getLogger(WebAccountService.class.getName());

    public WebAccountService(String serviceUrl) {
        this.serviceUrl = serviceUrl.startsWith("http") ? serviceUrl : "http://" + serviceUrl;
        this.serviceUrl += "/api";
    }

    public ResponseEntity<String> getHome() {
        //logger.info("findByNumber() invoked: for " + accountNumber);
        try {
            String msg = webClientBuilder.build().get()
                .uri(serviceUrl)
                .retrieve()
                .bodyToMono(String.class).block();
            return ResponseEntity.status(HttpStatus.OK).body(msg);
        } catch (Exception e) {
            logger.severe(e.getClass() + ": " + e.getLocalizedMessage());
            return null;
        }
    }

    public Account findByNumber(String accountNumber) {
        logger.info("findByNumber() invoked: for " + accountNumber);

        try {
            Object credentials = SecurityContextHolder.getContext().getAuthentication().getCredentials();
            String TOKEN_PREFIX = "Bearer ";
            String token = credentials.toString();
    
            // REST call to back-end Account microservice
            Account account = webClientBuilder.build().get()
                .uri(String.join("/", serviceUrl, "accounts", accountNumber))
                .header(HttpHeaders.AUTHORIZATION, TOKEN_PREFIX + token)
                .retrieve()
                .bodyToMono(Account.class).block();
            return account;
        } catch (Exception e) {
            logger.severe(e.getClass() + ": " + e.getLocalizedMessage());
            return null;
        }
    }
}
