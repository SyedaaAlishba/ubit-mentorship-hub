package com.ubit.mentorshiphub.common.exception;

import org.springframework.http.HttpStatus;

/**
 * A submitted field could not be understood: an unknown enum value, a missing
 * multipart field, or text where a number was expected.
 *
 * <p>Exists so that malformed input is a reported 400 rather than an unhandled
 * exception, and so the offending field can be named in the message.
 */
public class InvalidRequestException extends ApiException {

    public InvalidRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }

    public InvalidRequestException(String message, Throwable cause) {
        super(HttpStatus.BAD_REQUEST, message, cause);
    }
}
