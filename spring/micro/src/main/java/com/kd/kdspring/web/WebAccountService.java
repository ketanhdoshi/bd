package com.kd.kdspring.web;

import java.util.List;
import java.util.logging.Logger;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import com.kd.kdspring.account.Account;

public class WebAccountService {
    @Autowired
    private WebClient.Builder webClientBuilder;

    protected String serviceUrl;
    static final String TOKEN_PREFIX = "Bearer ";
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

    public Account findByNumber(String token, String accountNumber) {
        logger.info("findByNumber() invoked: for " + accountNumber);

        try {
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

    public List<Account> findAll(String token) {
        logger.info("findAll Accounts invoked");

        try {
            // REST call to back-end Account microservice
            List<Account> accounts = webClientBuilder.build().get()
                .uri(String.join("/", serviceUrl, "accounts"))
                .header(HttpHeaders.AUTHORIZATION, TOKEN_PREFIX + token)
                .retrieve()
                .bodyToFlux(Account.class)
                .collectList().block();
            return accounts;
        } catch (Exception e) {
            logger.severe(e.getClass() + ": " + e.getLocalizedMessage());
            return null;
        }
    }

    public Account create(String token, Account createAccount) {
        logger.info("create() invoked: for " + createAccount);

        try {
            // REST call to back-end Account microservice
            Mono<Account> accountMono = Mono.just(createAccount);
            Account account = webClientBuilder.build().post()
                .uri(String.join("/", serviceUrl, "accounts"))
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, TOKEN_PREFIX + token)
                .body(accountMono, Account.class)
                .retrieve()
                .bodyToMono(Account.class).block();
            return account;
        } catch (Exception e) {
            logger.severe(e.getClass() + ": " + e.getLocalizedMessage());
            return null;
        }
    }

    public Account update(String token, Account updateAccount, String accountNumber) {
        logger.info("update() invoked: for " + accountNumber);

        try {
            // REST call to back-end Account microservice
            Mono<Account> accountMono = Mono.just(updateAccount);
            Account account = webClientBuilder.build().put()
                .uri(String.join("/", serviceUrl, "accounts", accountNumber))
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, TOKEN_PREFIX + token)
                .body(accountMono, Account.class)
                .retrieve()
                .bodyToMono(Account.class).block();
            return account;
        } catch (Exception e) {
            logger.severe(e.getClass() + ": " + e.getLocalizedMessage());
            return null;
        }
    }

    public Boolean delete(String token, String accountNumber) {
        logger.info("delete() invoked: for " + accountNumber);

        try {
            // REST call to back-end Account microservice
            Boolean status = webClientBuilder.build().delete()
                .uri(String.join("/", serviceUrl, "accounts", accountNumber))
                .header(HttpHeaders.AUTHORIZATION, TOKEN_PREFIX + token)
                .exchangeToMono(response -> {
                    if (response.statusCode().equals(HttpStatus.OK)) {
                        return Mono.just(true);
                    } else if (response.statusCode().is4xxClientError()) {
                        return Mono.just(false);
                    } else {
                        return Mono.just(false);
                    }
                }).block();
            return status;
        } catch (Exception e) {
            logger.severe(e.getClass() + ": " + e.getLocalizedMessage());
            return false;
        }
    }
}
