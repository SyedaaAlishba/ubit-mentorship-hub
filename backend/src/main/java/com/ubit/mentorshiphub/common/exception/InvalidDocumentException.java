package com.ubit.mentorshiphub.common.exception;

import org.springframework.http.HttpStatus;

/**
 * An uploaded verification document was rejected before it was stored: wrong
 * type, empty, oversized, or a filename/content mismatch.
 */
public class InvalidDocumentException extends ApiException {

    public InvalidDocumentException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }

    public InvalidDocumentException(String message, Throwable cause) {
        super(HttpStatus.BAD_REQUEST, message, cause);
    }
}
