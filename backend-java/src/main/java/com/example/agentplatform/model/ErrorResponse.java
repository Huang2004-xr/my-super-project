package com.example.agentplatform.model;

import java.time.Instant;

public class ErrorResponse {
    public String code;
    public String message;
    public String requestId;
    public Instant timestamp;

    public ErrorResponse(String code, String message, String requestId) {
        this.code = code;
        this.message = message;
        this.requestId = requestId;
        this.timestamp = Instant.now();
    }
}
