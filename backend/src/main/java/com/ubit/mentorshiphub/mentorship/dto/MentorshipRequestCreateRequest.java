package com.ubit.mentorshiphub.mentorship.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of a create-request call.
 *
 * <p>The requester is never taken from here: it is the authenticated principal.
 * Only the recipient and the two text fields are client-supplied.
 */
public record MentorshipRequestCreateRequest(
        @NotBlank String recipientUserId,
        @NotBlank @Size(min = 10, max = 2000) String message,
        @Size(max = 500) String goal
) {
}
