package com.example.agentplatform.dto;

import java.time.Instant;

public class AiProviderTestResponse {
    public boolean ok;
    public String status;
    public String message;
    public String errorCode;
    public Integer httpStatus;
    public String providerRequestId;
    public Instant testedAt;

    public AiProviderTestResponse() {
    }

    public AiProviderTestResponse(boolean ok, String status, String message, Instant testedAt) {
        this.ok = ok;
        this.status = status;
        this.message = message;
        this.testedAt = testedAt;
    }

    public AiProviderTestResponse(boolean ok, String status, String message, String errorCode,
            Integer httpStatus, String providerRequestId, Instant testedAt) {
        this.ok = ok;
        this.status = status;
        this.message = message;
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.providerRequestId = providerRequestId;
        this.testedAt = testedAt;
    }
}
