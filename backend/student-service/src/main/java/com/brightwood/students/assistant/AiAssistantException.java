package com.brightwood.students.assistant;

import org.springframework.http.HttpStatus;

public class AiAssistantException extends RuntimeException {

    private final HttpStatus status;

    public AiAssistantException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
