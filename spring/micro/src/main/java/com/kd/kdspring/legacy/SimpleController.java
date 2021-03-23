package com.kd.kdspring.legacy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import java.sql.SQLException;

// Web UI, return a view
@Controller
public class SimpleController {

    @Value("${spring.application.name}")
    String appName;

    @RequestMapping("/")
    public String homePage(Model model) {
        // Add the application name into the model object so that it can be
        // accessed by the view template
        model.addAttribute("appName", appName);
        // The view is defined in a 'home.html' page
        return "home";
    }
    
	/**
	 * Simulates a database exception by always throwing <tt>SQLException</tt>.
	 * Must be caught by an exception handler.
	 * 
	 * @return Nothing - it always throws the exception.
	 * @throws SQLException
	 *             Always thrown.
	 */
	@RequestMapping("/databaseError1")
	String throwDatabaseException1() throws SQLException {
		//logger.info("Throw SQLException");
		throw new SQLException();
	}
}
