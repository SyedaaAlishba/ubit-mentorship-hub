package com.ubit.mentorshiphub.verification.service;

import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.util.Optional;

import com.ubit.mentorshiphub.common.exception.VerificationStateConflictException;
import com.ubit.mentorshiphub.user.model.AppRole;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.user.model.VerificationStatus;
import com.ubit.mentorshiphub.user.service.UserService;
import com.ubit.mentorshiphub.verification.dto.AlumniVerificationRequest;
import com.ubit.mentorshiphub.verification.dto.StudentVerificationRequest;
import com.ubit.mentorshiphub.verification.dto.VerificationApplicationResponse;
import com.ubit.mentorshiphub.verification.model.ApplicationStatus;
import com.ubit.mentorshiphub.verification.model.ApplicationType;
import com.ubit.mentorshiphub.verification.model.VerificationApplication;
import com.ubit.mentorshiphub.verification.repository.VerificationApplicationRepository;
import com.ubit.mentorshiphub.verification.storage.VerificationDocumentService;
import com.ubit.mentorshiphub.verification.storage.VerificationDocumentService.StoredDocument;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Owns the verification workflow: accepting a claim, storing its evidence and
 * moving the account to {@code PENDING}.
 *
 * <p>Two properties matter most here.
 *
 * <p><b>The submitted application type must match the role chosen at onboarding.</b>
 * A student may not file as an alumnus to avoid uploading a student card, so the
 * type is checked against the account's role rather than trusted.
 *
 * <p><b>The role is never touched here.</b> Approval and rejection both happen
 * elsewhere; what this service does is record a claim and park it. Because the
 * role is untouched, a rejection is non-destructive and the account keeps its
 * role, its history and its ability to resubmit.
 *
 * <p>Uploads are stored before the application row is written and cleaned up if
 * the write fails, so a failed submission cannot leave a 5 MB orphan behind.
 */
@Service
public class VerificationApplicationService {

    private final VerificationApplicationRepository applicationRepository;
    private final VerificationDocumentService documentService;
    private final UserService userService;
    private final Clock clock;

    public VerificationApplicationService(VerificationApplicationRepository applicationRepository,
                                          VerificationDocumentService documentService,
                                          UserService userService,
                                          Clock clock) {
        this.applicationRepository = applicationRepository;
        this.documentService = documentService;
        this.userService = userService;
        this.clock = clock;
    }

    /**
     * Records a student claim with its uploaded proof.
     *
     * @throws VerificationStateConflictException if the account has no role yet, is
     *                                           not a student, or already has an
     *                                           application in progress
     * @throws com.ubit.mentorshiphub.common.exception.InvalidDocumentException if the
     *                                                                    upload is unusable
     */
    public VerificationApplicationResponse submitStudent(User user, StudentVerificationRequest request,
                                                         MultipartFile document) {
        requireRoleMatches(user, ApplicationType.STUDENT);
        requireSubmittable(user);

        StoredDocument stored = documentService.store(user.getId(), document);
        try {
            VerificationApplication application = newApplication(user, ApplicationType.STUDENT);
            application.setProgram(request.program());
            application.setCurrentSemester(request.currentSemester());
            application.setDocumentType(request.documentType());
            application.setDocumentId(stored.id());
            application.setDocumentOriginalFilename(stored.originalFilename());
            application.setDocumentContentType(stored.contentType());
            application.setDocumentSizeBytes(stored.sizeBytes());
            return persistAndStart(user, application);
        } catch (RuntimeException ex) {
            documentService.deleteQuietly(stored.id());
            throw ex;
        }
    }

    /** Records an alumnus claim. Supporting evidence only; no document required. */
    public VerificationApplicationResponse submitAlumni(User user, AlumniVerificationRequest request) {
        requireRoleMatches(user, ApplicationType.ALUMNI);
        requireSubmittable(user);

        if (!request.isPassoutYearPlausible(Year.now().getValue())) {
            throw new VerificationStateConflictException(
                    "That passout year has not happened yet. Enter a year up to next year.");
        }

        VerificationApplication application = newApplication(user, ApplicationType.ALUMNI);
        application.setPassoutYear(request.passoutYear());
        application.setLinkedInUrl(request.linkedInUrl() == null ? null : request.linkedInUrl().trim());
        return persistAndStart(user, application);
    }

    /** The account's most recent application, for the status screen. */
    public Optional<VerificationApplicationResponse> currentApplication(User user) {
        return applicationRepository.findFirstByUserIdOrderBySubmittedAtDesc(user.getId())
                .map(VerificationApplicationResponse::from);
    }

    /**
     * Rejects a submission whose type disagrees with the account's role.
     *
     * <p>This is the control that makes the required document meaningful: without
     * it, the student document could simply be skipped by claiming alumnus status.
     */
    private void requireRoleMatches(User user, ApplicationType expected) {
        AppRole expectedRole = expected == ApplicationType.STUDENT ? AppRole.STUDENT : AppRole.ALUMNI;
        if (user.getRole() != expectedRole) {
            throw new VerificationStateConflictException(
                    ("Your account is registered as %s. Submit the matching application, or contact an administrator "
                            + "to change it.").formatted(user.getRole()));
        }
    }

    /**
     * Legal submission states: never applied ({@code UNVERIFIED}) or resubmitting
     * after a rejection ({@code REJECTED}). {@code PENDING} cannot be resubmitted
     * so a pending application cannot be quietly replaced, and {@code VERIFIED}
     * never regresses.
     */
    private void requireSubmittable(User user) {
        VerificationStatus status = user.getVerificationStatus();
        if (status != null && status.isApplicationStartable()) {
            return;
        }
        throw new VerificationStateConflictException(
                status == VerificationStatus.VERIFIED
                        ? "Your account is already verified. No further application is needed."
                        : "An application is already awaiting review. Wait for a decision before applying again.");
    }

    private VerificationApplication newApplication(User user, ApplicationType type) {
        Instant now = clock.instant();
        VerificationApplication application = new VerificationApplication();
        application.setUserId(user.getId());
        application.setType(type);
        application.setStatus(ApplicationStatus.PENDING);
        application.setSubmittedAt(now);
        application.setAttemptNumber(applicationRepository
                .findFirstByUserIdOrderBySubmittedAtDesc(user.getId())
                .map(VerificationApplication::getAttemptNumber)
                .orElse(0) + 1);
        // Snapshotted so a later role change cannot rewrite what was claimed.
        application.setClaimedRole(user.getRole() == null ? null : user.getRole().name());
        return application;
    }

    /**
     * Writes the application and moves the account to {@code PENDING}.
     *
     * <p>The account is moved only after the application is safely stored, so the
     * status never advertises a submission that does not exist.
     */
    private VerificationApplicationResponse persistAndStart(User user, VerificationApplication application) {
        VerificationApplication saved = applicationRepository.save(application);
        userService.startVerification(user);
        return VerificationApplicationResponse.from(saved);
    }
}
