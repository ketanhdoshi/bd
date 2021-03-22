package com.kd.kdspring.web;

import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;

import com.kd.kdspring.book.Book;

// ------------------------------------------
// This is the Controller for the microservices web frontend UI. All end-user calls
// come here and the don't access the microservices directly. This Controller displays the 
// web pages, and in turn makes calls to the backend microservices to fetch data.
// ------------------------------------------
@Controller
public class WebLegacyController {
    @Autowired
    protected WebLegacyService legacyService;

    protected Logger logger = Logger.getLogger(WebLegacyController.class.getName());

    @Value("${spring.application.name}")
    String appName;

    public WebLegacyController(WebLegacyService legacyService) {
        this.legacyService = legacyService;
    }

    // ------------------------------------------
    // Get a book's info given the id
    // ------------------------------------------
    @GetMapping("/books/{id}")
    public String getBook(Model model, @PathVariable("id") String id) {
        // Fetch book from backend microservice
        Book book = legacyService.findBookById(id);
        if (book == null) { // no such book
            model.addAttribute("bookid", id);
        } else {
            // Found the book, add that object to the model.
            logger.info("web-service findBookById() found: " + book);
            model.addAttribute("book", book);
        }

        model.addAttribute("appName", appName);
        return "home";
    }
}
