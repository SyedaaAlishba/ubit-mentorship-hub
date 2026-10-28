package com.ubit.mentorshiphub.question.repository;

import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.ubit.mentorshiphub.question.model.Question;
import com.ubit.mentorshiphub.question.model.QuestionCategory;
import com.ubit.mentorshiphub.question.model.QuestionStatus;

public interface QuestionRepository extends MongoRepository<Question, ObjectId> {
    Page<Question> findByStatusIn(List<QuestionStatus> statuses, Pageable pageable);
    Page<Question> findByStatusInAndCategory(List<QuestionStatus> statuses, QuestionCategory category, Pageable pageable);
    Page<Question> findByStatusInAndAuthorUserId(List<QuestionStatus> statuses, ObjectId authorUserId, Pageable pageable);
    Page<Question> findByStatusInAndCategoryAndAuthorUserId(List<QuestionStatus> statuses, QuestionCategory category, ObjectId authorUserId, Pageable pageable);
    Page<Question> findByStatusInAndTagsIn(List<QuestionStatus> statuses, List<String> tags, Pageable pageable);
    Page<Question> findByStatusInAndCategoryAndTagsIn(List<QuestionStatus> statuses, QuestionCategory category, List<String> tags, Pageable pageable);
    Page<Question> findByStatusInAndAuthorUserIdAndTagsIn(List<QuestionStatus> statuses, ObjectId authorUserId, List<String> tags, Pageable pageable);
    Page<Question> findByStatusInAndCategoryAndAuthorUserIdAndTagsIn(List<QuestionStatus> statuses, QuestionCategory category, ObjectId authorUserId, List<String> tags, Pageable pageable);
    Optional<Question> findByIdAndStatusNot(ObjectId id, QuestionStatus status);
}
