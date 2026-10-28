package com.ubit.mentorshiphub.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An administrator's decision on one pending application.
 *
 * <p>The decision is its own two-value enum rather than the account's
 * {@code VerificationStatus}: {@code PENDING} is not a decision, so keeping it
 * out of the type means a request body can never carry it and the service never
 * has to reject it. The account-level status transition itself stays in
 * {@code UserService}, which is the single mutation point for it.
 *
 * <p>The note is what makes a rejection reviewable by the applicant. It is
 * required when rejecting - a silent rejection leaves the applicant unable to
 * fix anything - and optional when approving, because an approval needs no
 * explanation. The length bound matches the scale of the other free-text fields
 * in the project.
 *
 * @param decision     the outcome, {@code VERIFIED} or {@code REJECTED}
 * @param reviewerNote explanation; required and non-blank when rejecting
 */
public record AdminVerificationDecisionRequest(

        @NotNull(message = "Choose whether to verify or reject the application")
        Decision decision,

        @Size(max = 1000, message = "The review note must be 1000 characters or fewer")
        String reviewerNote
) {

    /** The only two outcomes an administrator may decide. */
    public enum Decision {
        VERIFIED,
        REJECTED
    }

    /**
     * The rejection note as it should be stored: trimmed, and {@code null} when
     * absent so an approval that carries no note leaves the field unset rather
     * than writing an empty string.
     */
    public String normalizedReviewerNote() {
        if (reviewerNote == null || reviewerNote.isBlank()) {
            return null;
        }
        return reviewerNote.trim();
    }

    /**
     * Whether this decision carries the note a rejection requires. Checked in the
     * service rather than with a conditional constraint, because bean validation
     * cannot express "required only when the other field equals REJECTED".
     */
    public boolean hasSufficientNote() {
        return decision != Decision.REJECTED || normalizedReviewerNote() != null;
    }
}
