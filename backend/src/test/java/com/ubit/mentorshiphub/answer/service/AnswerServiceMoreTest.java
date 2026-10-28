package com.ubit.mentorshiphub.answer.service;

import com.ubit.mentorshiphub.answer.dto.AnswerCreateRequest;
import com.ubit.mentorshiphub.answer.dto.AnswerResponse;
import com.ubit.mentorshiphub.answer.dto.AnswerUpdateRequest;
import com.ubit.mentorshiphub.answer.model.Answer;
import com.ubit.mentorshiphub.answer.model.AnswerStatus;
import com.ubit.mentorshiphub.answer.repository.AnswerRepository;
import com.ubit.mentorshiphub.common.exception.InvalidRequestException;
import com.ubit.mentorshiphub.common.exception.VerificationStateConflictException;
import com.ubit.mentorshiphub.common.security.AccountAccess;
import com.ubit.mentorshiphub.profile.repository.ProfileRepository;
import com.ubit.mentorshiphub.question.model.Question;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnswerServiceMoreTest {
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

    private User verifiedUser(ObjectId id) {
        User u = new User();
        u.setId(id);
        u.setName("Test");
        u.setRole(AppRole.STUDENT);
        u.setVerificationStatus(VerificationStatus.VERIFIED);
        u.setEnabled(true);
        return u;
    }

    @Test
    void cannotAnswerClosedQuestion() {
        User u = verifiedUser(new ObjectId());
        Question q = new Question();
        q.setId(new ObjectId());
        q.setStatus(QuestionStatus.CLOSED);
        when(accountAccess.requireCurrentUser()).thenReturn(u);
        when(questionRepository.findById(any())).thenReturn(Optional.of(q));
        assertThatThrownBy(() -> answerService.createAnswer(q.getId().toHexString(), new AnswerCreateRequest("Valid answer body content.")))
                .isInstanceOf(VerificationStateConflictException.class);
    }

    @Test
    void cannotAnswerDeletedQuestion() {
        User u = verifiedUser(new ObjectId());
        Question q = new Question();
        q.setId(new ObjectId());
        q.setStatus(QuestionStatus.DELETED);
        when(accountAccess.requireCurrentUser()).thenReturn(u);
        when(questionRepository.findById(any())).thenReturn(Optional.of(q));
        assertThatThrownBy(() -> answerService.createAnswer(q.getId().toHexString(), new AnswerCreateRequest("Valid answer body content.")))
                .isInstanceOf(VerificationStateConflictException.class);
    }

    // ------------------------------------------------------------- min length

    @Test
    void createRejectsBodyThatIsTooShortAfterTrimming() {
        User u = verifiedUser(new ObjectId());
        Question q = activeQuestion(u);
        when(accountAccess.requireCurrentUser()).thenReturn(u);
        when(questionRepository.findById(any())).thenReturn(Optional.of(q));

        // 11 raw characters, so a raw-length check would pass; only 3 remain once
        // the surrounding whitespace is trimmed.
        assertThatThrownBy(() -> answerService.createAnswer(q.getId().toHexString(),
                new AnswerCreateRequest("       abc       ")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void updateRejectsBodyThatIsTooShortAfterTrimming() {
        User u = verifiedUser(new ObjectId());
        Answer a = activeAnswer(u);
        Question q = activeQuestion(u);
        q.setId(a.getQuestionId());
        when(accountAccess.requireCurrentUser()).thenReturn(u);
        when(answerRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(questionRepository.findById(a.getQuestionId())).thenReturn(Optional.of(q));

        assertThatThrownBy(() -> answerService.updateAnswer(a.getId().toHexString(),
                new AnswerUpdateRequest("       abc       ")))
                .isInstanceOf(InvalidRequestException.class);
    }

    // --------------------------------------------------------------- unaccept

    @Test
    void unacceptRejectsAnAnswerThatIsNotTheAcceptedOne() {
        User u = verifiedUser(new ObjectId());
        Question q = activeQuestion(u);
        q.setAcceptedAnswerId(new ObjectId());
        when(accountAccess.requireCurrentUser()).thenReturn(u);
        when(questionRepository.findById(any())).thenReturn(Optional.of(q));

        assertThatThrownBy(() -> answerService.unacceptAnswer(q.getId().toHexString(),
                new ObjectId().toHexString()))
                .isInstanceOf(VerificationStateConflictException.class);
    }

    @Test
    void unacceptRejectsWhenNothingIsAccepted() {
        User u = verifiedUser(new ObjectId());
        Question q = activeQuestion(u);
        q.setAcceptedAnswerId(null);
        when(accountAccess.requireCurrentUser()).thenReturn(u);
        when(questionRepository.findById(any())).thenReturn(Optional.of(q));

        assertThatThrownBy(() -> answerService.unacceptAnswer(q.getId().toHexString(),
                new ObjectId().toHexString()))
                .isInstanceOf(VerificationStateConflictException.class);
    }

    @Test
    void unacceptRejectsANonActiveQuestion() {
        User u = verifiedUser(new ObjectId());
        Question q = activeQuestion(u);
        ObjectId accepted = new ObjectId();
        q.setAcceptedAnswerId(accepted);
        q.setStatus(QuestionStatus.CLOSED);
        when(accountAccess.requireCurrentUser()).thenReturn(u);
        when(questionRepository.findById(any())).thenReturn(Optional.of(q));

        assertThatThrownBy(() -> answerService.unacceptAnswer(q.getId().toHexString(),
                accepted.toHexString()))
                .isInstanceOf(VerificationStateConflictException.class);
    }

    @Test
    void unacceptClearsTheAcceptedAnswerWhenItMatches() {
        User u = verifiedUser(new ObjectId());
        Question q = activeQuestion(u);
        ObjectId accepted = new ObjectId();
        q.setAcceptedAnswerId(accepted);
        when(accountAccess.requireCurrentUser()).thenReturn(u);
        when(questionRepository.findById(any())).thenReturn(Optional.of(q));
        when(questionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        answerService.unacceptAnswer(q.getId().toHexString(), accepted.toHexString());

        assertThat(q.getAcceptedAnswerId()).isNull();
    }

    private Question activeQuestion(User author) {
        Question q = new Question();
        q.setId(new ObjectId());
        q.setStatus(QuestionStatus.ACTIVE);
        q.setAuthorUserId(author.getId());
        return q;
    }

    private Answer activeAnswer(User author) {
        Answer a = new Answer();
        a.setId(new ObjectId());
        a.setQuestionId(new ObjectId());
        a.setAuthorUserId(author.getId());
        a.setStatus(AnswerStatus.ACTIVE);
        a.setBody("An original body that is long enough to keep.");
        return a;
    }
}
