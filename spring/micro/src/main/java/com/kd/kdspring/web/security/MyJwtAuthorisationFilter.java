package com.kd.kdspring.web.security;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Cookie;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

// ------------------------------------------
// After Web UI clients are logged in (through Form-based or Oauth), they can make requests.
// The JWT token is passed in as a Cookie with each request, which must be validated, so that
// the request can be Authorised.
// 
// Custom filter to intercept all HTTP requests so that we can extract the JWT token
// and validate it. Only if we have a valid token do we allow the request to go through.
// Otherwise we return a HTTP error.
// ------------------------------------------
@Component
public class MyJwtAuthorisationFilter extends OncePerRequestFilter {

	// Utility to generate and validate JWT tokens
	@Autowired
	private JwtUtil jwtTokenUtil;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {

		// JWT Token is in the form "Bearer token". Remove Bearer word and get  only the Token
		// String jwtToken = extractJwtFromRequest(request);
		Cookie cookie = WebUtils.getCookie(request, "jwt");
		String jwtToken = (cookie != null) ? cookie.getValue() : null;

		// Validate the token
		if (StringUtils.hasText(jwtToken) && jwtTokenUtil.validateToken(jwtToken)) {
			// Successfully validated. Get the username and roles from the token
			UserDetails userDetails = new User(jwtTokenUtil.getUsernameFromToken(jwtToken), "",
					jwtTokenUtil.getRolesFromToken(jwtToken));

			// The UsernamePasswordAuthenticationToken is a subclass of an Authentication object. Instantiate
			// an Authentication object using the Principal (userDetails), Credentials (jwtToken) and 
			// Authorities
			UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
					userDetails, jwtToken, userDetails.getAuthorities());

			// Set the Authentication object in the Security Context, to indicate that the 
			// current user is authenticated. This allows it to pass the Spring Security 
			// Configurations successfully.
			SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
		} else {
			// Token wasn't validated
			System.out.println("Cannot set the Security Context");
		}

		// Continue processing subsequent filters in the chain
		chain.doFilter(request, response);
	}

	// ------------------------------------------
	// Extract the JWT token from the Authorization Header in the Request. The Header
	// says something like "Bearer: <token>"
	// We aren't using this because the token is sent as a Cookie rather than as 
	// an Request Header
	// ------------------------------------------
	private String extractJwtFromRequest(HttpServletRequest request) {
		String bearerToken = request.getHeader("Authorization");
		if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
			return bearerToken.substring(7, bearerToken.length());
		}
		return null;
	}

}