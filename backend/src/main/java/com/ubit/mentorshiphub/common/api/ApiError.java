package com.ubit.mentorshiphub.common.api;

import java.time.Instant;
import java.util.List;

/**
 * Uniform error payload returned by the REST API.
 *
 * @param timestamp  when the failure was produced
 * @param status     HTTP status code
 * @param error      HTTP reason phrase
 * @param message    short, safe description of the failure
 * @param path       request path that failed
 * @param violations per-field validation details, empty when not applicable
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldViolation> violations
) {

    public record FieldViolation(String field, String message) {
    }

    public static ApiError of(int status, String error, String message, String path,
                              List<FieldViolation> violations) {
        return new ApiError(Instant.now(), status, error, message, path,
                violations == null ? List.of() : List.copyOf(violations));
    }
}
