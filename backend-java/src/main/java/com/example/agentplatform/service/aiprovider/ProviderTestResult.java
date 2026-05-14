package com.example.agentplatform.service.aiprovider;

public class ProviderTestResult {
    private final boolean ok;
    private final String message;
    private final ProviderErrorCode errorCode;
    private final Integer httpStatus;
    private final String providerRequestId;

    public ProviderTestResult(boolean ok, String message) {
        this(ok, message, null, null, null);
    }

    public ProviderTestResult(boolean ok, String message, ProviderErrorCode errorCode,
            Integer httpStatus, String providerRequestId) {
        this.ok = ok;
        this.message = message;
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.providerRequestId = providerRequestId;
    }

    public static ProviderTestResult ok(String message, String providerRequestId) {
        return new ProviderTestResult(true, message, null, null, providerRequestId);
    }

    public static ProviderTestResult fail(ProviderErrorCode errorCode, String message) {
        return new ProviderTestResult(false, message, errorCode, null, null);
    }

    public static ProviderTestResult fail(ProviderErrorCode errorCode, String message,
            Integer httpStatus, String providerRequestId) {
        return new ProviderTestResult(false, message, errorCode, httpStatus, providerRequestId);
    }

    public boolean isOk() { return ok; }
    public String getMessage() { return message; }
    public ProviderErrorCode getErrorCode() { return errorCode; }
    public Integer getHttpStatus() { return httpStatus; }
    public String getProviderRequestId() { return providerRequestId; }
}
