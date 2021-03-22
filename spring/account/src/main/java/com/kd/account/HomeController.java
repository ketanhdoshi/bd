package com.kd.account;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
public class HomeController {

	@RequestMapping("/api")
	public String home() {
		return "Accounts Home";
	}

}