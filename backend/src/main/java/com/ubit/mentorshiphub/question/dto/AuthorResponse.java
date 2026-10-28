package com.ubit.mentorshiphub.question.dto;

import com.ubit.mentorshiphub.question.model.QuestionCategory;
import com.ubit.mentorshiphub.question.model.QuestionStatus;
import java.time.Instant;
import java.util.List;

public record AuthorResponse(
        String userId,
        String name,
        com.ubit.mentorshiphub.user.model.AppRole role,
        String avatarGridFsId
) {}
