package com.kd.kdspring.web.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.User;
import org.springframework.http.HttpHeaders;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import javax.servlet.FilterChain;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Collection;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MyJwtAuthenticationFilter extends UsernamePasswordAuthenticationFilter {
	
	// We use auth manager to validate the user credentials
	//private AuthenticationManager authManager;
    
	//public MyJwtAuthenticationFilter() {
		//this.authManager = authManager;
		
		// By default, UsernamePasswordAuthenticationFilter listens to "/login" path. 
		// In our case, we use "/auth". So, we need to override the defaults.
		// this.setRequiresAuthenticationRequestMatcher(new AntPathRequestMatcher(jwtConfig.getUri(), "POST"));
    //}
	
	// UsernamePasswordAuthenticationFilter (or its parent class actually) requires you to set the
	// AuthenticationManager explicitly like this.
    @Override
    @Autowired
    public void setAuthenticationManager(AuthenticationManager authenticationManager) {
        super.setAuthenticationManager(authenticationManager);
	}
	
	// Our custom User Details service which fetches user information from the database
	@Autowired
	private MyUserDetailsService userDetailsService;

	// Utility to generate JWT tokens
	@Autowired
	private JwtUtil jwtTokenUtil;
	
	@Override
	public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
			throws AuthenticationException {
		
		// try {
			
			// 1. Get credentials from request
			// This below is an example of reading JSON from the POST request
			// AuthRequest creds = new ObjectMapper().readValue(request.getInputStream(), AuthRequest.class);
			// Since the credentials are passed as form data not as JSON, read the form field values
            String username = request.getParameter("username");
            String password = request.getParameter("password");

			// 2. Create auth object (contains credentials) which will be used by auth manager
			UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
					username, password, new ArrayList<>());
			
            // 3. Authentication manager authenticate the user, and use UserDetialsServiceImpl::loadUserByUsername() method to load the user.
            AuthenticationManager authenticationManager = super.getAuthenticationManager();
			return authenticationManager.authenticate(authToken);
			
/* 		} catch (IOException e) {
			throw new RuntimeException(e);
		} */
	}
	
	// Upon successful authentication, generate a token.
	// The 'auth' passed to successfulAuthentication() is the current authenticated user.
	@Override
	protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
			Authentication auth) throws IOException, ServletException {

		// String username1 = ((User) auth.getPrincipal()).getUsername();
		String username = auth.getName();
		logger.info("successfulAuthentication: " + username);

		Collection<? extends GrantedAuthority> roles = auth.getAuthorities();

		//List<String> roles1 = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList());

		// Get the user info from the user database and generate a token for them
		//final UserDetails userDetails = userDetailsService.loadUserByUsername(username);
		// Collection<? extends GrantedAuthority> roles2 = userDetails.getAuthorities();

		final String token = jwtTokenUtil.createToken (roles, username);
		
		response.setStatus(HttpServletResponse.SC_OK);
		response.getWriter().write("Logged in successfully");
		// Return the token in the Authorization Response Header.
		String TOKEN_PREFIX = "Bearer ";
		response.addHeader(HttpHeaders.AUTHORIZATION, TOKEN_PREFIX + token);
	}
	
	// A (temporary) class just to represent the user credentials
	private static class UserCredentials {
	    private String username, password;
	    // getters and setters ...
	}
}
