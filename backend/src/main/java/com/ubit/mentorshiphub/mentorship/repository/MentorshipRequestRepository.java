package com.ubit.mentorshiphub.mentorship.repository;

import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.ubit.mentorshiphub.mentorship.model.MentorshipRequest;
import com.ubit.mentorshiphub.mentorship.model.MentorshipRequestStatus;

/**
 * Persistence access to {@link MentorshipRequest}.
 *
 * <p>The duplicate-pair invariant is <em>not</em> trusted to an
 * {@code exists}-then-{@code insert} here: that pattern races. It is enforced by
 * a unique partial index on {@code pairKey} for the non-terminal statuses
 * ({@code PENDING} and {@code ACCEPTING}), created in
 * {@code MongoIndexInitializer}, so a concurrent duplicate is a duplicate-key
 * error rather than a silent second document. Covering {@code ACCEPTING} too
 * means a pair cannot gain a second open request while one is mid-acceptance.
 */
public interface MentorshipRequestRepository extends MongoRepository<MentorshipRequest, ObjectId> {

    Page<MentorshipRequest> findByRequesterUserId(ObjectId requesterUserId, Pageable pageable);

    Page<MentorshipRequest> findByRecipientUserId(ObjectId recipientUserId, Pageable pageable);

    boolean existsByPairKeyAndStatus(String pairKey, MentorshipRequestStatus status);

    /** Every request currently in the given status; drives startup recovery. */
    List<MentorshipRequest> findByStatus(MentorshipRequestStatus status);
}
