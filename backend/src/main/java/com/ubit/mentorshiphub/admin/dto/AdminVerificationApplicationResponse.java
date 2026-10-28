package com.ubit.mentorshiphub.admin.dto;

import java.time.Instant;

import com.ubit.mentorshiphub.verification.model.ApplicationStatus;
import com.ubit.mentorshiphub.verification.model.ApplicationType;
import com.ubit.mentorshiphub.verification.model.Program;
import com.ubit.mentorshiphub.verification.model.StudentDocumentType;
import com.ubit.mentorshiphub.verification.model.VerificationApplication;

/**
 * One application as the administrator review queue shows it.
 *
 * <p>A deliberate projection rather than the entity: it carries everything a
 * reviewer needs to judge the claim against a university register - who filed,
 * what they claimed, when, which attempt this is, and where to open the proof -
 * and nothing else. In particular it never carries {@code googleId}, the
 * account's session or authentication internals, or the raw GridFS document id:
 * the proof is reached only through {@code documentUrl}, whose authorization is
 * re-checked on every request rather than conferred by holding an identifier.
 *
 * <p>The applicant's name and email are included because review is impossible
 * without identifying the person: an id alone cannot be matched against a
 * register. They are shown to administrators only, and never surface in the
 * profile, discovery or mentorship responses, which use their own projections.
 *
 * @param applicationId           id of this attempt
 * @param userId                  applicant's account id
 * @param applicantName           display name, for matching against the register
 * @param applicantEmail          Google-asserted address, for matching against the register
 * @param type                    student or alumni claim
 * @param claimedRole             role recorded at onboarding
 * @param status                  standing of this attempt
 * @param attemptNumber           1 for the first application, incremented on resubmission
 * @param submittedAt             when this attempt was made
 * @param program                 claimed programme, students only
 * @param currentSemester         claimed semester, students only
 * @param documentType            which proof was uploaded, students only
 * @param documentFilename        stored upload name, students only
 * @param passoutYear             claimed pass-out year, alumni only
 * @param linkedInUrl             supporting evidence, alumni only
 * @param decidedAt               when an administrator decided, null while pending
 * @param reviewerNote            administrator explanation, null while pending
 * @param documentUrl             where an administrator opens the proof; null when no document was uploaded
 */
public record AdminVerificationApplicationResponse(

        String applicationId,
        String userId,
        String applicantName,
        String applicantEmail,
        ApplicationType type,
        String claimedRole,
        ApplicationStatus status,
        int attemptNumber,
        Instant submittedAt,
        Program program,
        Integer currentSemester,
        StudentDocumentType documentType,
        String documentFilename,
        Integer passoutYear,
        String linkedInUrl,
        Instant decidedAt,
        String reviewerNote,
        String documentUrl
) {

    /**
     * Maps one stored application plus its applicant into the review projection.
     *
     * @param documentUrlTemplate a {@code %s} template for the document endpoint,
     *                            already scoped to this application id; null to
     *                            omit the link entirely
     */
    public static AdminVerificationApplicationResponse from(VerificationApplication application,
                                                            String applicantName,
                                                            String applicantEmail,
                                                            String documentUrlTemplate) {
        boolean hasDocument = application.getDocumentId() != null;
        return new AdminVerificationApplicationResponse(
                application.getId() == null ? null : application.getId().toHexString(),
                application.getUserId() == null ? null : application.getUserId().toHexString(),
                applicantName,
                applicantEmail,
                application.getType(),
                application.getClaimedRole(),
                application.getStatus(),
                application.getAttemptNumber(),
                application.getSubmittedAt(),
                application.getProgram(),
                application.getCurrentSemester(),
                application.getDocumentType(),
                application.getDocumentOriginalFilename(),
                application.getPassoutYear(),
                application.getLinkedInUrl(),
                application.getDecidedAt(),
                application.getReviewerNote(),
                hasDocument && documentUrlTemplate != null
                        ? documentUrlTemplate.formatted(application.getId().toHexString())
                        : null);
    }
}
