package com.ubit.mentorshiphub.answer.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.ubit.mentorshiphub.answer.dto.AnswerCreateRequest;
import com.ubit.mentorshiphub.answer.dto.AnswerResponse;
import com.ubit.mentorshiphub.answer.dto.AnswerUpdateRequest;
import com.ubit.mentorshiphub.answer.model.Answer;
import com.ubit.mentorshiphub.answer.model.AnswerStatus;
import com.ubit.mentorshiphub.answer.repository.AnswerRepository;
import com.ubit.mentorshiphub.common.exception.InvalidRequestException;
import com.ubit.mentorshiphub.common.exception.ResourceNotFoundException;
import com.ubit.mentorshiphub.common.exception.VerificationStateConflictException;
import com.ubit.mentorshiphub.common.security.AccountAccess;
import com.ubit.mentorshiphub.common.security.AuthorizationGates;
import com.ubit.mentorshiphub.profile.model.Profile;
import com.ubit.mentorshiphub.profile.repository.ProfileRepository;
import com.ubit.mentorshiphub.question.dto.AuthorResponse;
import com.ubit.mentorshiphub.question.model.Question;
import com.ubit.mentorshiphub.question.model.QuestionStatus;
import com.ubit.mentorshiphub.question.repository.QuestionRepository;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.user.service.UserService;

@Service
public class AnswerService {
    /** Minimum body length after trimming, mirroring the DTO constraint. */
    private static final int BODY_MIN_LENGTH = 10;

    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final UserService userService;
    private final ProfileRepository profileRepository;
    private final AccountAccess accountAccess;
    private final Clock clock;

    public AnswerService(AnswerRepository answerRepository, QuestionRepository questionRepository,
                         UserService userService, ProfileRepository profileRepository,
                         AccountAccess accountAccess, Clock clock) {
        this.answerRepository = answerRepository;
        this.questionRepository = questionRepository;
        this.userService = userService;
        this.profileRepository = profileRepository;
        this.accountAccess = accountAccess;
        this.clock = clock;
    }

    public AnswerResponse createAnswer(String questionId, AnswerCreateRequest req) {
        User current = accountAccess.requireCurrentUser();
        AuthorizationGates.requireVerifiedParticipant(current);
        ObjectId qid = parseId(questionId);
        Question q = questionRepository.findById(qid).orElseThrow(() -> new ResourceNotFoundException("Question not found."));
        if (q.getStatus() != QuestionStatus.ACTIVE) throw new VerificationStateConflictException("Forbidden");
        String validated = validatedBody(req.body());
        if (q.getAuthorUserId() != null && q.getAuthorUserId().equals(current.getId())) {
            throw new VerificationStateConflictException("You cannot answer your own question.");
        }
        Instant now = clock.instant();
        Answer a = new Answer();
        a.setQuestionId(qid);
        a.setAuthorUserId(current.getId());
        a.setBody(validated);
        a.setStatus(AnswerStatus.ACTIVE);
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        a = answerRepository.save(a);
        return toResponse(a, q);
    }

    public Page<AnswerResponse> listAnswers(String questionId, int page, int size) {
        User current = accountAccess.requireCurrentUser();
        AuthorizationGates.requireOnboardingComplete(current);
        ObjectId qid = parseId(questionId);
        Question q = questionRepository.findByIdAndStatusNot(qid, QuestionStatus.DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found."));
        if (size < 1 || size > 50) throw new InvalidRequestException("size must be between 1 and 50");
        if (page < 0) throw new InvalidRequestException("page must be >= 0");
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));
        return answerRepository.findByQuestionIdAndStatus(qid, AnswerStatus.ACTIVE, pageable).map(a -> toResponse(a, q));
    }

    public AnswerResponse updateAnswer(String answerId, AnswerUpdateRequest req) {
        User current = accountAccess.requireCurrentUser();
        AuthorizationGates.requireVerifiedParticipant(current);
        ObjectId aid = parseId(answerId);
        Answer a = answerRepository.findById(aid).orElseThrow(() -> new ResourceNotFoundException("Question not found."));
        if (a.getStatus() != AnswerStatus.ACTIVE) throw new VerificationStateConflictException("Forbidden");
        if (!a.getAuthorUserId().equals(current.getId())) throw new VerificationStateConflictException("Forbidden");
        Question q = questionRepository.findById(a.getQuestionId()).orElseThrow(() -> new ResourceNotFoundException("Question not found."));
        if (q.getStatus() == QuestionStatus.DELETED) throw new VerificationStateConflictException("Forbidden");
        if (req.body() != null) a.setBody(validatedBody(req.body()));
        a.setEditedAt(clock.instant());
        a.setUpdatedAt(clock.instant());
        return toResponse(answerRepository.save(a), q);
    }

    public void deleteAnswer(String answerId) {
        User current = accountAccess.requireCurrentUser();
        AuthorizationGates.requireVerifiedParticipant(current);
        ObjectId aid = parseId(answerId);
        Answer a = answerRepository.findById(aid).orElseThrow(() -> new ResourceNotFoundException("Question not found."));
        if (a.getStatus() == AnswerStatus.DELETED) throw new ResourceNotFoundException("Question not found.");
        if (!a.getAuthorUserId().equals(current.getId())) throw new VerificationStateConflictException("Forbidden");
        if (a.getStatus() != AnswerStatus.ACTIVE) throw new VerificationStateConflictException("Forbidden");
        Question q = questionRepository.findById(a.getQuestionId()).orElseThrow(() -> new ResourceNotFoundException("Question not found."));
        if (q.getAcceptedAnswerId() != null && q.getAcceptedAnswerId().equals(a.getId())) {
            throw new VerificationStateConflictException("Forbidden");
        }
        a.setStatus(AnswerStatus.DELETED);
        a.setUpdatedAt(clock.instant());
        answerRepository.save(a);
    }

    public Question acceptAnswer(String questionId, String answerId) {
        User current = accountAccess.requireCurrentUser();
        AuthorizationGates.requireVerifiedParticipant(current);
        ObjectId qid = parseId(questionId);
        ObjectId aid = parseId(answerId);
        Question q = questionRepository.findById(qid).orElseThrow(() -> new ResourceNotFoundException("Question not found."));
        if (q.getStatus() == QuestionStatus.DELETED) throw new ResourceNotFoundException("Question not found.");
        if (!q.getAuthorUserId().equals(current.getId())) throw new VerificationStateConflictException("Forbidden");
        if (q.getStatus() != QuestionStatus.ACTIVE) throw new VerificationStateConflictException("Forbidden");
        Answer a = answerRepository.findById(aid).orElseThrow(() -> new ResourceNotFoundException("Question not found."));
        if (a.getStatus() != AnswerStatus.ACTIVE) throw new VerificationStateConflictException("Forbidden");
        if (!a.getQuestionId().equals(qid)) throw new VerificationStateConflictException("Forbidden");
        q.setAcceptedAnswerId(aid);
        q.setUpdatedAt(clock.instant());
        return questionRepository.save(q);
    }

    public Question unacceptAnswer(String questionId, String answerId) {
        User current = accountAccess.requireCurrentUser();
        AuthorizationGates.requireVerifiedParticipant(current);
        ObjectId qid = parseId(questionId);
        ObjectId aid = parseId(answerId);
        Question q = questionRepository.findById(qid).orElseThrow(() -> new ResourceNotFoundException("Question not found."));
        if (q.getStatus() == QuestionStatus.DELETED) throw new ResourceNotFoundException("Question not found.");
        if (!q.getAuthorUserId().equals(current.getId())) throw new VerificationStateConflictException("Forbidden");
        // Consistent with acceptAnswer: acceptance can only be withdrawn while
        // the question is still open. A closed or otherwise settled question is
        // a conflict, not a silent no-op.
        if (q.getStatus() != QuestionStatus.ACTIVE) throw new VerificationStateConflictException("Forbidden");
        // The named answer must be the one currently accepted. Without this the
        // call would clear acceptance no matter which answer id was supplied,
        // turning a targeted action into a destructive one.
        if (q.getAcceptedAnswerId() == null || !q.getAcceptedAnswerId().equals(aid)) {
            throw new VerificationStateConflictException("Forbidden");
        }
        q.setAcceptedAnswerId(null);
        q.setUpdatedAt(clock.instant());
        return questionRepository.save(q);
    }

    private String trim(String s) { if (s == null) return null; return s.trim(); }

    /**
     * Trims the body and enforces the minimum length on the trimmed value. The
     * DTO's {@code @Size} checks the raw string, so a body padded with whitespace
     * could satisfy it and still store as something far below the minimum.
     */
    private String validatedBody(String body) {
        String trimmed = trim(body);
        if (trimmed == null || trimmed.length() < BODY_MIN_LENGTH) {
            throw new InvalidRequestException("body must be at least " + BODY_MIN_LENGTH + " characters");
        }
        return trimmed;
    }

    private ObjectId parseId(String id) {
        if (id == null || id.isBlank()) throw new ResourceNotFoundException("Question not found.");
        try { return new ObjectId(id); } catch (Exception e) { throw new ResourceNotFoundException("Question not found."); }
    }
    private AuthorResponse toAuthor(ObjectId userId) {
        User u = userService.findById(userId).orElse(null);
        if (u == null) return new AuthorResponse(userId.toHexString(), "Unknown", null, null);
        String avatar = null;
        Optional<Profile> p = profileRepository.findByUserId(userId);
        if (p.isPresent() && p.get().getAvatarGridFsId() != null) avatar = p.get().getAvatarGridFsId();
        return new AuthorResponse(userId.toHexString(), u.getName(), u.getRole(), avatar);
    }
    private AnswerResponse toResponse(Answer a, Question q) {
        boolean isAccepted = q.getAcceptedAnswerId() != null && q.getAcceptedAnswerId().equals(a.getId()) && a.getStatus() == AnswerStatus.ACTIVE;
        return new AnswerResponse(a.getId().toHexString(), a.getQuestionId().toHexString(), toAuthor(a.getAuthorUserId()),
                a.getBody(), a.getStatus(), isAccepted, a.getEditedAt(), a.getCreatedAt(), a.getUpdatedAt());
    }
}
