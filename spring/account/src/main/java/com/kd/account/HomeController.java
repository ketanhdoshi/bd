package com.kd.account;

import java.util.Collection;
import static java.util.stream.Collectors.joining;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;

// ------------------------------------------
// REST Controller for some path. Doesn't do anything useful, is there just for demo
// purposes.
// NB: May not work any more, need to update it to return a Reactive result
// ------------------------------------------

@RestController
public class HomeController {

	// Example showing the use of the @AuthenticationPrincipal annotation which injects the
	// current authenticated principal to any method
	@RequestMapping("/api")
	public String home(@AuthenticationPrincipal UserDetails userDetails) {
		// Get username from the Principal object
		String username = userDetails.getUsername();

		// Get the list of authorities from the Principal and convert to a comma-separated string
		String roles = "";
		Collection<? extends GrantedAuthority> authorities = userDetails.getAuthorities();
        if (!authorities.isEmpty()) {
			roles = authorities.stream().map(GrantedAuthority::getAuthority).collect(joining(","));
		}

		// Return a string by concatenating the username with the list of authorities.
		return "Accounts Home:" + username + "," + roles;
	}

}