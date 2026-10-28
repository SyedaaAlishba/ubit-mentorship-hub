package com.ubit.mentorshiphub.profile.repository;

import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.ubit.mentorshiphub.profile.model.Profile;

/**
 * Repository for Profile documents.
 */
public interface ProfileRepository extends MongoRepository<Profile, ObjectId> {

    Optional<Profile> findByUserId(ObjectId userId);

    boolean existsByUserId(ObjectId userId);
}
