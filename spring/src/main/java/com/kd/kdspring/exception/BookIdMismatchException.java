package com.kd.kdspring.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value=HttpStatus.BAD_REQUEST, reason="ID Mismatch")
public class BookIdMismatchException extends RuntimeException {

    // Not sure what it is, added to get rid of a warning
    private static final long serialVersionUID = 1L;

    public BookIdMismatchException() {
        super();
    }

    public BookIdMismatchException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public BookIdMismatchException(final String message) {
        super(message);
    }

    public BookIdMismatchException(final Throwable cause) {
        super(cause);
    }
}