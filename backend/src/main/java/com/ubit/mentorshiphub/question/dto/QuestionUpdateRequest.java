package com.ubit.mentorshiphub.question.dto;

import com.ubit.mentorshiphub.question.model.QuestionCategory;
import jakarta.validation.constraints.Size;
import java.util.List;

public record QuestionUpdateRequest(
        @Size(min = 5, max = 200) String title,
        @Size(min = 10, max = 50000) String body,
        QuestionCategory category,
        @Size(max = 10) List<@Size(min = 1, max = 50) String> tags
) {}
