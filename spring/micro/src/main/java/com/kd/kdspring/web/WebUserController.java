package com.kd.kdspring.web;

import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;

import com.kd.kdspring.user.UserInfo;

// ------------------------------------------
// This is the Controller for the microservices web frontend UI. All end-user calls
// come here and the don't access the microservices directly. This Controller displays the 
// web pages, and in turn makes calls to the backend microservices to fetch data.
// ------------------------------------------
@Controller
public class WebUserController {
    @Autowired
    protected WebUserService userService;

    protected Logger logger = Logger.getLogger(WebUserController.class.getName());

    @Value("${spring.application.name}")
    String appName;

    public WebUserController(WebUserService userService) {
        this.userService = userService;
    }

    // ------------------------------------------
    // Get a user's info given the username
    // ------------------------------------------
    @GetMapping("/users/{username}")
    public String getUser(Model model, @PathVariable("username") String username) {
        // Fetch user from backend microservice
        UserInfo user = userService.findByUsername(username);
        if (user == null) { // no such user
            model.addAttribute("username", username);
        } else {
            // Found the user, add that object to the model.
            logger.info("web-service byUsername() found: " + user);
            model.addAttribute("user", user);
        }

        model.addAttribute("appName", appName);
        return "home";
    }
}
