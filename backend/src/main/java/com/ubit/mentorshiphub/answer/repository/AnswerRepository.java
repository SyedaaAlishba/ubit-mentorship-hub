package com.ubit.mentorshiphub.answer.repository;

import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.ubit.mentorshiphub.answer.model.Answer;
import com.ubit.mentorshiphub.answer.model.AnswerStatus;

public interface AnswerRepository extends MongoRepository<Answer, ObjectId> {
    Page<Answer> findByQuestionIdAndStatus(ObjectId questionId, AnswerStatus status, Pageable pageable);
    List<Answer> findByQuestionIdAndStatus(ObjectId questionId, AnswerStatus status);
    Optional<Answer> findByIdAndStatus(ObjectId id, AnswerStatus status);
}
