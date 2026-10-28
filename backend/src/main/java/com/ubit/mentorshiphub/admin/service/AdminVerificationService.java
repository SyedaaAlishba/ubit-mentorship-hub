package com.ubit.mentorshiphub.admin.service;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

import com.ubit.mentorshiphub.admin.dto.AdminVerificationApplicationResponse;
import com.ubit.mentorshiphub.admin.dto.AdminVerificationDecisionRequest;
import com.ubit.mentorshiphub.admin.dto.AdminVerificationDecisionRequest.Decision;
import com.ubit.mentorshiphub.admin.dto.AdminVerificationDecisionResponse;
import com.ubit.mentorshiphub.common.exception.InvalidRequestException;
import com.ubit.mentorshiphub.common.exception.ResourceNotFoundException;
import com.ubit.mentorshiphub.common.exception.VerificationStateConflictException;
import com.ubit.mentorshiphub.common.security.AccountAccess;
import com.ubit.mentorshiphub.common.security.AuthorizationGates;
import com.ubit.mentorshiphub.user.model.AppRole;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.user.model.VerificationStatus;
import com.ubit.mentorshiphub.user.service.UserService;
import com.ubit.mentorshiphub.verification.model.ApplicationStatus;
import com.ubit.mentorshiphub.verification.model.VerificationApplication;
import com.ubit.mentorshiphub.verification.repository.VerificationApplicationRepository;
import com.ubit.mentorshiphub.verification.storage.VerificationDocumentService;
import com.ubit.mentorshiphub.verification.storage.VerificationDocumentService.ReviewableDocument;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * The administrator verification queue: listing what awaits a decision, opening
 * a student's proof, and recording the decision itself.
 *
 * <p>Every entry point starts from {@code AccountAccess.requireCurrentUser()}
 * and the {@code ADMIN} role gate, in that order, so an anonymous caller is
 * refused before anything is loaded and a signed-in non-administrator is refused
 * before any application is read. The gate is the project's existing
 * {@link AuthorizationGates}; this service adds no authorization model of its
 * own.
 *
 * <p><b>Consistency without transactions.</b> A standalone {@code mongod} cannot
 * run multi-document transactions, so "the application's status and the
 * applicant's account status always agree" cannot be one atomic write. The
 * ordering makes the unavoidable window safe:
 * <ol>
 *   <li>the application is claimed with one <em>conditional</em> update whose
 *       criteria require {@code status = PENDING}, so exactly one administrator
 *       can flip it - a second concurrent decision matches nothing and is
 *       refused;</li>
 *   <li>only then is the account moved, through {@code UserService}, which
 *       re-checks that the account is still {@code PENDING}.</li>
 * </ol>
 * If step 2 refuses - which the guards make all but impossible, since nothing
 * else writes a pending account - the claim from step 1 is reverted, so the two
 * records never disagree. A crash between the steps leaves the application
 * decided and the account still pending: the fail-closed direction, where no
 * privilege is ever granted without the review record being committed first.
 *
 * <p>Documents are opened only through {@link VerificationDocumentService}, and
 * only after this service has resolved the application by its own id. A caller
 * never supplies a GridFS id, so a guessed identifier cannot reach another
 * application's file: the only id in play is the one that came from the
 * authenticated administrator's own request path.
 */
@Service
public class AdminVerificationService {

    /** Path template for the document endpoint, the only way proof is reached. */
    public static final String DOCUMENT_URL_TEMPLATE =
            "/api/admin/verification/applications/%s/document";

    private static final int MAX_PAGE_SIZE = 50;

    private final VerificationApplicationRepository applicationRepository;
    private final VerificationDocumentService documentService;
    private final UserService userService;
    private final AccountAccess accountAccess;
    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    public AdminVerificationService(VerificationApplicationRepository applicationRepository,
                                    VerificationDocumentService documentService,
                                    UserService userService,
                                    AccountAccess accountAccess,
                                    MongoTemplate mongoTemplate,
                                    Clock clock) {
        this.applicationRepository = applicationRepository;
        this.documentService = documentService;
        this.userService = userService;
        this.accountAccess = accountAccess;
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
    }

    /** A document opened for review, with the headers the endpoint should send. */
    public record DocumentDownload(InputStream content, String contentType,
                                   String filename, String contentDisposition, long sizeBytes) {
    }

    /**
     * One page of the review queue, oldest submission first.
     *
     * @param page   zero-based page index
     * @param size   page size, 1 to 50
     * @param status which standing to list; null or blank means {@code PENDING}
     * @throws com.ubit.mentorshiphub.common.exception.InsufficientRoleException if the caller is not an administrator
     * @throws InvalidRequestException if page, size or status is invalid
     */
    public Page<AdminVerificationApplicationResponse> listApplications(int page, int size, String status) {
        requireAdministrator();
        ApplicationStatus filter = parseStatus(status);
        if (page < 0) {
            throw new InvalidRequestException("page must be >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }

        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "submittedAt").and(Sort.by(Sort.Direction.ASC, "_id")));
        return applicationRepository.findByStatus(filter, pageable)
                .map(this::toReviewResponse);
    }

    /**
     * Opens the proof belonging to one application.
     *
     * <p>The application is resolved from the path id, and the stored document id
     * on <em>that</em> application is the only id handed to GridFS. There is no
     * client-supplied file id anywhere in the flow, so an application that holds
     * no document - every alumni claim - is a plain not-found, and one
     * application's file can never be reached through another's id.
     *
     * @throws com.ubit.mentorshiphub.common.exception.InsufficientRoleException if the caller is not an administrator
     * @throws ResourceNotFoundException if no such application exists, or it references no stored document
     */
    public DocumentDownload openDocument(String applicationId) {
        requireAdministrator();
        VerificationApplication application = requireApplication(applicationId);
        if (application.getDocumentId() == null) {
            throw new ResourceNotFoundException("This application has no uploaded document.");
        }

        ReviewableDocument document = documentService.openForReview(application.getDocumentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "The uploaded document for this application no longer exists."));

        boolean inline = VerificationDocumentService.isInlineDisplayable(document.contentType());
        String disposition = (inline ? "inline" : "attachment")
                + "; filename=\"" + sanitizeForHeader(document.filename()) + "\"";
        return new DocumentDownload(document.content(), document.contentType(),
                document.filename(), disposition, document.sizeBytes());
    }

    /**
     * Records a decision and moves the applicant's account to match.
     *
     * @throws com.ubit.mentorshiphub.common.exception.InsufficientRoleException if the caller is not an administrator
     * @throws ResourceNotFoundException if no such application exists
     * @throws InvalidRequestException if a rejection carries no note
     * @throws VerificationStateConflictException if the application is no longer pending, or the account is not
     */
    public AdminVerificationDecisionResponse decide(String applicationId,
                                                    AdminVerificationDecisionRequest request) {
        User administrator = requireAdministrator();
        VerificationApplication existing = requireApplication(applicationId);
        if (!existing.isPending()) {
            throw new VerificationStateConflictException(
                    "This application has already been decided. Current status: " + existing.getStatus() + ".");
        }
        if (!request.hasSufficientNote()) {
            throw new InvalidRequestException(
                    "Give a reason when rejecting an application, so the applicant knows what to correct.");
        }

        ObjectId target = existing.getId();
        ApplicationStatus applicationOutcome = request.decision() == Decision.VERIFIED
                ? ApplicationStatus.VERIFIED : ApplicationStatus.REJECTED;
        VerificationStatus accountOutcome = request.decision() == Decision.VERIFIED
                ? VerificationStatus.VERIFIED : VerificationStatus.REJECTED;

        VerificationApplication decided = claimPending(target, applicationOutcome,
                request.normalizedReviewerNote(), administrator.getId());

        // An application whose account no longer exists is left decided: there is
        // nothing left to contradict the review record, and re-opening it would
        // put an undecideable row back in the queue forever.
        User applicant = userService.requireById(decided.getUserId());

        try {
            userService.applyVerificationDecision(applicant, accountOutcome);
        } catch (VerificationStateConflictException ex) {
            // The account refused the transition, so the claim must not stand:
            // revert it, leaving the application exactly as it was found.
            revertClaim(target, applicationOutcome);
            throw ex;
        }

        return AdminVerificationDecisionResponse.from(decided, applicant);
    }

    /**
     * Flips one pending application to its decided state, but only if it is still
     * pending. The conditional criteria are the concurrency guard: of two
     * administrators deciding the same application, exactly one update matches.
     */
    private VerificationApplication claimPending(ObjectId applicationId,
                                                 ApplicationStatus outcome,
                                                 String reviewerNote,
                                                 ObjectId reviewerUserId) {
        Instant now = clock.instant();
        Query stillPending = new Query(Criteria.where("_id").is(applicationId)
                .and("status").is(ApplicationStatus.PENDING));
        Update update = new Update()
                .set("status", outcome)
                .set("decidedAt", now)
                .set("reviewerUserId", reviewerUserId);
        if (reviewerNote != null) {
            update.set("reviewerNote", reviewerNote);
        } else {
            update.unset("reviewerNote");
        }

        VerificationApplication claimed = mongoTemplate.findAndModify(stillPending, update,
                FindAndModifyOptions.options().returnNew(true), VerificationApplication.class);
        if (claimed == null) {
            throw new VerificationStateConflictException(
                    "This application is no longer pending; another administrator may have just decided it.");
        }
        return claimed;
    }

    /** Puts a claimed application back to pending after the account refused its transition. */
    private void revertClaim(ObjectId applicationId, ApplicationStatus outcome) {
        Query ours = new Query(Criteria.where("_id").is(applicationId)
                .and("status").is(outcome));
        mongoTemplate.updateFirst(ours, new Update()
                        .set("status", ApplicationStatus.PENDING)
                        .unset("decidedAt")
                        .unset("reviewerNote")
                        .unset("reviewerUserId"),
                VerificationApplication.class);
    }

    private User requireAdministrator() {
        return AuthorizationGates.requireRoles(accountAccess.requireCurrentUser(), AppRole.ADMIN);
    }

    private AdminVerificationApplicationResponse toReviewResponse(VerificationApplication application) {
        String name = null;
        String email = null;
        if (application.getUserId() != null) {
            User applicant = userService.findById(application.getUserId()).orElse(null);
            if (applicant != null) {
                name = applicant.getName();
                email = applicant.getEmail();
            }
        }
        return AdminVerificationApplicationResponse.from(application, name, email, DOCUMENT_URL_TEMPLATE);
    }

    private VerificationApplication requireApplication(String applicationId) {
        return applicationRepository.findById(parseApplicationId(applicationId))
                .orElseThrow(() -> new ResourceNotFoundException("Verification application not found."));
    }

    private static ObjectId parseApplicationId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResourceNotFoundException("Verification application not found.");
        }
        try {
            return new ObjectId(raw);
        } catch (IllegalArgumentException ex) {
            throw new ResourceNotFoundException("Verification application not found.");
        }
    }

    private static ApplicationStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return ApplicationStatus.PENDING;
        }
        try {
            return ApplicationStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("status must be one of: PENDING, VERIFIED, REJECTED");
        }
    }

    /**
     * Makes a stored filename safe for a {@code Content-Disposition} header.
     * The name was already sanitized on upload; this is the second pass, because
     * a header value must not carry a quote, a backslash or a control character
     * even if the stored value somehow does.
     */
    private static String sanitizeForHeader(String filename) {
        String cleaned = filename == null ? "" : filename.replaceAll("[\\p{Cntrl}\\\\\"]", "").trim();
        return cleaned.isEmpty() ? "verification-document" : cleaned;
    }
}
