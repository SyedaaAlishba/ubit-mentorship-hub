package com.ubit.mentorshiphub.question.service;

import com.ubit.mentorshiphub.common.exception.ResourceNotFoundException;
import com.ubit.mentorshiphub.common.exception.VerificationStateConflictException;
import com.ubit.mentorshiphub.common.security.AccountAccess;
import com.ubit.mentorshiphub.profile.repository.ProfileRepository;
import com.ubit.mentorshiphub.question.dto.QuestionCreateRequest;
import com.ubit.mentorshiphub.question.dto.QuestionResponse;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionServiceExtendedTest {
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

    private User verifiedUser(ObjectId id) {
        User u = new User();
        u.setId(id);
        u.setName("Test");
        u.setRole(AppRole.STUDENT);
        u.setVerificationStatus(VerificationStatus.VERIFIED);
        u.setEnabled(true);
        return u;
    }
    private User unverifiedUser(ObjectId id) {
        User u = new User();
        u.setId(id);
        u.setName("Un");
        u.setRole(AppRole.STUDENT);
        u.setVerificationStatus(VerificationStatus.UNVERIFIED);
        u.setEnabled(true);
        return u;
    }

    @Test
    void createTagsNormalized() {
        User u = verifiedUser(new ObjectId());
        when(accountAccess.requireCurrentUser()).thenReturn(u);
        ArgumentCaptor<Question> cap = ArgumentCaptor.forClass(Question.class);
        when(questionRepository.save(any())).thenAnswer(i -> { Question q = i.getArgument(0); q.setId(new ObjectId()); return q; });
        QuestionCreateRequest req = new QuestionCreateRequest("Test Question Title", "This is body content that is long enough for validation.", QuestionCategory.TECHNICAL, List.of("  Java  ", "java", "SPRING"));
        questionService.createQuestion(req);
        // verify saved
    }
}
