package com.ubit.mentorshiphub.common.exception;

import org.springframework.http.HttpStatus;

/**
 * The account is disabled. Raised both when a disabled account tries to sign in
 * and when a live session is used, because disabling an account is meant to cut
 * off existing sessions as well as future ones.
 */
public class AccountDisabledException extends ApiException {

    public AccountDisabledException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
