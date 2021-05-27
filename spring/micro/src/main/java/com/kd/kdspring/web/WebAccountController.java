package com.kd.kdspring.web;

import java.util.List;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;

import com.kd.kdspring.account.Account;

// Figure out SecurityConfig how to use Authentication Entry Points
// Refactor the creation of JWT Token in Cookie in OauthAuthenticationSuccessHandler and JwtAuthenticationFilter
// In home.html, add one section where we use the saved JWT Authorization header from local storage to make another request with the JWT token (instead of relying on cookie)
// Do we need a JwtAuthenticationFilter that extends UsernamePasswordFilter, or can we just use a successHandler like we do for Oauth
// Figure out how to do a more advanced example with Authorization and Grant Authority etc.
// Make some better example out of the logic in the /kdtok and /oauthInfo methods below.
// Get rid of kdoauth folder
// Move the home, bootstrap, oauthInfo etc URLs into a separate controller

// ------------------------------------------
// Front-end controller for handling Account Web UI URLs for CRUD operations. Calls the 
// Front-end Service for Account, which then calls the back-end Account Microservice.
//
// This is the Controller for the microservices web frontend UI. All end-user calls
// come here and the don't access the microservices directly. This Controller displays the 
// web pages, and in turn makes calls to the backend microservices to fetch data.
// 
// All the CRUD methods here have the Authentication object for the currently authenticated user 
// automatically injected into them. They use that to obtain the user's JWT Token credentials, so 
// we can pass it to the backend microservice.
// ------------------------------------------
@Controller
public class WebAccountController {
    // Inject the Front-end Service for Account.
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

    /* @GetMapping("/kdtok")
    public String kdtok(Authentication authentication) {
        OAuth2AuthorizedClient authorizedClient =
            this.authorizedClientService.loadAuthorizedClient("github", authentication.getName());

        OAuth2AccessToken accessToken = authorizedClient.getAccessToken();

        return "kdtok";
    } */

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
    public String getAllAccounts(Authentication auth, Model model) {
        String token = auth.getCredentials().toString();

        // Call the front-end Account Service object which then calls the backend Account 
        // microservice to fetch all backend accounts
        List<Account> accounts = accountService.findAll(token);
        logger.info("Found all accounts" + accounts);

        model.addAttribute("appName", accounts);
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
    // The view populates an Account ModelAttribute object with user-inputted values from 
    // all the fields on the form
    // ------------------------------------------
    @PostMapping("/accounts")
    public String createAccount(Authentication auth, @ModelAttribute Account account, Model model) {
        // The JSON data from the HTTP request has been deserialised into a ModelAttribute Account object.

        // Get user's JWT Token credentials from the injected Authentication object, so we can pass 
        // it to the backend microservice
        String token = auth.getCredentials().toString();
        logger.info("web-service createAccount() invoked: " + account.getNumber() + account.getBalance());

        // Call the front-end Account Service object which then calls the backend Account 
        // microservice to create backend account
        account = accountService.create(token, account);
        logger.info("Created account: " + account);

        // Return a view name, which is in 'account.html'
        model.addAttribute("account", account);
        return "account";
    }

    // ------------------------------------------
    // Show the Account Details page, given the account number as part of the URL.
    // ------------------------------------------
    @GetMapping("/accounts/{accountNumber}")
    public String getAccount(Authentication auth, Model model, @PathVariable("accountNumber") String accountNumber) {
        // The URL request path contains the account number

        // Get user's JWT Token credentials from the injected Authentication object, so we can pass 
        // it to the backend microservice
        String token = auth.getCredentials().toString();
        
        // Use the number to get the account. It also gets added to the model
        Account account = byNumber(token, model, accountNumber);

        // Return a view name, which is in 'account.html'
        return "account";
    }

    // ------------------------------------------
    // Show the Edit Account Edit Form, given the account number as part of the URL.
    // ------------------------------------------
    @GetMapping("/accounts/{accountNumber}/edit")
    public String editForm(Authentication auth, Model model, @PathVariable("accountNumber") String accountNumber) {
        // The URL request path contains the account number

        // Get user's JWT Token credentials from the injected Authentication object, so we can pass 
        // it to the backend microservice
        String token = auth.getCredentials().toString();

        // Use the number to get the account. It also gets added to the model
        Account account = byNumber(token, model, accountNumber);

        // Return a view name, which is in 'accountedit.html'
        return "accountedit";
    }

    // ------------------------------------------
    // Handle the submit of the Account Edit form, given the account number as part of the URL.
    // The view populates an Account ModelAttribute object with user-inputted values from 
    // all the fields on the form
    // ------------------------------------------
    @PostMapping("/accounts/{accountNumber}")
    public String updateAccount(Authentication auth, @ModelAttribute Account updateAccount, Model model, @PathVariable("accountNumber") String accountNumber) {
        // The JSON data from the HTTP request has been deserialised into a ModelAttribute Account object.
        // The URL request path contains the account number

        // Get user's JWT Token credentials from the injected Authentication object, so we can pass 
        // it to the backend microservice
        String token = auth.getCredentials().toString();

        // Use the number to get the account. It also gets added to the model
        Account account = byNumber(token, model, accountNumber);

        // Call the front-end Account Service object which then calls the backend Account 
        // microservice to update the backend account
        account = accountService.update(token, updateAccount, accountNumber);
        logger.info("Updated account: " + account);

        // Return a view name, which is in 'account.html'
        model.addAttribute("account", account);
        return "account";
    }

    // ------------------------------------------
    // Delete the account with the given account number
    // ------------------------------------------
    @DeleteMapping("/accounts/{accountNumber}")
    public String deleteAccount(Authentication auth, Model model, @PathVariable("accountNumber") String accountNumber) {
        // The URL request path contains the account number

        // Get user's JWT Token credentials from the injected Authentication object, so we can pass 
        // it to the backend microservice
        String token = auth.getCredentials().toString();

        Account account = byNumber(token, model, accountNumber);

        // Call the front-end Account Service object which then calls the backend Account 
        // microservice to delete the backend account
        Boolean status = accountService.delete(token, accountNumber);
        String msg = "Delete account: " + (status ? "successful" : "unsuccessful");
        logger.info(msg);

        // Return a view name, which is in 'home.html'
        model.addAttribute("appName", msg);
        return "home";
    }

    // ------------------------------------------
    // Utility method to use the account number to fetch an account from the Account Service
    // which calls the backend microservice, . The returned Account object is added to the 
    // Model that gets attached to the view
    // ------------------------------------------
    private Account byNumber(String token, Model model, String accountNumber) {
        logger.info("web-service byNumber() invoked: " + accountNumber);

        // Fetch account from backend microservice
        Account account = accountService.findByNumber(token, accountNumber);
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
