package com.kd.kdspring.web;

import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.function.client.WebClient;

import com.kd.kdspring.user.UserInfo;

// ------------------------------------------
// Front-end service object that uses Reactive Webflux to call the back-end User Info Microservice.
// ------------------------------------------

public class WebUserService {
    // Inject the Reactive WebClient
    @Autowired
    private WebClient.Builder webClientBuilder;

    protected String serviceUrl;

    protected Logger logger = Logger.getLogger(WebUserService.class.getName());

    // ------------------------------------------
    // Initialise the service with the URL of the backend microservice
    // ------------------------------------------
    public WebUserService(String serviceUrl) {
        this.serviceUrl = serviceUrl.startsWith("http") ? serviceUrl : "http://" + serviceUrl;
        this.serviceUrl += "/api";
    }

    // ------------------------------------------
    // Make a Reactive Webflux call to the backend User Info microservice and return a
    // non-reactive result because the front-end web pipeline is Servlet based.
    // ------------------------------------------
    public UserInfo findByUsername(String username) {
        logger.info("findByUsername() invoked: for " + username);
        try {
            // REST call to back-end User microservice
            // Configure the WebClient with the microservice API URL, make the
            // call and return the response after serialising it to a UserInfo object.
            // Finally we have to block to obtain a non-reactive result that we can pass
            // back to our caller which is Servlet-based.
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
