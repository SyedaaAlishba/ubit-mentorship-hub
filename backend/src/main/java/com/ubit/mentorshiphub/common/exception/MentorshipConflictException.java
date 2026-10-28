package com.ubit.mentorshiphub.common.exception;

import org.springframework.http.HttpStatus;

/**
 * A mentorship action is impossible in the current state: a duplicate or reverse
 * pending request already exists, the pair already has an active mentorship, an
 * eligible recipient is required but the target is not one, or a request or
 * mentorship is in a terminal state that cannot transition again.
 *
 * <p>Mirrors {@link VerificationStateConflictException}: the request is well
 * formed and the caller is allowed to make it, but the stored state refuses it.
 */
public class MentorshipConflictException extends ApiException {

    public MentorshipConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
