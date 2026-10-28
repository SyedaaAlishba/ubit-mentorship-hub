package com.ubit.mentorshiphub.answer.service;

import com.ubit.mentorshiphub.answer.dto.AnswerCreateRequest;
import com.ubit.mentorshiphub.answer.model.Answer;
import com.ubit.mentorshiphub.answer.repository.AnswerRepository;
import com.ubit.mentorshiphub.common.security.AccountAccess;
import com.ubit.mentorshiphub.profile.repository.ProfileRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnswerServiceBasicTest {
    @Mock private AnswerRepository answerRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private UserService userService;
    @Mock private ProfileRepository profileRepository;
    @Mock private AccountAccess accountAccess;
    private Clock clock;
    private AnswerService answerService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneId.of("UTC"));
        answerService = new AnswerService(answerRepository, questionRepository, userService, profileRepository, accountAccess, clock);
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
    void createAnswerSuccess() {
        User u = verifiedUser();
        Question q = new Question();
        q.setId(new ObjectId());
        q.setStatus(QuestionStatus.ACTIVE);
        when(accountAccess.requireCurrentUser()).thenReturn(u);
        when(questionRepository.findById(any())).thenReturn(Optional.of(q));
        when(answerRepository.save(any())).thenAnswer(i -> { Answer a = i.getArgument(0); a.setId(new ObjectId()); return a; });
        AnswerCreateRequest req = new AnswerCreateRequest("This is a valid answer body long enough.");
        assertThat(answerService.createAnswer(q.getId().toHexString(), req)).isNotNull();
    }
}
