package com.kd.kdspring.web;

import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;

import com.kd.kdspring.account.Account;

// ------------------------------------------
// This is the Controller for the microservices web frontend UI. All end-user calls
// come here and the don't access the microservices directly. This Controller displays the 
// web pages, and in turn makes calls to the backend microservices to fetch data.
// ------------------------------------------
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

    // ------------------------------------------
    // Show the Home page 
    // ------------------------------------------
    @RequestMapping("/")
    public String homePage(Model model) {
        model.addAttribute("appName", appName);
        // The view is defined in a 'home.html' page
        return "home";
    }

    // ------------------------------------------
    // Show a Bootstrap and Jquery page 
    // ------------------------------------------
    @RequestMapping("/js")
    public String jsPage(Model model) {
        model.addAttribute("appName", appName);
        // The view is defined in a 'js.html' page
        return "js";
    }

    // ------------------------------------------
    // Show info about the Oauth User
    // ------------------------------------------
    @RequestMapping("/oauthInfo")  
    public String securedPage(Model model,  
                              @RegisteredOAuth2AuthorizedClient("github") OAuth2AuthorizedClient authorizedClient,  
                              @AuthenticationPrincipal OAuth2User oauth2User) {  
        if (oauth2User != null) {
            model.addAttribute("userName", oauth2User.getName());
            model.addAttribute("userAttributes", oauth2User.getAttributes());        
        }
        model.addAttribute("clientName", authorizedClient.getClientRegistration().getClientName());  
        return "oauthInfo";  
    } 

    // ------------------------------------------
    // Show the Login page 
    // ------------------------------------------
    @RequestMapping("/login")
    public String logPage(Model model) {
        // The view is defined in a 'login.html' page
        return "login";
    }

    // ------------------------------------------
    // Show list of accounts 
    // !!!!!!right now it just shows a home page
    // ------------------------------------------
    @GetMapping("/accounts")
    public String getHome(Model model) {
        String retValue = accountService.getHome().getBody();
        model.addAttribute("appName", retValue);
        return "home";
    }

    // ------------------------------------------
    // Show the Account Create form
    // ------------------------------------------
    @GetMapping("/accounts/create")
    public String createForm(Model model) {
        // Attach a new Account object with the view. When the form is submitted
        // the view will populate all the form's field values into this object and
        // pass it to the POST handler method.
        model.addAttribute("account", new Account());
        // Return the name of the view, whose template is accountcreate.html
        return "accountcreate";
    }

    // ------------------------------------------
    // Handle the submit of the Account Create form. 
    // !!!!!!! Right now, it only receives submitted data but doesn't do anything with it
    // The view populates an Account ModelAttribute object with user-inputted values from 
    // all the fields on the form
    // ------------------------------------------
    @PostMapping("/accounts")
    public String createAccount(@ModelAttribute Account account, Model model) {
        logger.info("web-service createAccount() invoked: " + account.getNumber() + account.getBalance());

        // !!!!!!! Create backend account

        // !!!!!!! We should show a proper result form
        String retValue = accountService.getHome().getBody();
        model.addAttribute("appName", retValue);
        return "home";
    }

    // ------------------------------------------
    // Show the Account Details page, given the account number as part of the URL.
    // ------------------------------------------
    @GetMapping("/accounts/{accountNumber}")
    public String getAccount(Model model, @PathVariable("accountNumber") String accountNumber) {
        // Use the number to get the account. It also gets added to the model
        Account account = byNumber(model, accountNumber);

        // Return a view name, which is in 'account.html'
        return "account";
    }

    // ------------------------------------------
    // Show the Edit Account Edit Form, given the account number as part of the URL.
    // ------------------------------------------
    @GetMapping("/accounts/{accountNumber}/edit")
    public String editForm(Model model, @PathVariable("accountNumber") String accountNumber) {
        // Use the number to get the account. It also gets added to the model
        Account account = byNumber(model, accountNumber);

        // Return a view name, which is in 'accountedit.html'
        return "accountedit";
    }

    // ------------------------------------------
    // Handle the submit of the Account Edit form, given the account number as part of the URL.
    // The view populates an Account ModelAttribute object with user-inputted values from 
    // all the fields on the form
    // ------------------------------------------
    @PostMapping("/accounts/{accountNumber}")
    public String updateAccount(@ModelAttribute Account updateAccount, Model model, @PathVariable("accountNumber") String accountNumber) {
        Account account = byNumber(model, accountNumber);

        // !!!!!!! Update backend account

        // Return a view name, which is in 'account.html'
        return "account";
    }

    // ------------------------------------------
    // Utility method to fetch an account from the backend microservice, given 
    // its account number. The returned Account object is added to the Model that
    // gets attached to the view
    // ------------------------------------------
    private Account byNumber(Model model, String accountNumber) {
        logger.info("web-service byNumber() invoked: " + accountNumber);

        // Fetch account from backend microservice
        Account account = accountService.findByNumber(accountNumber);
        if (account == null) { // no such account
            model.addAttribute("number", accountNumber);
        } else {
            // Found the account, add that object to the model.
            logger.info("web-service byNumber() found: " + account);
            model.addAttribute("account", account);
        }

        return account;
    }
}
