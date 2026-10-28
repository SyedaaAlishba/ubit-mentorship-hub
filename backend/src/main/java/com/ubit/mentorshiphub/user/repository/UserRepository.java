package com.ubit.mentorshiphub.user.repository;

import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.ubit.mentorshiphub.user.model.User;

/**
 * Persistence access to {@link User}.
 *
 * <p>Only the user service may use this. Authentication and verification code
 * call the service layer instead, so that authorization rules cannot be bypassed
 * by reaching storage directly.
 */
public interface UserRepository extends MongoRepository<User, ObjectId> {

    Optional<User> findByGoogleId(String googleId);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
