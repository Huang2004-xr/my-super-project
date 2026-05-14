package com.example.agentplatform.service.aiprovider;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

abstract class ProviderAdapterSupport {
    protected void applyAuthHeader(HttpHeaders headers, ProviderTestRequest request) {
        String headerName = defaulted(request.getAuthHeaderName(), "Authorization");
        String key = defaulted(request.getApiKey(), "");
        if ("Authorization".equalsIgnoreCase(headerName)) {
            headers.set(HttpHeaders.AUTHORIZATION, key.toLowerCase().startsWith("bearer ") ? key : "Bearer " + key);
        } else {
            headers.set(headerName, key);
        }
        if (request.getRequestId() != null && !request.getRequestId().trim().isEmpty()) {
            headers.set("X-Client-Request-Id", request.getRequestId());
        }
    }

    protected String endpoint(String baseUrl, String versionPath, String suffix) {
        String normalized = stripTrailingSlash(baseUrl);
        if (normalized.endsWith(suffix)) {
            return normalized;
        }
        if (normalized.endsWith(versionPath)) {
            return normalized + suffix;
        }
        return normalized + versionPath + suffix;
    }

    protected ProviderTestResult httpError(HttpStatusCodeException ex) {
        String body = normalize(ex.getResponseBodyAsString());
        String message = extractJsonString(body, "message");
        String param = extractJsonString(body, "param");
        String type = extractJsonString(body, "type");
        Integer status = ex.getRawStatusCode();
        String requestId = firstHeader(ex.getResponseHeaders(), "x-request-id", "request-id", "x-client-request-id");
        if (status == 401 || status == 403) {
            return ProviderTestResult.fail(ProviderErrorCode.AUTH_FAILED,
                    "authentication failed: check API Key and auth header", status, requestId);
        }
        if (status == 404) {
            return ProviderTestResult.fail(ProviderErrorCode.ENDPOINT_NOT_FOUND,
                    "endpoint not found: check API format and Base URL", status, requestId);
        }
        if (status == 429) {
            return ProviderTestResult.fail(ProviderErrorCode.RATE_LIMITED,
                    "provider rate limit or quota exceeded", status, requestId);
        }
        if (status == 529 || status == 503 || status == 502) {
            return ProviderTestResult.fail(ProviderErrorCode.OVERLOADED,
                    "provider is overloaded or temporarily unavailable", status, requestId);
        }
        if (looksLikeBalanceOrQuota(body, message)) {
            return ProviderTestResult.fail(ProviderErrorCode.BALANCE_OR_QUOTA,
                    "provider rejected request because balance or quota is insufficient", status, requestId);
        }
        if (looksLikeModelError(body, message, param)) {
            return ProviderTestResult.fail(ProviderErrorCode.MODEL_NOT_FOUND,
                    "model is not supported by this endpoint" + modelHint(param), status, requestId);
        }
        if (looksLikeProtocolMismatch(body, message, type)) {
            return ProviderTestResult.fail(ProviderErrorCode.PROTOCOL_MISMATCH,
                    "provider response indicates API protocol mismatch", status, requestId);
        }
        if (message != null) {
            return ProviderTestResult.fail(ProviderErrorCode.UNKNOWN,
                    "provider rejected request: HTTP " + status + ". " + message + (param == null ? "" : " (" + param + ")"),
                    status, requestId);
        }
        if (type != null) {
            return ProviderTestResult.fail(ProviderErrorCode.UNKNOWN,
                    "provider rejected request: HTTP " + status + ". " + type, status, requestId);
        }
        return ProviderTestResult.fail(ProviderErrorCode.UNKNOWN,
                "provider request failed: HTTP " + status + (body.isEmpty() ? "" : ". " + body),
                status, requestId);
    }

    protected ProviderTestResult clientError(RestClientException ex) {
        ProviderErrorCode code = ex instanceof ResourceAccessException
                ? ProviderErrorCode.TIMEOUT
                : ProviderErrorCode.UNKNOWN;
        return ProviderTestResult.fail(code, "connection test failed: " + normalize(ex.getMessage()));
    }

    protected String normalize(String text) {
        return text == null ? "" : text
                .replace("<EOL>", " ")
                .replace("\\n", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    protected String extractJsonString(String text, String key) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        Matcher matcher = Pattern
                .compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private boolean looksLikeModelError(String body, String message, String param) {
        String text = (body + " " + message + " " + param).toLowerCase();
        return text.contains("not supported model")
                || text.contains("model_not_found")
                || text.contains("model not found")
                || text.contains("invalid model")
                || text.contains("does not exist");
    }

    private boolean looksLikeProtocolMismatch(String body, String message, String type) {
        String text = (body + " " + message + " " + type).toLowerCase();
        return text.contains("unsupported endpoint")
                || text.contains("unsupported api")
                || text.contains("messages: invalid")
                || text.contains("chat completions")
                || text.contains("unknown parameter");
    }

    private boolean looksLikeBalanceOrQuota(String body, String message) {
        String text = (body + " " + message).toLowerCase();
        return text.contains("insufficient")
                || text.contains("balance")
                || text.contains("quota")
                || text.contains("credit")
                || text.contains("billing");
    }

    private String modelHint(String param) {
        if (param == null || param.trim().isEmpty()) {
            return "";
        }
        if (!param.equals(param.toLowerCase())) {
            return ": " + param + ". Check model id case; some providers require lowercase ids.";
        }
        return ": " + param;
    }

    private String firstHeader(HttpHeaders headers, String... names) {
        if (headers == null) {
            return null;
        }
        for (String name : names) {
            String value = headers.getFirst(name);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    protected String defaulted(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    protected String stripTrailingSlash(String value) {
        String cleaned = defaulted(value, "");
        while (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }
}
