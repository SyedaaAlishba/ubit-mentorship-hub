package com.ubit.mentorshiphub.mentorship.service;

import java.time.Clock;
import java.time.Instant;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.ubit.mentorshiphub.common.exception.InvalidRequestException;
import com.ubit.mentorshiphub.common.exception.MentorshipConflictException;
import com.ubit.mentorshiphub.common.exception.MentorshipForbiddenException;
import com.ubit.mentorshiphub.common.exception.ResourceNotFoundException;
import com.ubit.mentorshiphub.common.security.AccountAccess;
import com.ubit.mentorshiphub.common.security.AuthorizationGates;
import com.ubit.mentorshiphub.mentorship.dto.EndMentorshipRequest;
import com.ubit.mentorshiphub.mentorship.dto.MentorshipParticipantResponse;
import com.ubit.mentorshiphub.mentorship.dto.MentorshipRequestCreateRequest;
import com.ubit.mentorshiphub.mentorship.dto.MentorshipRequestResponse;
import com.ubit.mentorshiphub.mentorship.dto.MentorshipResponse;
import com.ubit.mentorshiphub.mentorship.model.Mentorship;
import com.ubit.mentorshiphub.mentorship.model.MentorshipRequest;
import com.ubit.mentorshiphub.mentorship.model.MentorshipRequestStatus;
import com.ubit.mentorshiphub.mentorship.model.MentorshipStatus;
import com.ubit.mentorshiphub.mentorship.model.PairKeys;
import com.ubit.mentorshiphub.mentorship.repository.MentorshipRepository;
import com.ubit.mentorshiphub.mentorship.repository.MentorshipRequestRepository;
import com.ubit.mentorshiphub.profile.model.Profile;
import com.ubit.mentorshiphub.profile.repository.ProfileRepository;
import com.ubit.mentorshiphub.user.model.AppRole;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.user.service.UserService;

/**
 * The mentorship workflow: a verified student asks a verified alumni or senior
 * to mentor them, the recipient answers, and an acceptance produces an active
 * mentorship.
 *
 * <h2>Correctness under concurrency</h2>
 *
 * <p>The configured deployment is a standalone {@code mongod}, which does
 * <em>not</em> support multi-document transactions, so acceptance cannot be a
 * single atomic write. Instead two <b>unique partial indexes</b> (created in
 * {@code MongoIndexInitializer}) carry the invariants the design cannot work
 * without:
 * <ul>
 *   <li>{@code (pairKey) where status in (PENDING, ACCEPTING)} on requests - at
 *       most one open request per pair, in either direction, including while an
 *       acceptance is in flight.</li>
 *   <li>{@code (pairKey) where status = ACTIVE} on mentorships - at most one
 *       active mentorship per pair.</li>
 * </ul>
 *
 * <p>Acceptance is a two-phase operation over the two collections, using an
 * explicit in-progress state rather than a compensating delete around a
 * completed one:
 * <ol>
 *   <li>claim the request with one conditional update, {@code PENDING ->
 *       ACCEPTING}. This is the single winner gate: a competing accept, reject
 *       or cancel updates {@code PENDING} and loses.</li>
 *   <li>insert the {@code ACTIVE} mentorship; the unique index rejects a second
 *       one.</li>
 *   <li>finalize with a conditional update, {@code ACCEPTING -> ACCEPTED}.</li>
 * </ol>
 * A crash between any two steps leaves the request in {@code ACCEPTING}, which
 * is the marker {@code MentorshipRecoveryService} looks for on the next startup:
 * if the mentorship exists the acceptance is completed, otherwise the request
 * is reopened to {@code PENDING}. Recovery never deletes a mentorship and is
 * idempotent. The one thing this cannot do is make the two writes atomic, so a
 * replica set with multi-document transactions would still be the stronger
 * deployment; the recovery path is the smallest correct fallback for a
 * standalone server.
 *
 * <p>Creating a request inserts first and only then re-checks for an active
 * mentorship, undoing the insert if one appeared meanwhile, so a request cannot
 * slip in beside a mentorship that acceptance committed concurrently.
 *
 * <p>Every state change re-derives the caller from {@link AccountAccess} and the
 * gate, never from the request body, and every eligibility rule is enforced here
 * rather than trusted to the controller or the UI.
 */
@Service
public class MentorshipService {

    private static final Logger log = LoggerFactory.getLogger(MentorshipService.class);

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MESSAGE_MIN = 10;
    private static final int MESSAGE_MAX = 2000;
    private static final int GOAL_MAX = 500;
    private static final int REASON_MAX = 500;

    private final MentorshipRequestRepository requestRepository;
    private final MentorshipRepository mentorshipRepository;
    private final UserService userService;
    private final ProfileRepository profileRepository;
    private final AccountAccess accountAccess;
    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    public MentorshipService(MentorshipRequestRepository requestRepository,
                             MentorshipRepository mentorshipRepository,
                             UserService userService,
                             ProfileRepository profileRepository,
                             AccountAccess accountAccess,
                             MongoTemplate mongoTemplate,
                             Clock clock) {
        this.requestRepository = requestRepository;
        this.mentorshipRepository = mentorshipRepository;
        this.userService = userService;
        this.profileRepository = profileRepository;
        this.accountAccess = accountAccess;
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- create

    /** A verified student asks a verified mentor for mentorship. */
    public MentorshipRequestResponse createRequest(MentorshipRequestCreateRequest req) {
        User requester = accountAccess.requireCurrentUser();
        AuthorizationGates.requireRoles(requester, AppRole.STUDENT);

        ObjectId recipientId = parseRecipientId(req.recipientUserId());
        if (recipientId.equals(requester.getId())) {
            throw new InvalidRequestException("You cannot send a mentorship request to yourself.");
        }
        String message = requireBounded(req.message(), "message", MESSAGE_MIN, MESSAGE_MAX);
        String goal = optionalBounded(req.goal(), "goal", GOAL_MAX);

        User recipient = userService.findById(recipientId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipient not found."));
        requireEligibleMentor(recipient);

        String pairKey = PairKeys.of(requester.getId(), recipientId);
        if (mentorshipRepository.existsByPairKeyAndStatus(pairKey, MentorshipStatus.ACTIVE)) {
            throw new MentorshipConflictException(
                    "You already have an active mentorship with this member.");
        }
        if (requestRepository.existsByPairKeyAndStatus(pairKey, MentorshipRequestStatus.PENDING)) {
            throw new MentorshipConflictException(
                    "A mentorship request between you and this member is already pending.");
        }

        Instant now = clock.instant();
        MentorshipRequest request = new MentorshipRequest();
        request.setRequesterUserId(requester.getId());
        request.setRecipientUserId(recipientId);
        request.setMessage(message);
        request.setGoal(goal);
        request.setStatus(MentorshipRequestStatus.PENDING);
        request.setPairKey(pairKey);
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        try {
            request = requestRepository.save(request);
        } catch (DuplicateKeyException ex) {
            // The unique partial index caught a concurrent duplicate or reverse
            // request, or a request being accepted, for the pair.
            throw new MentorshipConflictException(
                    "A mentorship request between you and this member is already pending.");
        }

        // The insert above is the hard guard for the duplicate rule; this second
        // check closes the window where an acceptance committed an active
        // mentorship between the pre-check and the insert. Because a successful
        // insert implies the pair held no open request for the acceptance to be
        // working from, an active mentorship can only appear here if it was
        // already committed, so undoing our insert is safe and complete.
        if (mentorshipRepository.existsByPairKeyAndStatus(pairKey, MentorshipStatus.ACTIVE)) {
            compensateDeleteRequest(request.getId());
            throw new MentorshipConflictException(
                    "You already have an active mentorship with this member.");
        }
        return toRequestResponse(request);
    }

    // ---------------------------------------------------------------- reads

    public Page<MentorshipRequestResponse> listSent(int page, int size) {
        User current = requireParticipant();
        return requestRepository
                .findByRequesterUserId(current.getId(), pageable(page, size, "createdAt"))
                .map(this::toRequestResponse);
    }

    public Page<MentorshipRequestResponse> listReceived(int page, int size) {
        User current = requireParticipant();
        return requestRepository
                .findByRecipientUserId(current.getId(), pageable(page, size, "createdAt"))
                .map(this::toRequestResponse);
    }

    public MentorshipRequestResponse getRequest(String requestId) {
        User current = requireParticipant();
        MentorshipRequest request = loadRequest(requestId);
        if (!isParticipant(request, current.getId())) {
            // An outsider is not told the request exists.
            throw new ResourceNotFoundException("Mentorship request not found.");
        }
        return toRequestResponse(request);
    }

    public Page<MentorshipResponse> listActive(int page, int size) {
        User current = requireParticipant();
        return mentorshipRepository
                .findActiveForUser(current.getId(), pageable(page, size, "startedAt"))
                .map(this::toMentorshipResponse);
    }

    // ---------------------------------------------------------------- lifecycle

    /** The recipient accepts; this is the only path that creates a mentorship. */
    public MentorshipResponse accept(String requestId) {
        User current = requireParticipant();
        MentorshipRequest request = loadRequest(requestId);
        requireRecipient(request, current.getId(), "Only the recipient can accept this request.");
        if (request.getStatus() != MentorshipRequestStatus.PENDING) {
            throw new MentorshipConflictException("This request is no longer pending and cannot be accepted.");
        }

        // Revalidate both accounts and the mentor's current availability (rule 4).
        User mentor = userService.findById(request.getRecipientUserId())
                .orElseThrow(() -> new MentorshipConflictException("This request can no longer be accepted."));
        requireEligibleMentor(mentor);
        User mentee = userService.findById(request.getRequesterUserId())
                .orElseThrow(() -> new MentorshipConflictException("This request can no longer be accepted."));
        if (!mentee.isEnabled() || !AuthorizationGates.isVerifiedParticipant(mentee)
                || mentee.getRole() != AppRole.STUDENT) {
            throw new MentorshipConflictException("This request can no longer be accepted.");
        }

        String pairKey = pairKeyOf(request);
        if (mentorshipRepository.existsByPairKeyAndStatus(pairKey, MentorshipStatus.ACTIVE)) {
            throw new MentorshipConflictException("This pair already has an active mentorship.");
        }

        Instant now = clock.instant();

        // Phase 1: claim the request. This conditional PENDING -> ACCEPTING update
        // is the single winner gate; a competing accept, reject or cancel that
        // targets PENDING loses here, so the mentorship below is written at most
        // once per request.
        if (!flipRequest(request.getId(), MentorshipRequestStatus.PENDING,
                MentorshipRequestStatus.ACCEPTING, now, false)) {
            throw new MentorshipConflictException("This request is no longer pending and cannot be accepted.");
        }

        // Phase 2: write the active mentorship. The unique partial index on the
        // active pair is the backstop if a previous interrupted acceptance left one
        // behind (or two accepts raced past phase 1 before either claimed the pair).
        Mentorship mentorship = new Mentorship();
        mentorship.setMentorUserId(request.getRecipientUserId());
        mentorship.setMenteeUserId(request.getRequesterUserId());
        mentorship.setRequestId(request.getId());
        mentorship.setStatus(MentorshipStatus.ACTIVE);
        mentorship.setPairKey(pairKey);
        mentorship.setStartedAt(now);
        mentorship.setCreatedAt(now);
        mentorship.setUpdatedAt(now);
        try {
            mentorship = mentorshipRepository.save(mentorship);
        } catch (DuplicateKeyException ex) {
            // The pair already has an active mentorship: either it committed
            // between the pre-check and this insert, or a prior interrupted
            // acceptance left one. Never leave a PENDING request beside it.
            Mentorship existing = mentorshipRepository
                    .findByPairKeyAndStatus(pairKey, MentorshipStatus.ACTIVE)
                    .orElse(null);
            if (existing != null) {
                // The pair is actively mentored and our claim still holds, so
                // finalize consistently against the existing mentorship.
                flipRequest(request.getId(), MentorshipRequestStatus.ACCEPTING,
                        MentorshipRequestStatus.ACCEPTED, now, true);
                return toMentorshipResponse(existing);
            }
            // No active mentorship is visible; reopen the invitation for retry.
            flipRequest(request.getId(), MentorshipRequestStatus.ACCEPTING,
                    MentorshipRequestStatus.PENDING, now, false);
            throw new MentorshipConflictException("This pair already has an active mentorship.");
        }

        // Phase 3: finalize. If this loses, the request stays ACCEPTING and the
        // mentorship exists; MentorshipRecoveryService completes it on the next
        // startup rather than deleting a legitimate mentorship here.
        if (!flipRequest(request.getId(), MentorshipRequestStatus.ACCEPTING,
                MentorshipRequestStatus.ACCEPTED, now, true)) {
            throw new MentorshipConflictException("This request could not be finalized and will be repaired on restart.");
        }
        return toMentorshipResponse(mentorship);
    }

    /** The recipient declines a pending request. */
    public MentorshipRequestResponse reject(String requestId) {
        User current = requireParticipant();
        MentorshipRequest request = loadRequest(requestId);
        requireRecipient(request, current.getId(), "Only the recipient can reject this request.");
        if (request.getStatus() != MentorshipRequestStatus.PENDING) {
            throw new MentorshipConflictException("This request is no longer pending and cannot be rejected.");
        }
        Instant now = clock.instant();
        boolean flipped = flipRequest(request.getId(),
                MentorshipRequestStatus.PENDING, MentorshipRequestStatus.REJECTED, now, true);
        if (!flipped) {
            throw new MentorshipConflictException("This request is no longer pending and cannot be rejected.");
        }
        request.setStatus(MentorshipRequestStatus.REJECTED);
        request.setRespondedAt(now);
        request.setUpdatedAt(now);
        return toRequestResponse(request);
    }

    /** The requester withdraws a pending request. */
    public MentorshipRequestResponse cancel(String requestId) {
        User current = requireParticipant();
        MentorshipRequest request = loadRequest(requestId);
        requireRequester(request, current.getId(), "Only the requester can cancel this request.");
        if (request.getStatus() != MentorshipRequestStatus.PENDING) {
            throw new MentorshipConflictException("This request is no longer pending and cannot be cancelled.");
        }
        Instant now = clock.instant();
        boolean flipped = flipRequest(request.getId(),
                MentorshipRequestStatus.PENDING, MentorshipRequestStatus.CANCELLED, now, true);
        if (!flipped) {
            throw new MentorshipConflictException("This request is no longer pending and cannot be cancelled.");
        }
        request.setStatus(MentorshipRequestStatus.CANCELLED);
        request.setRespondedAt(now);
        request.setUpdatedAt(now);
        return toRequestResponse(request);
    }

    /** Either participant ends an active mentorship. */
    public MentorshipResponse end(String mentorshipId, EndMentorshipRequest req) {
        User current = requireParticipant();
        ObjectId id = parsePathId(mentorshipId, "Mentorship not found.");
        Mentorship mentorship = mentorshipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mentorship not found."));
        if (!isMentorshipParticipant(mentorship, current.getId())) {
            throw new ResourceNotFoundException("Mentorship not found.");
        }
        if (mentorship.getStatus() != MentorshipStatus.ACTIVE) {
            throw new MentorshipConflictException("This mentorship has already ended.");
        }
        String reason = req == null ? null : optionalBounded(req.reason(), "reason", REASON_MAX);

        Instant now = clock.instant();
        Query query = Query.query(Criteria.where("_id").is(id)
                .and("status").is(MentorshipStatus.ACTIVE));
        Update update = new Update()
                .set("status", MentorshipStatus.ENDED)
                .set("endedAt", now)
                .set("endedByUserId", current.getId())
                .set("updatedAt", now);
        if (reason != null) {
            update.set("endReason", reason);
        }
        boolean ended = mongoTemplate.updateFirst(query, update, Mentorship.class).getModifiedCount() == 1;
        if (!ended) {
            throw new MentorshipConflictException("This mentorship has already ended.");
        }
        mentorship.setStatus(MentorshipStatus.ENDED);
        mentorship.setEndedAt(now);
        mentorship.setEndedByUserId(current.getId());
        mentorship.setEndReason(reason);
        mentorship.setUpdatedAt(now);
        return toMentorshipResponse(mentorship);
    }

    // ---------------------------------------------------------------- guards

    private User requireParticipant() {
        User current = accountAccess.requireCurrentUser();
        AuthorizationGates.requireVerifiedParticipant(current);
        return current;
    }

    /**
     * The target must be an enabled, verified alumni or senior whose profile has
     * opted in. A missing legacy {@code mentorshipAvailable} field reads as
     * {@code null}, which fails closed exactly like {@code false}.
     */
    private void requireEligibleMentor(User recipient) {
        boolean hasMentorRole = recipient.getRole() == AppRole.ALUMNI || recipient.getRole() == AppRole.SENIOR;
        if (!recipient.isEnabled() || !recipient.isVerified() || !hasMentorRole) {
            throw new MentorshipConflictException("This member is not available to mentor.");
        }
        boolean available = profileRepository.findByUserId(recipient.getId())
                .map(Profile::getMentorshipAvailable)
                .map(Boolean.TRUE::equals)
                .orElse(false);
        if (!available) {
            throw new MentorshipConflictException(
                    "This member is not currently accepting mentorship requests.");
        }
    }

    private MentorshipRequest loadRequest(String requestId) {
        ObjectId id = parsePathId(requestId, "Mentorship request not found.");
        return requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mentorship request not found."));
    }

    private void requireRecipient(MentorshipRequest request, ObjectId userId, String forbidden) {
        if (userId.equals(request.getRecipientUserId())) {
            return;
        }
        if (isParticipant(request, userId)) {
            throw new MentorshipForbiddenException(forbidden);
        }
        throw new ResourceNotFoundException("Mentorship request not found.");
    }

    private void requireRequester(MentorshipRequest request, ObjectId userId, String forbidden) {
        if (userId.equals(request.getRequesterUserId())) {
            return;
        }
        if (isParticipant(request, userId)) {
            throw new MentorshipForbiddenException(forbidden);
        }
        throw new ResourceNotFoundException("Mentorship request not found.");
    }

    private boolean isParticipant(MentorshipRequest request, ObjectId userId) {
        return userId.equals(request.getRequesterUserId()) || userId.equals(request.getRecipientUserId());
    }

    private boolean isMentorshipParticipant(Mentorship mentorship, ObjectId userId) {
        return userId.equals(mentorship.getMentorUserId()) || userId.equals(mentorship.getMenteeUserId());
    }

    private String pairKeyOf(MentorshipRequest request) {
        return request.getPairKey() != null
                ? request.getPairKey()
                : PairKeys.of(request.getRequesterUserId(), request.getRecipientUserId());
    }

    // ---------------------------------------------------------------- persistence helpers

    /**
     * Moves a request out of {@code expected} only if it is still there, in one
     * conditional update. Returns whether a document was modified, which is the
     * signal that this caller won the race. {@code responded} stamps
     * {@code respondedAt} for transitions the recipient or requester drove; the
     * in-flight {@code ACCEPTING} move instead records {@code acceptStartedAt}.
     */
    private boolean flipRequest(ObjectId id, MentorshipRequestStatus expected,
                                MentorshipRequestStatus next, Instant now, boolean responded) {
        Query query = Query.query(Criteria.where("_id").is(id).and("status").is(expected));
        Update update = new Update()
                .set("status", next)
                .set("updatedAt", now);
        if (responded) {
            update.set("respondedAt", now);
        }
        if (next == MentorshipRequestStatus.ACCEPTING) {
            update.set("acceptStartedAt", now);
        }
        return mongoTemplate.updateFirst(query, update, MentorshipRequest.class).getModifiedCount() == 1;
    }

    /**
     * Best-effort removal of a request whose insert lost to an active mentorship
     * that committed between the pre-check and the insert. A failure to delete is
     * logged rather than masking the original conflict.
     */
    private void compensateDeleteRequest(ObjectId requestId) {
        try {
            requestRepository.deleteById(requestId);
        } catch (RuntimeException ex) {
            log.error("Could not remove request {} that collided with an active mentorship; "
                    + "manual reconciliation may be required.", requestId, ex);
        }
    }

    private Pageable pageable(int page, int size, String sortField) {
        if (page < 0) {
            throw new InvalidRequestException("page must be >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, sortField).and(Sort.by(Sort.Direction.DESC, "id")));
    }

    // ---------------------------------------------------------------- parsing and text

    private ObjectId parseRecipientId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidRequestException("recipientUserId is required.");
        }
        try {
            return new ObjectId(raw.trim());
        } catch (RuntimeException ex) {
            throw new InvalidRequestException("recipientUserId is not a valid id.");
        }
    }

    private ObjectId parsePathId(String raw, String notFoundMessage) {
        if (raw == null || raw.isBlank()) {
            throw new ResourceNotFoundException(notFoundMessage);
        }
        try {
            return new ObjectId(raw.trim());
        } catch (RuntimeException ex) {
            throw new ResourceNotFoundException(notFoundMessage);
        }
    }

    private String requireBounded(String value, String field, int min, int max) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() < min) {
            throw new InvalidRequestException(field + " must be between " + min + " and " + max + " characters.");
        }
        if (trimmed.length() > max) {
            throw new InvalidRequestException(field + " must be between " + min + " and " + max + " characters.");
        }
        return trimmed;
    }

    private String optionalBounded(String value, String field, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > max) {
            throw new InvalidRequestException(field + " must be at most " + max + " characters.");
        }
        return trimmed;
    }

    // ---------------------------------------------------------------- mapping

    private MentorshipRequestResponse toRequestResponse(MentorshipRequest request) {
        return new MentorshipRequestResponse(
                request.getId().toHexString(),
                participant(request.getRequesterUserId()),
                participant(request.getRecipientUserId()),
                request.getMessage(),
                request.getGoal(),
                request.getStatus(),
                request.getCreatedAt(),
                request.getUpdatedAt(),
                request.getRespondedAt());
    }

    private MentorshipResponse toMentorshipResponse(Mentorship mentorship) {
        return new MentorshipResponse(
                mentorship.getId().toHexString(),
                participant(mentorship.getMentorUserId()),
                participant(mentorship.getMenteeUserId()),
                mentorship.getRequestId() == null ? null : mentorship.getRequestId().toHexString(),
                mentorship.getStatus(),
                mentorship.getStartedAt(),
                mentorship.getEndedAt(),
                mentorship.getEndedByUserId() == null ? null : mentorship.getEndedByUserId().toHexString(),
                mentorship.getEndReason(),
                mentorship.getCreatedAt(),
                mentorship.getUpdatedAt());
    }

    private MentorshipParticipantResponse participant(ObjectId userId) {
        User user = userService.findById(userId).orElse(null);
        if (user == null) {
            return new MentorshipParticipantResponse(userId.toHexString(), "Unknown", null, null);
        }
        String avatar = profileRepository.findByUserId(userId)
                .map(Profile::getAvatarGridFsId)
                .orElse(null);
        return new MentorshipParticipantResponse(userId.toHexString(), user.getName(), user.getRole(), avatar);
    }
}
