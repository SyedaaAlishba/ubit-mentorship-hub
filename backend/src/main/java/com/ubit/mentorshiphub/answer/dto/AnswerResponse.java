package com.ubit.mentorshiphub.answer.dto;

import com.ubit.mentorshiphub.answer.model.AnswerStatus;
import com.ubit.mentorshiphub.question.dto.AuthorResponse;
import java.time.Instant;

public record AnswerResponse(
        String answerId,
        String questionId,
        AuthorResponse author,
        String body,
        AnswerStatus status,
        boolean isAccepted,
        Instant editedAt,
        Instant createdAt,
        Instant updatedAt
) {}
