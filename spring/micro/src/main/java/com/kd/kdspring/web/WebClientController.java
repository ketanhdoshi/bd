package com.kd.kdspring.web;

import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.ui.Model;

import com.kd.kdspring.account.Account;

@Controller
public class WebClientController {
    @Autowired
    private WebClient.Builder webClientBuilder;

    protected Logger logger = Logger.getLogger(WebClientController.class.getName());

    public static final String ACCOUNT_SERVICE_URL = "http://ACCOUNT-SERVICE";
    private WebClient client = WebClient.create("https://jsonplaceholder.typicode.com");

    // ------------------------------------------
    // 
    // ------------------------------------------
    @GetMapping("/wc")
    public String getUser(Model model) {
        final String id = "2";
        String res = client.get()
            .uri(String.join("", "/users/", id))
            .retrieve()
            .bodyToMono(String.class).block();
            //.flatMap(res -> res.bodyToMono(String.class)).block();

        model.addAttribute("appName", res);
        return "home";
    }

    @GetMapping("/ac")
    public String findAc(Model model) {
        String accountNumber = "6648";
        logger.info("findAc() invoked: for " + accountNumber);
        try {
            // REST call to back-end Account microservice
            Account account = webClientBuilder.build().get()
                //.uri("http://ACCOUNT-SERVICE/accounts/{accountNumber}", accountNumber)
                .uri(String.join("/", ACCOUNT_SERVICE_URL, "accounts", accountNumber))
                //.uri("http://a.com:8080/path1", uri -> uri.queryParam("param1", "v1").build())
                .retrieve()
                .bodyToMono(Account.class).block();
 
            model.addAttribute("account", account);
        } catch (Exception e) {
            logger.severe(e.getClass() + ": " + e.getLocalizedMessage());
            model.addAttribute("number", accountNumber);
        }

        return "account";
    }
}