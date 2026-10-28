package com.ubit.mentorshiphub.mentorship.dto;

import java.time.Instant;

import com.ubit.mentorshiphub.mentorship.model.MentorshipStatus;

/**
 * An active or ended mentorship as one of its participants sees it.
 */
public record MentorshipResponse(
        String id,
        MentorshipParticipantResponse mentor,
        MentorshipParticipantResponse mentee,
        String requestId,
        MentorshipStatus status,
        Instant startedAt,
        Instant endedAt,
        String endedByUserId,
        String endReason,
        Instant createdAt,
        Instant updatedAt
) {
}
