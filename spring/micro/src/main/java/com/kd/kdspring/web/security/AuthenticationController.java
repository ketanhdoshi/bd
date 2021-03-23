package com.kd.kdspring.web.security;

import java.util.logging.Logger;
import java.util.Collection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

// This is essentially the 'Login Service'
// Clients can 'login' by POSTing username/password to the /authenticate URL. Here we use 
// those credentials to authenticate the user and generate a JWT token which is returned 
// in the response header.
@RestController
public class AuthenticationController {

	protected Logger logger = Logger.getLogger(AuthenticationController.class.getName());

	// Spring's built-in Authentication Manager
	@Autowired
	private AuthenticationManager authenticationManager;

	// Utility to generate JWT tokens
	@Autowired
	private JwtUtil jwtTokenUtil;

	@PostMapping("/authenticate")
	public ResponseEntity<String> createAuthenticationToken(@RequestBody AuthRequest request)
			throws Exception {

		Authentication auth = null;
		try {
			// Authenticate with the Authentication Manager using the username/password
			// from the HTTP request
			auth = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
				request.getUsername(), request.getPassword()));
		} catch (DisabledException e) {
			// !!!!!!!!!
			throw new Exception("USER_DISABLED", e);
		} catch (BadCredentialsException e) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		String username = auth.getName();
		Collection<? extends GrantedAuthority> roles = auth.getAuthorities();

		final String token = jwtTokenUtil.createToken (roles, username);
		
		logger.info("createAuthenticationToken: " + username);

		// Return the token in the Authorization Response Header.
		return ResponseEntity.ok().header(HttpHeaders.AUTHORIZATION, token).body("Great");
	}

}