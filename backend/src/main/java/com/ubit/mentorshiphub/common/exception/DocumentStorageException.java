package com.ubit.mentorshiphub.common.exception;

import org.springframework.http.HttpStatus;

/**
 * GridFS could not store or retrieve a document. This is an infrastructure
 * failure, never a user-input problem, so it surfaces as a 500 and is logged
 * with its cause.
 */
public class DocumentStorageException extends ApiException {

    public DocumentStorageException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message, cause);
    }
}
