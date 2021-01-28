package com.kd.kdspring.legacy;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

// Rest API, return data not web UI
@RestController
public class HelloController {

	// @RequestMapping maps /welcome to the index() method
	@RequestMapping("/welcome")
	public String index() {
		return "Greetings from Spring Boot!";
	}

}