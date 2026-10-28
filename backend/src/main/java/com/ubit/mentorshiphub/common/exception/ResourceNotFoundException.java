package com.ubit.mentorshiphub.common.exception;

import org.springframework.http.HttpStatus;

/** The request named a resource that does not exist, or is not visible to it. */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
