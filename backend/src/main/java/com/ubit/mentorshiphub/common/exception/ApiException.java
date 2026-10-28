package com.ubit.mentorshiphub.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for expected, domain-level failures.
 *
 * <p>These carry the HTTP status they should surface as, so
 * {@link GlobalExceptionHandler} can translate them uniformly and none of them
 * reaches the catch-all 500 path. Anything thrown that is not an
 * {@code ApiException} is treated as a bug and logged with a stack trace.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    protected ApiException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
