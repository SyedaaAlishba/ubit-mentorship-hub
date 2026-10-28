package com.ubit.mentorshiphub.admin.dto;

import java.time.Instant;

import com.ubit.mentorshiphub.user.model.VerificationStatus;
import com.ubit.mentorshiphub.verification.model.ApplicationStatus;
import com.ubit.mentorshiphub.verification.model.VerificationApplication;
import com.ubit.mentorshiphub.user.model.User;

/**
 * The recorded outcome of one decision.
 *
 * <p>Carries both final states - the application's and the applicant's account
 * - so a caller can confirm the two agree without a second request, and so a
 * test can assert the consistency the service guarantees. It never carries the
 * applicant's email, Google id or any other account field beyond the resulting
 * verification status.
 *
 * @param applicationId         id of the decided application
 * @param userId                applicant's account id
 * @param applicationStatus     final standing of the application
 * @param userVerificationStatus the applicant's resulting account status
 * @param decidedAt             when the decision was recorded
 * @param reviewerNote          the stored note, or null when approving without one
 */
public record AdminVerificationDecisionResponse(

        String applicationId,
        String userId,
        ApplicationStatus applicationStatus,
        VerificationStatus userVerificationStatus,
        Instant decidedAt,
        String reviewerNote
) {

    public static AdminVerificationDecisionResponse from(VerificationApplication application, User applicant) {
        return new AdminVerificationDecisionResponse(
                application.getId() == null ? null : application.getId().toHexString(),
                application.getUserId() == null ? null : application.getUserId().toHexString(),
                application.getStatus(),
                applicant.getVerificationStatus(),
                application.getDecidedAt(),
                application.getReviewerNote());
    }
}
