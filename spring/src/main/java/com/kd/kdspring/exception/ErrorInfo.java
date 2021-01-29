package com.kd.kdspring.exception;

import org.springframework.http.HttpStatus;

import lombok.Data;

@Data
public class ErrorInfo {
    private final HttpStatus status;
    private final String message;

    public ErrorInfo(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}