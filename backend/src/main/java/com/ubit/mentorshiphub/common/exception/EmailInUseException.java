package com.ubit.mentorshiphub.common.exception;

import org.springframework.http.HttpStatus;

/**
 * The email address presented by Google is already claimed by a different
 * account. Signup is never blocked on a domain, but identity is still keyed on
 * {@code sub}, so two accounts must not end up sharing one address.
 */
public class EmailInUseException extends ApiException {

    public EmailInUseException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
