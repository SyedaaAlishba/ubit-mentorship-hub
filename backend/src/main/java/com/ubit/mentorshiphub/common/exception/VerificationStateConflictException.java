package com.ubit.mentorshiphub.common.exception;

import org.springframework.http.HttpStatus;

/** The requested verification transition is not legal from the current status. */
public class VerificationStateConflictException extends ApiException {

    public VerificationStateConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
