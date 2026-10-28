package com.ubit.mentorshiphub.mentorship.dto;

import java.time.Instant;

import com.ubit.mentorshiphub.mentorship.model.MentorshipRequestStatus;

/**
 * A mentorship request as its two participants see it.
 *
 * <p>{@code pairKey} is intentionally not exposed: it is an internal identity
 * used for duplicate protection, not part of the API contract.
 */
public record MentorshipRequestResponse(
        String id,
        MentorshipParticipantResponse requester,
        MentorshipParticipantResponse recipient,
        String message,
        String goal,
        MentorshipRequestStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant respondedAt
) {
}
