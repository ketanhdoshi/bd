package com.kd.account;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

// ------------------------------------------
// Reactive custom entry point
//
// Return a 401 Uauthorized Error and error message to clients that try to access a protected resource 
// without proper authentication.
// Note that Spring's default EntryPoint also does return a HTTP 401 error, so this custom EntryPoint
// is not strictly necessary. It is here mostly as a learning example.
// ------------------------------------------
@Component
public class JwtServerAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

	// ------------------------------------------
	// Customise by overriding the 'commence' method
	// ------------------------------------------
	@Override
	public Mono<Void> commence(ServerWebExchange serverWebExchange, AuthenticationException ex) {
		// Get the Response object and set the HTTP status code and header using Reactive methods
		ServerHttpResponse response = serverWebExchange.getResponse();
		response.setStatusCode(HttpStatus.UNAUTHORIZED);
		response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

		String message = "Authentication Failed: ";

		// Get the error message
		if (ex.getCause() != null) {
			message += ex.getCause().toString() + " " + ex.getMessage();
		} else {
			message += ex.getMessage();
		}

		// This is the standard way to write the body of a Reactive Response
		// Create a DataBuffer from the bytes of the message string, and then
		// wrap that with a basic Reactive Mono.
		byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
		DataBuffer buffer = response.bufferFactory().wrap(bytes);
	  
		return response.writeWith(Mono.just(buffer));
	}

}