package com.kd.account;

import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import reactor.core.publisher.Mono;

// ------------------------------------------
// After REST API clients are logged in (after an Authenticate API request), they can make requests.
// The JWT token is passed in as a Request Authorization Header with each request, which must 
// be validated, so that the request can be Authorised.
// 
// Reactive custom security filter to extract the JWT Token from the Authorization Header
// ------------------------------------------

@Component
public class JwtAuthorisationWebFilter implements WebFilter {

    // JWT Utilities
    @Autowired
	private JwtUtil jwtTokenUtil;
 
    protected Logger logger = Logger.getLogger(JwtAuthorisationWebFilter.class.getName());

    // ------------------------------------------
    // Customize by overriding the 'filter' method. Use Reactive objects for the Request pipeline
    // rather than MVC Servlet objects
    // ------------------------------------------
    @Override
    public Mono<Void> filter(ServerWebExchange serverWebExchange, WebFilterChain webFilterChain) {
        // Get the Request and extract the JWT token from the Request Header
        ServerHttpRequest request = serverWebExchange.getRequest();
        String jwtToken = extractJwtFromRequest(request);
        
        logger.info("JWT" + jwtToken);
        logger.info("Request {} called" + request.getPath().value());

        // Validate the JWT token
        if (StringUtils.hasText(jwtToken) && jwtTokenUtil.validateToken(jwtToken)) {
            // Create a User Details object using the username and roles from the token
            UserDetails userDetails = new User(jwtTokenUtil.getUsernameFromToken(jwtToken), "",
                    jwtTokenUtil.getRolesFromToken(jwtToken));

            // Create a Spring Authentication object with the three parameters:
            //      User Principal (ie. User Details object), Credentials (ie. token) and Authorities (ie. roles)
            UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                    userDetails, jwtToken, userDetails.getAuthorities());

            // After setting the Authentication in the Security Context, we specify that the current user 
            // is authenticated. So it passes the Spring Security Configurations successfully.
            return webFilterChain.filter(serverWebExchange).contextWrite(
                ReactiveSecurityContextHolder.withAuthentication(usernamePasswordAuthenticationToken));
        } else {
            // Token Validation failed
            System.out.println("Cannot set the Security Context");
            // Continue with the next filter in the chain
            return webFilterChain.filter(serverWebExchange);
        }  
    }

    // ------------------------------------------
    // Extract the JWT token from the Authorization Header in the Request. The Header
	// says something like "Bearer: <token>"
    // ------------------------------------------
	private String extractJwtFromRequest(ServerHttpRequest request) {
        // Get the Authorization Header from the list of Request Headers
        String bearerToken = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        // The Header is "Bearer: <JWT Token>". Skip the first part of the string
        // and return just the token.
		if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
			return bearerToken.substring(7, bearerToken.length());
		}
		return null;
	}
}