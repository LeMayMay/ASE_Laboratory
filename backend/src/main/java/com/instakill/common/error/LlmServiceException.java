package com.instakill.common.error;

import org.springframework.http.HttpStatus;

public class LlmServiceException extends InstakillException {

    private final HttpStatus status;

    public LlmServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public LlmServiceException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static LlmServiceException timeout(String message, Throwable cause) {
        return new LlmServiceException(HttpStatus.GATEWAY_TIMEOUT, message, cause);
    }

    public static LlmServiceException unavailable(String message, Throwable cause) {
        return new LlmServiceException(HttpStatus.SERVICE_UNAVAILABLE, message, cause);
    }

    public static LlmServiceException badGateway(String message) {
        return new LlmServiceException(HttpStatus.BAD_GATEWAY, message);
    }
}
