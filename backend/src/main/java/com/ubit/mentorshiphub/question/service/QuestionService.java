package com.ubit.mentorshiphub.question.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.ubit.mentorshiphub.common.exception.InvalidRequestException;
import com.ubit.mentorshiphub.common.exception.ResourceNotFoundException;
import com.ubit.mentorshiphub.common.exception.VerificationStateConflictException;
import com.ubit.mentorshiphub.common.security.AccountAccess;
import com.ubit.mentorshiphub.common.security.AuthorizationGates;
import com.ubit.mentorshiphub.profile.model.Profile;
import com.ubit.mentorshiphub.profile.repository.ProfileRepository;
import com.ubit.mentorshiphub.question.dto.AuthorResponse;
import com.ubit.mentorshiphub.question.dto.QuestionCreateRequest;
import com.ubit.mentorshiphub.question.dto.QuestionDetailResponse;
import com.ubit.mentorshiphub.question.dto.QuestionResponse;
import com.ubit.mentorshiphub.question.dto.QuestionUpdateRequest;
import com.ubit.mentorshiphub.question.model.Question;
import com.ubit.mentorshiphub.question.model.QuestionCategory;
import com.ubit.mentorshiphub.question.model.QuestionStatus;
import com.ubit.mentorshiphub.question.repository.QuestionRepository;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.user.service.UserService;

@Service
public class QuestionService {
    private final QuestionRepository questionRepository;
    private final UserService userService;
    private final ProfileRepository profileRepository;
    private final AccountAccess accountAccess;
    private final Clock clock;

    public QuestionService(QuestionRepository questionRepository, UserService userService,
                           ProfileRepository profileRepository, AccountAccess accountAccess, Clock clock) {
        this.questionRepository = questionRepository;
        this.userService = userService;
        this.profileRepository = profileRepository;
        this.accountAccess = accountAccess;
        this.clock = clock;
    }

    public QuestionResponse createQuestion(QuestionCreateRequest req) {
        User current = accountAccess.requireCurrentUser();
        AuthorizationGates.requireVerifiedParticipant(current);
        Instant now = clock.instant();
        Question q = new Question();
        q.setAuthorUserId(current.getId());
        q.setTitle(trim(req.title()));
        q.setBody(trim(req.body()));
        q.setCategory(req.category());
        q.setTags(normalizeTags(req.tags()));
        q.setStatus(QuestionStatus.ACTIVE);
        q.setCreatedAt(now);
        q.setUpdatedAt(now);
        return toResponse(questionRepository.save(q));
    }

    public Page<QuestionResponse> listQuestions(int page, int size, QuestionCategory category, List<String> tags, String authorId) {
        User current = accountAccess.requireCurrentUser();
        AuthorizationGates.requireOnboardingComplete(current);
        if (size < 1 || size > 50) throw new InvalidRequestException("size must be between 1 and 50");
        if (page < 0) throw new InvalidRequestException("page must be >= 0");
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        List<QuestionStatus> statuses = Arrays.asList(QuestionStatus.ACTIVE, QuestionStatus.CLOSED);
        List<String> tagList = normalizeTagsAny(tags);
        ObjectId authorOid = null;
        if (authorId != null && !authorId.isBlank()) {
            try { authorOid = new ObjectId(authorId); } catch (Exception e) { throw new ResourceNotFoundException("Question not found."); }
        }
        Page<Question> result;
        if (category != null && authorOid != null && !tagList.isEmpty()) {
            result = questionRepository.findByStatusInAndCategoryAndAuthorUserIdAndTagsIn(statuses, category, authorOid, tagList, pageable);
        } else if (category != null && authorOid != null) {
            result = questionRepository.findByStatusInAndCategoryAndAuthorUserId(statuses, category, authorOid, pageable);
        } else if (category != null && !tagList.isEmpty()) {
            result = questionRepository.findByStatusInAndCategoryAndTagsIn(statuses, category, tagList, pageable);
        } else if (authorOid != null && !tagList.isEmpty()) {
            result = questionRepository.findByStatusInAndAuthorUserIdAndTagsIn(statuses, authorOid, tagList, pageable);
        } else if (category != null) {
            result = questionRepository.findByStatusInAndCategory(statuses, category, pageable);
        } else if (authorOid != null) {
            result = questionRepository.findByStatusInAndAuthorUserId(statuses, authorOid, pageable);
        } else if (!tagList.isEmpty()) {
            result = questionRepository.findByStatusInAndTagsIn(statuses, tagList, pageable);
        } else {
            result = questionRepository.findByStatusIn(statuses, pageable);
        }
        return result.map(this::toResponse);
    }

    public QuestionDetailResponse getQuestion(String id) {
        User current = accountAccess.requireCurrentUser();
        AuthorizationGates.requireOnboardingComplete(current);
        ObjectId oid = parseId(id);
        Question q = questionRepository.findByIdAndStatusNot(oid, QuestionStatus.DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found."));
        return toDetailResponse(q);
    }

    public QuestionResponse updateQuestion(String id, QuestionUpdateRequest req) {
        User current = accountAccess.requireCurrentUser();
        AuthorizationGates.requireVerifiedParticipant(current);
        ObjectId oid = parseId(id);
        Question q = questionRepository.findById(oid).orElseThrow(() -> new ResourceNotFoundException("Question not found."));
        if (!q.getAuthorUserId().equals(current.getId())) throw new VerificationStateConflictException("Forbidden");
        if (q.getStatus() != QuestionStatus.ACTIVE) throw new VerificationStateConflictException("Forbidden");
        if (req.title() != null) q.setTitle(trim(req.title()));
        if (req.body() != null) q.setBody(trim(req.body()));
        if (req.category() != null) q.setCategory(req.category());
        if (req.tags() != null) q.setTags(normalizeTags(req.tags()));
        q.setEditedAt(clock.instant());
        q.setUpdatedAt(clock.instant());
        return toResponse(questionRepository.save(q));
    }

    public void deleteQuestion(String id) {
        User current = accountAccess.requireCurrentUser();
        AuthorizationGates.requireVerifiedParticipant(current);
        ObjectId oid = parseId(id);
        Question q = questionRepository.findById(oid).orElseThrow(() -> new ResourceNotFoundException("Question not found."));
        if (q.getStatus() == QuestionStatus.DELETED) throw new ResourceNotFoundException("Question not found.");
        if (!q.getAuthorUserId().equals(current.getId())) throw new VerificationStateConflictException("Forbidden");
        if (q.getStatus() != QuestionStatus.ACTIVE) throw new VerificationStateConflictException("Forbidden");
        if (q.getAcceptedAnswerId() != null) throw new VerificationStateConflictException("Forbidden");
        q.setStatus(QuestionStatus.DELETED);
        q.setUpdatedAt(clock.instant());
        questionRepository.save(q);
    }

    public QuestionResponse closeQuestion(String id) {
        User current = accountAccess.requireCurrentUser();
        AuthorizationGates.requireVerifiedParticipant(current);
        ObjectId oid = parseId(id);
        Question q = questionRepository.findById(oid).orElseThrow(() -> new ResourceNotFoundException("Question not found."));
        if (q.getStatus() == QuestionStatus.DELETED) throw new ResourceNotFoundException("Question not found.");
        if (!q.getAuthorUserId().equals(current.getId())) throw new VerificationStateConflictException("Forbidden");
        if (q.getStatus() != QuestionStatus.ACTIVE) throw new VerificationStateConflictException("Forbidden");
        q.setStatus(QuestionStatus.CLOSED);
        q.setUpdatedAt(clock.instant());
        return toResponse(questionRepository.save(q));
    }

    private String trim(String s) { if (s == null) return null; return s.trim(); }
    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) return new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String t : tags) {
            if (t == null) continue;
            String v = t.trim().toLowerCase();
            if (v.isEmpty() || v.length() > 50) continue;
            seen.add(v);
            if (seen.size() >= 10) break;
        }
        return new ArrayList<>(seen);
    }
    private List<String> normalizeTagsAny(List<String> tags) {
        if (tags == null) return new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String t : tags) {
            if (t == null) continue;
            String v = t.trim().toLowerCase();
            if (v.isEmpty() || v.length() > 50) continue;
            seen.add(v);
        }
        return new ArrayList<>(seen);
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
    private QuestionResponse toResponse(Question q) {
        return new QuestionResponse(q.getId().toHexString(), toAuthor(q.getAuthorUserId()), q.getTitle(), q.getBody(),
                q.getCategory(), new ArrayList<>(q.getTags()), q.getStatus(),
                q.getAcceptedAnswerId() == null ? null : q.getAcceptedAnswerId().toHexString(),
                q.getEditedAt(), q.getCreatedAt(), q.getUpdatedAt());
    }
    private QuestionDetailResponse toDetailResponse(Question q) {
        return new QuestionDetailResponse(q.getId().toHexString(), toAuthor(q.getAuthorUserId()), q.getTitle(), q.getBody(),
                q.getCategory(), new ArrayList<>(q.getTags()), q.getStatus(),
                q.getAcceptedAnswerId() == null ? null : q.getAcceptedAnswerId().toHexString(),
                q.getEditedAt(), q.getCreatedAt(), q.getUpdatedAt());
    }
}
