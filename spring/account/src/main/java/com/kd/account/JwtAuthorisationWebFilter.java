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

@Component
public class JwtAuthorisationWebFilter implements WebFilter {

    @Autowired
	private JwtUtil jwtTokenUtil;
 
    protected Logger logger = Logger.getLogger(JwtAuthorisationWebFilter.class.getName());

    @Override
    public Mono<Void> filter(ServerWebExchange serverWebExchange, WebFilterChain webFilterChain) {
        ServerHttpRequest request = serverWebExchange.getRequest();
        String jwtToken = extractJwtFromRequest(request);
        
        logger.info("JWT" + jwtToken);
        logger.info("Request {} called" + request.getPath().value());

        if (StringUtils.hasText(jwtToken) && jwtTokenUtil.validateToken(jwtToken)) {
            UserDetails userDetails = new User(jwtTokenUtil.getUsernameFromToken(jwtToken), "",
                    jwtTokenUtil.getRolesFromToken(jwtToken));

            UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                    userDetails, jwtToken, userDetails.getAuthorities());

            // After setting the Authentication in the context, we specify that the current user 
            // is authenticated. So it passes the Spring Security Configurations successfully.
            return webFilterChain.filter(serverWebExchange).contextWrite(
                ReactiveSecurityContextHolder.withAuthentication(usernamePasswordAuthenticationToken));
        } else {
            System.out.println("Cannot set the Security Context");
            return webFilterChain.filter(serverWebExchange);
        }  
    }

    // Extract the JWT token from the Authorization Header in the Request. The Header
	// says something like "Bearer: <token>"
	private String extractJwtFromRequest(ServerHttpRequest request) {
        String bearerToken = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
		if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
			return bearerToken.substring(7, bearerToken.length());
		}
		return null;
	}
}