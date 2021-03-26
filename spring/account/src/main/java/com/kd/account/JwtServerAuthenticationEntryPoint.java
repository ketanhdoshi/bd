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

// Return a 401 Uauthorized Error and error message to clients that try to access a protected resource 
// without proper authentication.
// Note that Spring's default EntryPoint also does return a HTTP 401 error, so this custom EntryPoint
// is not strictly necessary. It is here mostly as a learning example.
@Component
public class JwtServerAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

	@Override
	public Mono<Void> commence(ServerWebExchange serverWebExchange, AuthenticationException ex) {
		ServerHttpResponse response = serverWebExchange.getResponse();
		response.setStatusCode(HttpStatus.UNAUTHORIZED);
		response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

		String message = "Authentication Failed: ";

		if (ex.getCause() != null) {
			message += ex.getCause().toString() + " " + ex.getMessage();
		} else {
			message += ex.getMessage();
		}

		byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
		DataBuffer buffer = response.bufferFactory().wrap(bytes);
	  
		return response.writeWith(Mono.just(buffer));
	}

}