package com.ubit.mentorshiphub.mentorship.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.ubit.mentorshiphub.mentorship.model.MentorshipRequest;
import com.ubit.mentorshiphub.mentorship.model.MentorshipRequestStatus;
import com.ubit.mentorshiphub.mentorship.model.MentorshipStatus;
import com.ubit.mentorshiphub.mentorship.model.PairKeys;
import com.ubit.mentorshiphub.mentorship.repository.MentorshipRepository;
import com.ubit.mentorshiphub.mentorship.repository.MentorshipRequestRepository;

/**
 * Repairs mentorship records left inconsistent by a process crash during
 * acceptance, and resolves the one cross-collection conflict the request-first
 * acceptance cannot rule out on its own.
 *
 * <h2>Why this exists</h2>
 *
 * <p>A standalone {@code mongod} has no multi-document transactions, so an
 * acceptance writes two documents in sequence: the request first moves to
 * {@link MentorshipRequestStatus#ACCEPTING}, then the {@code ACTIVE} mentorship
 * is inserted, then the request is finalized to
 * {@link MentorshipRequestStatus#ACCEPTED}. A crash between the second and third
 * writes leaves an {@code ACCEPTING} request and a real mentorship; this service
 * detects exactly that after a restart and finishes the job. The state is
 * durable metadata in the collection, so it survives a restart by construction -
 * nothing here depends on an in-memory queue.
 *
 * <h2>Resolution rules (deterministic, idempotent)</h2>
 * <ul>
 *   <li>{@code ACCEPTING} request <b>with</b> an {@code ACTIVE} mentorship for
 *       its pair - roll forward to {@code ACCEPTED}. The mentorship is
 *       legitimate and is never deleted.</li>
 *   <li>{@code ACCEPTING} request <b>without</b> a mentorship - roll back to
 *       {@code PENDING}; no mentorship was written, so the invitation is simply
 *       open again.</li>
 *   <li>{@code PENDING} request that shares a pair with an {@code ACTIVE}
 *       mentorship - cancel the request. The mentorship is authoritative; a
 *       pending request must never coexist with it.</li>
 * </ul>
 *
 * <p>Every repair is a conditional update keyed on the current status, so
 * running this repeatedly (or concurrently on two instances) cannot corrupt a
 * record that has already been repaired: the second pass matches nothing. When
 * the state is ambiguous the safer reading wins - a mentorship is only ever
 * completed or left alone, never removed.
 */
@Component
public class MentorshipRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(MentorshipRecoveryService.class);

    private final MentorshipRequestRepository requestRepository;
    private final MentorshipRepository mentorshipRepository;
    private final MongoTemplate mongoTemplate;
    private final Clock clock;
    private final boolean enabled;

    public MentorshipRecoveryService(MentorshipRequestRepository requestRepository,
                                     MentorshipRepository mentorshipRepository,
                                     MongoTemplate mongoTemplate,
                                     Clock clock,
                                     @Value("${app.mentorship.recovery-enabled:true}") boolean enabled) {
        this.requestRepository = requestRepository;
        this.mentorshipRepository = mentorshipRepository;
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
        this.enabled = enabled;
    }

    /**
     * Runs once the context is ready. A failure is logged and swallowed: a
     * recovery problem must not stop the application from serving, and the next
     * restart will try again since the inconsistent state is persisted.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!enabled) {
            log.debug("Mentorship recovery is disabled for this profile");
            return;
        }
        try {
            int repaired = recoverInterruptedAcceptances();
            if (repaired > 0) {
                log.warn("Mentorship recovery repaired {} interrupted or conflicting record(s)", repaired);
            }
        } catch (RuntimeException ex) {
            log.error("Mentorship recovery failed; interrupted acceptances may remain until the next start.", ex);
        }
    }

    /**
     * Scans for and repairs interrupted acceptances and the pending/active
     * conflict. Safe to call repeatedly and from a test.
     *
     * @return the number of documents actually changed
     */
    public int recoverInterruptedAcceptances() {
        Instant now = clock.instant();
        int repaired = 0;

        for (MentorshipRequest request : requestRepository.findByStatus(MentorshipRequestStatus.ACCEPTING)) {
            String pairKey = pairKeyOf(request);
            boolean mentorshipExists =
                    mentorshipRepository.existsByPairKeyAndStatus(pairKey, MentorshipStatus.ACTIVE);
            if (mentorshipExists) {
                if (flip(request.getId(), MentorshipRequestStatus.ACCEPTING,
                        MentorshipRequestStatus.ACCEPTED, now, true)) {
                    repaired++;
                    log.warn("Completed interrupted acceptance: request {} is now ACCEPTED.", request.getId());
                }
            } else {
                if (flip(request.getId(), MentorshipRequestStatus.ACCEPTING,
                        MentorshipRequestStatus.PENDING, now, false)) {
                    repaired++;
                    log.warn("Reopened interrupted acceptance: request {} is PENDING again "
                            + "(no mentorship had been written).", request.getId());
                }
            }
        }

        for (MentorshipRequest pending : requestRepository.findByStatus(MentorshipRequestStatus.PENDING)) {
            String pairKey = pairKeyOf(pending);
            if (mentorshipRepository.existsByPairKeyAndStatus(pairKey, MentorshipStatus.ACTIVE)) {
                if (flip(pending.getId(), MentorshipRequestStatus.PENDING,
                        MentorshipRequestStatus.CANCELLED, now, true)) {
                    repaired++;
                    log.warn("Resolved a conflicting pair: pending request {} cancelled because an "
                            + "active mentorship already exists.", pending.getId());
                }
            }
        }

        return repaired;
    }

    /**
     * Conditional status move. {@code responded} stamps {@code respondedAt} only
     * for transitions that represent a real response, so a roll back to
     * {@code PENDING} leaves that field unset.
     */
    private boolean flip(ObjectId id, MentorshipRequestStatus expected,
                         MentorshipRequestStatus next, Instant now, boolean responded) {
        Query query = Query.query(Criteria.where("_id").is(id).and("status").is(expected));
        Update update = new Update().set("status", next).set("updatedAt", now);
        if (responded) {
            update.set("respondedAt", now);
        }
        return mongoTemplate.updateFirst(query, update, MentorshipRequest.class).getModifiedCount() == 1;
    }

    private String pairKeyOf(MentorshipRequest request) {
        return request.getPairKey() != null
                ? request.getPairKey()
                : PairKeys.of(request.getRequesterUserId(), request.getRecipientUserId());
    }
}
