package com.ubit.mentorshiphub.mentorship.dto;

import jakarta.validation.constraints.Size;

/**
 * Body of an end-mentorship call. The reason is optional; a blank value is
 * stored as absent.
 */
public record EndMentorshipRequest(
        @Size(max = 500) String reason
) {
}
