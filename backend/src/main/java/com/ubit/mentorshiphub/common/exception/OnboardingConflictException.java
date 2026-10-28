package com.ubit.mentorshiphub.common.exception;

import org.springframework.http.HttpStatus;

/** Onboarding has already been completed, or is not possible right now. */
public class OnboardingConflictException extends ApiException {

    public OnboardingConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
