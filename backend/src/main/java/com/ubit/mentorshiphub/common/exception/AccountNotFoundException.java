package com.ubit.mentorshiphub.common.exception;

import org.springframework.http.HttpStatus;

/** No account exists for the identifier supplied by the current session. */
public class AccountNotFoundException extends ApiException {

    public AccountNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
