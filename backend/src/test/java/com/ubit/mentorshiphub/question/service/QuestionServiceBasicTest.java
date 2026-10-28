package com.ubit.mentorshiphub.question.service;

import com.ubit.mentorshiphub.common.exception.VerificationStateConflictException;
import com.ubit.mentorshiphub.common.security.AccountAccess;
import com.ubit.mentorshiphub.common.security.AuthorizationGates;
import com.ubit.mentorshiphub.profile.repository.ProfileRepository;
import com.ubit.mentorshiphub.question.dto.QuestionCreateRequest;
import com.ubit.mentorshiphub.question.model.Question;
import com.ubit.mentorshiphub.question.model.QuestionCategory;
import com.ubit.mentorshiphub.question.model.QuestionStatus;
import com.ubit.mentorshiphub.question.repository.QuestionRepository;
import com.ubit.mentorshiphub.user.model.AppRole;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.user.model.VerificationStatus;
import com.ubit.mentorshiphub.user.service.UserService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionServiceBasicTest {
    @Mock private QuestionRepository questionRepository;
    @Mock private UserService userService;
    @Mock private ProfileRepository profileRepository;
    @Mock private AccountAccess accountAccess;
    private Clock clock;
    private QuestionService questionService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneId.of("UTC"));
        questionService = new QuestionService(questionRepository, userService, profileRepository, accountAccess, clock);
    }

    private User verifiedUser() {
        User u = new User();
        u.setId(new ObjectId("507f191e810c19729de860aa"));
        u.setName("Ali");
        u.setRole(AppRole.STUDENT);
        u.setVerificationStatus(VerificationStatus.VERIFIED);
        u.setEnabled(true);
        return u;
    }

    @Test
    void createQuestionSuccess() {
        User u = verifiedUser();
        when(accountAccess.requireCurrentUser()).thenReturn(u);
        when(questionRepository.save(any())).thenAnswer(i -> {
            Question q = i.getArgument(0);
            q.setId(new ObjectId());
            return q;
        });
        QuestionCreateRequest req = new QuestionCreateRequest("How to test?", "This is a long enough body to satisfy validation rules.", QuestionCategory.TECHNICAL, List.of("java", "test"));
        assertThat(questionService.createQuestion(req)).isNotNull();
    }

    @Test
    void pendingUserCannotCreateQuestion() {
        User pending = new User();
        pending.setId(new ObjectId("507f191e810c19729de860aa"));
        pending.setName("Pending Student");
        pending.setRole(AppRole.STUDENT);
        pending.setVerificationStatus(VerificationStatus.PENDING);
        pending.setEnabled(true);

        when(accountAccess.requireCurrentUser()).thenReturn(pending);
        QuestionCreateRequest req = new QuestionCreateRequest("How to test?", "This is a long enough body to satisfy validation rules.", QuestionCategory.TECHNICAL, List.of("java", "test"));
        org.junit.jupiter.api.Assertions.assertThrows(
                com.ubit.mentorshiphub.common.exception.VerificationRequiredException.class,
                () -> questionService.createQuestion(req)
        );
    }

    @Test
    void pendingUserCanListQuestions() {
        User pending = new User();
        pending.setId(new ObjectId("507f191e810c19729de860aa"));
        pending.setName("Pending Student");
        pending.setRole(AppRole.STUDENT);
        pending.setVerificationStatus(VerificationStatus.PENDING);
        pending.setEnabled(true);

        when(accountAccess.requireCurrentUser()).thenReturn(pending);
        when(questionRepository.findByStatusIn(any(), any())).thenReturn(org.springframework.data.domain.Page.empty());

        assertThat(questionService.listQuestions(0, 10, null, null, null)).isNotNull();
    }
}
