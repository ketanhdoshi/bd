package com.kd.account;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;

// ------------------------------------------
// Configuration Settings for all Security in the app
// ------------------------------------------
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

	@Autowired
	private JwtAuthorisationWebFilter customJwtAuthorisationFilter;

	// ------------------------------------------
	// ------------------------------------------
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
		http
			.httpBasic().disable()
        	.formLogin().disable()
        	.csrf().disable()
        	.logout().disable()
			// Add a filter with Authorization on header   
			.addFilterAt(customJwtAuthorisationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
        	.authorizeExchange()
			.anyExchange().authenticated();
		
		return http.build();
    }
}