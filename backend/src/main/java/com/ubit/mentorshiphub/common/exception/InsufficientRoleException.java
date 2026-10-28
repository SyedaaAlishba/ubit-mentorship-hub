package com.ubit.mentorshiphub.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Gate 2 failed: the account is a verified participant, but its role does not
 * permit the requested action.
 *
 * <p>Distinct from {@link VerificationRequiredException} on purpose, so the API
 * can tell "come back once you are verified" apart from "your role does not
 * cover this".
 */
public class InsufficientRoleException extends ApiException {

    public InsufficientRoleException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
