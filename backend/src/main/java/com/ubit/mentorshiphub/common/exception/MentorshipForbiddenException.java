package com.ubit.mentorshiphub.common.exception;

import org.springframework.http.HttpStatus;

/**
 * The account is a participant in the resource but not the one the action
 * requires: only a request's recipient may accept or reject it, and only its
 * requester may cancel it.
 *
 * <p>Distinct from a plain "does not exist / is not yours" 404 so a participant
 * who may see the resource is told plainly that this particular action is not
 * theirs to take.
 */
public class MentorshipForbiddenException extends ApiException {

    public MentorshipForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
