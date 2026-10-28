package com.ubit.mentorshiphub.verification.dto;

import java.time.Instant;

import com.ubit.mentorshiphub.verification.model.ApplicationStatus;
import com.ubit.mentorshiphub.verification.model.ApplicationType;
import com.ubit.mentorshiphub.verification.model.Program;
import com.ubit.mentorshiphub.verification.model.StudentDocumentType;
import com.ubit.mentorshiphub.verification.model.VerificationApplication;

/**
 * The applicant's own application, as returned after a submission or when the
 * status screen polls it.
 *
 * <p>Always scoped to the signed-in account, and it never exposes the GridFS id:
 * the document is private and is readable only by an administrator through a
 * review screen, so there is no value in shipping the pointer to the client.
 *
 * @param applicationId    id of this attempt
 * @param type             student or alumnus claim
 * @param status           standing of this attempt
 * @param claimedRole      role recorded at onboarding, preserved across rejection
 * @param program          claimed programme, students only
 * @param currentSemester  claimed semester, students only
 * @param documentType     which proof was uploaded, students only
 * @param passoutYear      claimed passout year, alumni only
 * @param linkedInUrl      supporting evidence, alumni only
 * @param documentFilename stored upload name, for the applicant to recognise
 * @param attemptNumber    1 for the first application, incremented on resubmission
 * @param submittedAt      when this attempt was made
 * @param reviewerNote     administrator explanation, shown when rejected
 */
public record VerificationApplicationResponse(
        String applicationId,
        ApplicationType type,
        ApplicationStatus status,
        String claimedRole,
        Program program,
        Integer currentSemester,
        StudentDocumentType documentType,
        Integer passoutYear,
        String linkedInUrl,
        String documentFilename,
        int attemptNumber,
        Instant submittedAt,
        String reviewerNote
) {

    public static VerificationApplicationResponse from(VerificationApplication application) {
        return new VerificationApplicationResponse(
                application.getId() == null ? null : application.getId().toHexString(),
                application.getType(),
                application.getStatus(),
                application.getClaimedRole(),
                application.getProgram(),
                application.getCurrentSemester(),
                application.getDocumentType(),
                application.getPassoutYear(),
                application.getLinkedInUrl(),
                application.getDocumentOriginalFilename(),
                application.getAttemptNumber(),
                application.getSubmittedAt(),
                application.getReviewerNote());
    }
}
