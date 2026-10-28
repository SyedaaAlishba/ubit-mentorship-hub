package com.ubit.mentorshiphub.mentorship.repository;

import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.ubit.mentorshiphub.mentorship.model.Mentorship;
import com.ubit.mentorshiphub.mentorship.model.MentorshipStatus;

/**
 * Persistence access to {@link Mentorship}.
 *
 * <p>The "one active mentorship per pair" invariant is enforced by a unique
 * partial index on {@code pairKey} where {@code status = ACTIVE} (see
 * {@code MongoIndexInitializer}); a concurrent second acceptance collides on
 * that index instead of creating a second active mentorship.
 */
public interface MentorshipRepository extends MongoRepository<Mentorship, ObjectId> {

    boolean existsByPairKeyAndStatus(String pairKey, MentorshipStatus status);

    Optional<Mentorship> findByPairKeyAndStatus(String pairKey, MentorshipStatus status);

    /** Active mentorships in which the user is either the mentor or the mentee. */
    @Query("{ 'status': 'ACTIVE', '$or': [ { 'mentorUserId': ?0 }, { 'menteeUserId': ?0 } ] }")
    Page<Mentorship> findActiveForUser(ObjectId userId, Pageable pageable);
}
