package com.kd.kdspring.exception;

import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

/**
 * Allow the controller to return a 404 if a user is not found by simply
 * throwing this exception (and not handling it in the global handler). 
 * The @ResponseStatus causes Spring MVC to return a 404 instead of the usual 500.
 * 
 */
@ResponseStatus(value=HttpStatus.NOT_FOUND, reason="No such user")
public class UserNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public UserNotFoundException(String username) {
		super("No such user: " + username);
	}
}