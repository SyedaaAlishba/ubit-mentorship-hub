package com.ubit.mentorshiphub.common.exception;

import org.springframework.http.HttpStatus;

/**
 * The account has authenticated but has no role yet, so it may read its own
 * profile and onboard but nothing else.
 */
public class OnboardingRequiredException extends ApiException {

    public OnboardingRequiredException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
