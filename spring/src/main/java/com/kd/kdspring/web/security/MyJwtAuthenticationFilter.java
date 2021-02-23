package com.kd.kdspring.web.security;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import javax.servlet.FilterChain;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.http.HttpHeaders;

public class MyJwtAuthenticationFilter extends UsernamePasswordAuthenticationFilter {
	
	// ------------------------------------------
	// UsernamePasswordAuthenticationFilter (or its parent class actually) requires you to set the
	// AuthenticationManager explicitly like this.
	// ------------------------------------------
	@Override
    @Autowired
    public void setAuthenticationManager(AuthenticationManager authenticationManager) {
        super.setAuthenticationManager(authenticationManager);
	}
	
	// Utility to generate JWT tokens
	@Autowired
	private JwtUtil jwtTokenUtil;
	
	// ------------------------------------------
	// ------------------------------------------
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
			
            // 3. Authentication manager authenticates the user, and use UserDetailsService.loadUserByUsername() method to load the user.
            AuthenticationManager authenticationManager = super.getAuthenticationManager();
			return authenticationManager.authenticate(authToken);
			
/* 		} catch (IOException e) {
			throw new RuntimeException(e);
		} */
	}
	
	// ------------------------------------------
	// Upon successful authentication, generate a token.
	// 'auth' is the current authenticated user.
	// ------------------------------------------
	@Override
	protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
			Authentication auth) throws IOException, ServletException {

		String username = auth.getName();
		Collection<? extends GrantedAuthority> roles = auth.getAuthorities();

		logger.info("successfulAuthentication: " + username);

		final String token = jwtTokenUtil.createToken (roles, username);
		
		response.setStatus(HttpServletResponse.SC_OK);
		response.getWriter().write("Logged in successfully");
		// Return the token in the Authorization Response Header.
		String TOKEN_PREFIX = "Bearer ";
		response.addHeader(HttpHeaders.AUTHORIZATION, TOKEN_PREFIX + token);
	}
}
