package com.kd.kdspring.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value=HttpStatus.NOT_FOUND, reason="Book not found")
public class BookNotFoundException extends RuntimeException {

    // Not sure what it is, added to get rid of a warning
    private static final long serialVersionUID = 1L;  

    public BookNotFoundException() {
        super();
    }

    public BookNotFoundException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public BookNotFoundException(final String message) {
        super(message);
    }

    public BookNotFoundException(final Throwable cause) {
        super(cause);
    }
}