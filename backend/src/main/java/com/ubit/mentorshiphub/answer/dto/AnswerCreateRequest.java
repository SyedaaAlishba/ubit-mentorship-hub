package com.ubit.mentorshiphub.answer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnswerCreateRequest(
        @NotBlank @Size(min = 10, max = 50000) String body
) {}
