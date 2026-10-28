package com.ubit.mentorshiphub.verification.repository;

import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.ubit.mentorshiphub.verification.model.ApplicationStatus;
import com.ubit.mentorshiphub.verification.model.VerificationApplication;

/**
 * Persistence access to submitted verification applications.
 *
 * <p>Only the verification and admin services use this, so ownership checks
 * cannot be skipped by reaching storage directly from a controller.
 */
public interface VerificationApplicationRepository
        extends MongoRepository<VerificationApplication, ObjectId> {

    /** The account's applications, newest first, for resubmission and history. */
    List<VerificationApplication> findByUserIdOrderBySubmittedAtDesc(ObjectId userId);

    /** The most recent application, used to number the next attempt. */
    Optional<VerificationApplication> findFirstByUserIdOrderBySubmittedAtDesc(ObjectId userId);

    /**
     * One status's applications for the administrator review queue. The sort
     * carried by {@code pageable} decides the order; the review queue asks for
     * oldest first so the earliest claim is reviewed first.
     */
    Page<VerificationApplication> findByStatus(ApplicationStatus status, Pageable pageable);
}
