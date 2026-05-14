package com.example.agentplatform.service.aiprovider;

public enum ProviderErrorCode {
    AUTH_FAILED,
    MODEL_NOT_FOUND,
    ENDPOINT_NOT_FOUND,
    RATE_LIMITED,
    OVERLOADED,
    TIMEOUT,
    PROTOCOL_MISMATCH,
    BALANCE_OR_QUOTA,
    UNKNOWN
}
