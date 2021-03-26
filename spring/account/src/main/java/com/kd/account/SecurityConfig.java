package com.kd.account;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import reactor.core.publisher.Mono;

// ------------------------------------------
// Configuration Settings for all Security in the app
// ------------------------------------------
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

	@Autowired
	private JwtAuthorisationWebFilter customJwtAuthorisationFilter;

	@Autowired
	private JwtServerAuthenticationEntryPoint customJwtEntryPoint;

	// ------------------------------------------
	// ------------------------------------------
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
		http
			// Disable default Spring Security features such as HTTP Basic Auth, Form Login 
			// and Logout as we don't need them for our microservice. We use JWT token auth.
			.httpBasic().disable()
        	.formLogin().disable()
        	.csrf().disable()
        	.logout().disable()
			// Add a filter with Authorization on header   
			.addFilterAt(customJwtAuthorisationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
			// Custom Entrypoint for Authentication and Access Denied failures. These are not
			// really required, as Spring's default handling does what we need. But they are
			// here as a learning example. The example below shows two different ways to implement
			// the EntryPoint - one uses a custom class that extends the base EntryPoint and the
			// other just implements a simple anonymous function that sets a HTTP status code.
			.exceptionHandling()
				.authenticationEntryPoint(customJwtEntryPoint)
				.accessDeniedHandler((swe, e) -> Mono.fromRunnable(() -> {
					swe.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
				}))
			// All routes require authentication
			.and().authorizeExchange()
			.anyExchange().authenticated();
		
		return http.build();
    }
}