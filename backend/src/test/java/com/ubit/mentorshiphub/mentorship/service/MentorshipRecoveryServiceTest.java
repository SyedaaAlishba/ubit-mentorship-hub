package com.ubit.mentorshiphub.mentorship.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.result.UpdateResult;
import com.ubit.mentorshiphub.mentorship.model.MentorshipRequest;
import com.ubit.mentorshiphub.mentorship.model.MentorshipRequestStatus;
import com.ubit.mentorshiphub.mentorship.model.MentorshipStatus;
import com.ubit.mentorshiphub.mentorship.model.PairKeys;
import com.ubit.mentorshiphub.mentorship.repository.MentorshipRepository;
import com.ubit.mentorshiphub.mentorship.repository.MentorshipRequestRepository;

/**
 * The recovery rules against mocks: which branch each persisted state takes, and
 * that a failure during startup recovery is swallowed rather than fatal. The
 * same rules are proven against a real MongoDB in
 * {@code MentorshipIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MentorshipRecoveryServiceTest {

    private static final ObjectId STUDENT_ID = new ObjectId("64b0000000000000000000a1");
    private static final ObjectId MENTOR_ID = new ObjectId("64b0000000000000000000b2");
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    private static final String PAIR_KEY = PairKeys.of(STUDENT_ID, MENTOR_ID);

    @Mock private MentorshipRequestRepository requestRepository;
    @Mock private MentorshipRepository mentorshipRepository;
    @Mock private MongoTemplate mongoTemplate;

    private MentorshipRecoveryService enabled;
    private MentorshipRecoveryService disabled;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));
        enabled = new MentorshipRecoveryService(requestRepository, mentorshipRepository,
                mongoTemplate, clock, true);
        disabled = new MentorshipRecoveryService(requestRepository, mentorshipRepository,
                mongoTemplate, clock, false);
    }

    @Test
    @DisplayName("an ACCEPTING request with a mentorship is rolled forward to ACCEPTED")
    void rollsForwardWhenMentorshipExists() {
        when(requestRepository.findByStatus(MentorshipRequestStatus.ACCEPTING))
                .thenReturn(List.of(request()));
        when(mentorshipRepository.existsByPairKeyAndStatus(PAIR_KEY, MentorshipStatus.ACTIVE))
                .thenReturn(true);
        UpdateResult updated = modified(1);
        when(mongoTemplate.updateFirst(any(), any(), eq(MentorshipRequest.class))).thenReturn(updated);

        assertThat(enabled.recoverInterruptedAcceptances()).isEqualTo(1);
        verify(mentorshipRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("an ACCEPTING request without a mentorship is reopened to PENDING")
    void rollsBackWhenNoMentorshipExists() {
        when(requestRepository.findByStatus(MentorshipRequestStatus.ACCEPTING))
                .thenReturn(List.of(request()));
        when(mentorshipRepository.existsByPairKeyAndStatus(PAIR_KEY, MentorshipStatus.ACTIVE))
                .thenReturn(false);
        UpdateResult updated = modified(1);
        when(mongoTemplate.updateFirst(any(), any(), eq(MentorshipRequest.class))).thenReturn(updated);

        assertThat(enabled.recoverInterruptedAcceptances()).isEqualTo(1);
    }

    @Test
    @DisplayName("a PENDING request sharing a pair with an active mentorship is cancelled")
    void cancelsPendingBesideActiveMentorship() {
        when(requestRepository.findByStatus(MentorshipRequestStatus.PENDING))
                .thenReturn(List.of(request()));
        when(mentorshipRepository.existsByPairKeyAndStatus(PAIR_KEY, MentorshipStatus.ACTIVE))
                .thenReturn(true);
        UpdateResult updated = modified(1);
        when(mongoTemplate.updateFirst(any(), any(), eq(MentorshipRequest.class))).thenReturn(updated);

        assertThat(enabled.recoverInterruptedAcceptances()).isEqualTo(1);
    }

    @Test
    @DisplayName("a healthy database needs no repair and touches no mentorship")
    void doesNothingWhenThereIsNothingToRepair() {
        assertThat(enabled.recoverInterruptedAcceptances()).isZero();
        verify(mentorshipRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("recovery is skipped entirely when disabled")
    void disabledServiceDoesNothing() {
        disabled.onApplicationReady();

        verify(requestRepository, never()).findByStatus(any());
    }

    @Test
    @DisplayName("a failure during startup recovery is swallowed so the application still starts")
    void startupFailureIsSwallowed() {
        when(requestRepository.findByStatus(any())).thenThrow(new RuntimeException("mongo down"));

        assertThatCode(() -> enabled.onApplicationReady()).doesNotThrowAnyException();
    }

    private MentorshipRequest request() {
        MentorshipRequest request = new MentorshipRequest();
        request.setId(new ObjectId());
        request.setRequesterUserId(STUDENT_ID);
        request.setRecipientUserId(MENTOR_ID);
        request.setPairKey(PAIR_KEY);
        request.setStatus(MentorshipRequestStatus.PENDING);
        request.setCreatedAt(NOW);
        request.setUpdatedAt(NOW);
        return request;
    }

    private UpdateResult modified(long count) {
        UpdateResult result = mock(UpdateResult.class);
        when(result.getModifiedCount()).thenReturn(count);
        return result;
    }
}
