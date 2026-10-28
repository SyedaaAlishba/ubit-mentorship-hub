package com.ubit.mentorshiphub.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RagQueryRequest(
        @NotBlank(message = "Question must not be blank")
        @Size(min = 2, max = 1000, message = "Question must be between 2 and 1000 characters")
        String question
) {}
