package com.kd.kdspring.account;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
public class HomeController {

	@RequestMapping("/")
	public String home() {
		return "Accounts Home";
	}

}