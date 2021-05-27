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

// ------------------------------------------
// Front-end service object that uses Reactive Webflux to call the back-end Account Microservice.
//
// It makes Reactive Webflux calls to the backend Account microservice for all the CRUD APIs 
// and returns a non-reactive result because the front-end web pipeline is Servlet based.
// ------------------------------------------

public class WebAccountService {
    // Inject the Reactive WebClient
    @Autowired
    private WebClient.Builder webClientBuilder;

    protected String serviceUrl;
    static final String TOKEN_PREFIX = "Bearer ";
    protected Logger logger = Logger.getLogger(WebAccountService.class.getName());

    // ------------------------------------------
    // Initialise the service with the URL of the backend microservice
    // ------------------------------------------
    public WebAccountService(String serviceUrl) {
        this.serviceUrl = serviceUrl.startsWith("http") ? serviceUrl : "http://" + serviceUrl;
        this.serviceUrl += "/api";
    }

    // ------------------------------------------
    // Call the Account microservice 'root' URL - not very useful, mostly as a quick test.
    // ------------------------------------------
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

    // ------------------------------------------
    // REST call to back-end Account microservice to get an Account, given the account number
    // ------------------------------------------
    public Account findByNumber(String token, String accountNumber) {
        logger.info("findByNumber() invoked: for " + accountNumber);

        try {
            // All of the CRUD methods here follow a similar pattern:
            // Configure the WebClient with the microservice API URL and Authorizatio Header.
            // Then make the API call and return the response after serialising it to an Account object.
            // Finally we have to block to obtain a non-reactive result that we can pass
            // back to our caller which is Servlet-based.
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

    // ------------------------------------------
    // List all Accounts
    // ------------------------------------------
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

    // ------------------------------------------
    // Create an Account
    // ------------------------------------------
    public Account create(String token, Account createAccount) {
        logger.info("create() invoked: for " + createAccount);

        try {
            // Populate a Mono using the Account data from the POST body of the incoming request
            Mono<Account> accountMono = Mono.just(createAccount);

            // Call the backend API.
            Account account = webClientBuilder.build().post()
                .uri(String.join("/", serviceUrl, "accounts"))
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, TOKEN_PREFIX + token)
                // Set the request body to the Mono populated above.
                .body(accountMono, Account.class)
                .retrieve()
                .bodyToMono(Account.class).block();
            return account;
        } catch (Exception e) {
            logger.severe(e.getClass() + ": " + e.getLocalizedMessage());
            return null;
        }
    }

    // ------------------------------------------
    // Update an Account
    // ------------------------------------------
    public Account update(String token, Account updateAccount, String accountNumber) {
        logger.info("update() invoked: for " + accountNumber);

        try {
            // Populate a Mono using the Account data from the POST body of the incoming request
            Mono<Account> accountMono = Mono.just(updateAccount);

            // Call the backend API. 
            Account account = webClientBuilder.build().put()
                .uri(String.join("/", serviceUrl, "accounts", accountNumber))
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, TOKEN_PREFIX + token)
                // Set the request body to the Mono populated above.
                .body(accountMono, Account.class)
                .retrieve()
                .bodyToMono(Account.class).block();
            return account;
        } catch (Exception e) {
            logger.severe(e.getClass() + ": " + e.getLocalizedMessage());
            return null;
        }
    }

    // ------------------------------------------
    // Delete an Account
    // ------------------------------------------
    public Boolean delete(String token, String accountNumber) {
        logger.info("delete() invoked: for " + accountNumber);

        try {
            // REST call to back-end Account microservice
            
            Boolean status = webClientBuilder.build().delete()
                .uri(String.join("/", serviceUrl, "accounts", accountNumber))
                .header(HttpHeaders.AUTHORIZATION, TOKEN_PREFIX + token)
                // In the response from the REST API we get just the HTTP status code. Since we have to
                // generate a Mono from this, we populate a simple Mono with a boolean value based on the
                // status code.
                .exchangeToMono(response -> {
                    if (response.statusCode().equals(HttpStatus.OK)) {
                        // Success, return True Mono
                        return Mono.just(true);
                    } else if (response.statusCode().is4xxClientError()) {
                        // Authorization error, return False Mono
                        return Mono.just(false);
                    } else {
                        // Other error, return False Mono
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
