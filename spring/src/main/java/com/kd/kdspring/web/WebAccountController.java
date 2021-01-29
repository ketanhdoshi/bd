package com.kd.kdspring.web;

import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;

import com.kd.kdspring.account.Account;

@Controller
public class WebAccountController {
    @Autowired
    protected WebAccountService accountService;

    protected Logger logger = Logger.getLogger(WebAccountController.class.getName());

    @Value("${spring.application.name}")
    String appName;

    public WebAccountController(WebAccountService accountService) {
        this.accountService = accountService;
    }

    @RequestMapping("/")
    public String homePage(Model model) {
        model.addAttribute("appName", appName);
        // The view is defined in a 'home.html' page
        return "home";
    }    

    @RequestMapping("/accounts")
    public String getHome(Model model) {
        String retValue = accountService.getHome().getBody();
        model.addAttribute("appName", retValue);
        return "home";
    }

    @RequestMapping("/accounts/{accountNumber}")
    public String byNumber(Model model, @PathVariable("accountNumber") String accountNumber) {

        logger.info("web-service byNumber() invoked: " + accountNumber);

        Account account = accountService.findByNumber(accountNumber);

        if (account == null) { // no such account
            model.addAttribute("number", accountNumber);
            // Return a view name, which is in 'account.html'
            return "account";
        }

        logger.info("web-service byNumber() found: " + account);
        model.addAttribute("account", account);
        // Return a view name, which is in 'account.html'
        return "account";
    }


}
