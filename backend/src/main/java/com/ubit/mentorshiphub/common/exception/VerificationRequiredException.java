package com.ubit.mentorshiphub.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Gate 1 failed: the account is authenticated and holds a role, but its claimed
 * student or alumni status has not been confirmed by an administrator.
 *
 * <p>Pending and rejected accounts are deliberately <em>not</em> blocked from
 * signing in or reading - they may resubmit an application and see why it was
 * declined. This exception guards community interaction only.
 */
public class VerificationRequiredException extends ApiException {

    public VerificationRequiredException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
