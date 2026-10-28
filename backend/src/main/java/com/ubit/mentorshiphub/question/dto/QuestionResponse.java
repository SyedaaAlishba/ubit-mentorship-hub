package com.ubit.mentorshiphub.question.dto;

import com.ubit.mentorshiphub.question.model.QuestionCategory;
import com.ubit.mentorshiphub.question.model.QuestionStatus;
import java.time.Instant;
import java.util.List;

public record QuestionResponse(
        String questionId,
        AuthorResponse author,
        String title,
        String body,
        QuestionCategory category,
        List<String> tags,
        QuestionStatus status,
        String acceptedAnswerId,
        Instant editedAt,
        Instant createdAt,
        Instant updatedAt
) {}
