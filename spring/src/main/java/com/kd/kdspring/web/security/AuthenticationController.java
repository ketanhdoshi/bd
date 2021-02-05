package com.kd.kdspring.web.security;

import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMethod;
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

	// Our custom User Details service which fetches user information from the database
	@Autowired
	private MyUserDetailsService userDetailsService;

	// Utility to generate JWT tokens
	@Autowired
	private JwtUtil jwtTokenUtil;

	@PostMapping("/authenticate")
	public ResponseEntity<String> createAuthenticationToken(@RequestBody AuthRequest request)
			throws Exception {
		try {
			// Authenticate with the Authentication Manager using the username/password
			// from the HTTP request
			authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
				request.getUsername(), request.getPassword()));
		} catch (DisabledException e) {
			// !!!!!!!!!
			throw new Exception("USER_DISABLED", e);
		} catch (BadCredentialsException e) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		// Get the user info from the user database and generate a token for them
		final UserDetails userDetails = userDetailsService.loadUserByUsername(request.getUsername());
		final String token = jwtTokenUtil.generateToken(userDetails);
		
		logger.info("createAuthenticationToken: " + request.getUsername());

		// Return the token in the Authorization Response Header.
		return ResponseEntity.ok().header(HttpHeaders.AUTHORIZATION, token).body("Great");
	}

}