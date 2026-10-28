package com.ubit.mentorshiphub.mentorship.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.result.UpdateResult;
import com.ubit.mentorshiphub.common.exception.InsufficientRoleException;
import com.ubit.mentorshiphub.common.exception.InvalidRequestException;
import com.ubit.mentorshiphub.common.exception.MentorshipConflictException;
import com.ubit.mentorshiphub.common.exception.MentorshipForbiddenException;
import com.ubit.mentorshiphub.common.exception.ResourceNotFoundException;
import com.ubit.mentorshiphub.common.exception.VerificationRequiredException;
import com.ubit.mentorshiphub.common.security.AccountAccess;
import com.ubit.mentorshiphub.mentorship.dto.MentorshipRequestCreateRequest;
import com.ubit.mentorshiphub.mentorship.dto.MentorshipRequestResponse;
import com.ubit.mentorshiphub.mentorship.dto.MentorshipResponse;
import com.ubit.mentorshiphub.mentorship.model.Mentorship;
import com.ubit.mentorshiphub.mentorship.model.MentorshipRequest;
import com.ubit.mentorshiphub.mentorship.model.MentorshipRequestStatus;
import com.ubit.mentorshiphub.mentorship.model.MentorshipStatus;
import com.ubit.mentorshiphub.mentorship.model.PairKeys;
import com.ubit.mentorshiphub.mentorship.repository.MentorshipRepository;
import com.ubit.mentorshiphub.mentorship.repository.MentorshipRequestRepository;
import com.ubit.mentorshiphub.profile.model.Profile;
import com.ubit.mentorshiphub.profile.repository.ProfileRepository;
import com.ubit.mentorshiphub.user.model.AppRole;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.user.model.VerificationStatus;
import com.ubit.mentorshiphub.user.service.UserService;

/**
 * The mentorship rules, one at a time, against mocks.
 *
 * <p>Lenient strictness is deliberate: most scenarios stub a shared set of
 * collaborators (the current account, a recipient lookup, a profile) and only
 * some of them are reached per branch, so flagging an unreached stub as an error
 * would be noise rather than a signal.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MentorshipServiceTest {

    private static final ObjectId STUDENT_ID = new ObjectId("64b0000000000000000000a1");
    private static final ObjectId MENTOR_ID = new ObjectId("64b0000000000000000000b2");
    private static final ObjectId OTHER_ID = new ObjectId("64b0000000000000000000c3");
    private static final String MESSAGE = "I would really value your guidance on backend engineering.";
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    @Mock private MentorshipRequestRepository requestRepository;
    @Mock private MentorshipRepository mentorshipRepository;
    @Mock private UserService userService;
    @Mock private ProfileRepository profileRepository;
    @Mock private AccountAccess accountAccess;
    @Mock private MongoTemplate mongoTemplate;

    private MentorshipService service;

    @BeforeEach
    void setUp() {
        service = new MentorshipService(requestRepository, mentorshipRepository, userService,
                profileRepository, accountAccess, mongoTemplate, Clock.fixed(NOW, ZoneId.of("UTC")));
    }

    // ---------------------------------------------------------------- create

    @Test
    @DisplayName("a verified student's request is stored PENDING with the canonical pair key")
    void createStoresPendingWithCanonicalPairKey() {
        User student = verified(AppRole.STUDENT, STUDENT_ID);
        User mentor = verified(AppRole.ALUMNI, MENTOR_ID);
        when(accountAccess.requireCurrentUser()).thenReturn(student);
        when(userService.findById(MENTOR_ID)).thenReturn(Optional.of(mentor));
        when(profileRepository.findByUserId(MENTOR_ID)).thenReturn(Optional.of(profile(true)));
        when(requestRepository.save(any())).thenAnswer(invocation -> {
            MentorshipRequest request = invocation.getArgument(0);
            request.setId(new ObjectId());
            return request;
        });

        MentorshipRequestResponse response = service.createRequest(
                new MentorshipRequestCreateRequest(MENTOR_ID.toHexString(), "  " + MESSAGE + "  ", "  Learn Spring  "));

        assertThat(response.status()).isEqualTo(MentorshipRequestStatus.PENDING);
        assertThat(response.requester().userId()).isEqualTo(STUDENT_ID.toHexString());
        assertThat(response.recipient().userId()).isEqualTo(MENTOR_ID.toHexString());
        assertThat(response.message()).isEqualTo(MESSAGE);
        assertThat(response.goal()).isEqualTo("Learn Spring");
        assertThat(response.createdAt()).isEqualTo(NOW);

        ArgumentCaptor<MentorshipRequest> saved = ArgumentCaptor.forClass(MentorshipRequest.class);
        verify(requestRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(MentorshipRequestStatus.PENDING);
        assertThat(saved.getValue().getPairKey()).isEqualTo(PairKeys.of(STUDENT_ID, MENTOR_ID));
    }

    @Test
    @DisplayName("a request to yourself is refused as bad input")
    void selfRequestIsRefused() {
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.STUDENT, STUDENT_ID));

        assertThatThrownBy(() -> service.createRequest(
                new MentorshipRequestCreateRequest(STUDENT_ID.toHexString(), MESSAGE, null)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("a malformed recipient id is a 400, not a lookup miss")
    void malformedRecipientIsBadInput() {
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.STUDENT, STUDENT_ID));

        assertThatThrownBy(() -> service.createRequest(
                new MentorshipRequestCreateRequest("not-an-object-id", MESSAGE, null)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("an unknown recipient is a 404")
    void unknownRecipientIsNotFound() {
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.STUDENT, STUDENT_ID));
        when(userService.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createRequest(
                new MentorshipRequestCreateRequest(MENTOR_ID.toHexString(), MESSAGE, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("only alumni and seniors may be asked to mentor")
    void recipientMustHoldAMentorRole() {
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.STUDENT, STUDENT_ID));
        when(userService.findById(MENTOR_ID)).thenReturn(Optional.of(verified(AppRole.STUDENT, MENTOR_ID)));

        assertThatThrownBy(() -> service.createRequest(
                new MentorshipRequestCreateRequest(MENTOR_ID.toHexString(), MESSAGE, null)))
                .isInstanceOf(MentorshipConflictException.class);
    }

    @Test
    @DisplayName("an unverified mentor is not available")
    void recipientMustBeVerified() {
        User pending = new User();
        pending.setId(MENTOR_ID);
        pending.setRole(AppRole.ALUMNI);
        pending.setVerificationStatus(VerificationStatus.PENDING);
        pending.setEnabled(true);
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.STUDENT, STUDENT_ID));
        when(userService.findById(MENTOR_ID)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.createRequest(
                new MentorshipRequestCreateRequest(MENTOR_ID.toHexString(), MESSAGE, null)))
                .isInstanceOf(MentorshipConflictException.class);
    }

    @Test
    @DisplayName("a mentor who has not opted in is not available")
    void recipientMustOptIn() {
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.STUDENT, STUDENT_ID));
        when(userService.findById(MENTOR_ID)).thenReturn(Optional.of(verified(AppRole.ALUMNI, MENTOR_ID)));
        when(profileRepository.findByUserId(MENTOR_ID)).thenReturn(Optional.of(profile(false)));

        assertThatThrownBy(() -> service.createRequest(
                new MentorshipRequestCreateRequest(MENTOR_ID.toHexString(), MESSAGE, null)))
                .isInstanceOf(MentorshipConflictException.class);
    }

    @Test
    @DisplayName("a missing legacy mentorshipAvailable flag fails closed")
    void recipientWithoutFlagIsNotAvailable() {
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.STUDENT, STUDENT_ID));
        when(userService.findById(MENTOR_ID)).thenReturn(Optional.of(verified(AppRole.ALUMNI, MENTOR_ID)));
        when(profileRepository.findByUserId(MENTOR_ID)).thenReturn(Optional.of(profile(null)));

        assertThatThrownBy(() -> service.createRequest(
                new MentorshipRequestCreateRequest(MENTOR_ID.toHexString(), MESSAGE, null)))
                .isInstanceOf(MentorshipConflictException.class);
    }

    @Test
    @DisplayName("a duplicate pending request for the pair is a conflict")
    void duplicatePendingIsConflict() {
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.STUDENT, STUDENT_ID));
        when(userService.findById(MENTOR_ID)).thenReturn(Optional.of(verified(AppRole.ALUMNI, MENTOR_ID)));
        when(profileRepository.findByUserId(MENTOR_ID)).thenReturn(Optional.of(profile(true)));
        when(requestRepository.existsByPairKeyAndStatus(any(), eq(MentorshipRequestStatus.PENDING)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createRequest(
                new MentorshipRequestCreateRequest(MENTOR_ID.toHexString(), MESSAGE, null)))
                .isInstanceOf(MentorshipConflictException.class);
    }

    @Test
    @DisplayName("an existing active mentorship for the pair blocks a new request")
    void activeMentorshipBlocksRequest() {
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.STUDENT, STUDENT_ID));
        when(userService.findById(MENTOR_ID)).thenReturn(Optional.of(verified(AppRole.ALUMNI, MENTOR_ID)));
        when(profileRepository.findByUserId(MENTOR_ID)).thenReturn(Optional.of(profile(true)));
        when(mentorshipRepository.existsByPairKeyAndStatus(any(), eq(MentorshipStatus.ACTIVE)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createRequest(
                new MentorshipRequestCreateRequest(MENTOR_ID.toHexString(), MESSAGE, null)))
                .isInstanceOf(MentorshipConflictException.class);
    }

    @Test
    @DisplayName("the requester must be a verified participant")
    void requesterMustBeVerified() {
        User unverified = new User();
        unverified.setId(STUDENT_ID);
        unverified.setRole(AppRole.STUDENT);
        unverified.setVerificationStatus(VerificationStatus.PENDING);
        unverified.setEnabled(true);
        when(accountAccess.requireCurrentUser()).thenReturn(unverified);

        assertThatThrownBy(() -> service.createRequest(
                new MentorshipRequestCreateRequest(MENTOR_ID.toHexString(), MESSAGE, null)))
                .isInstanceOf(VerificationRequiredException.class);
    }

    @Test
    @DisplayName("only students initiate requests")
    void requesterMustBeAStudent() {
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.ALUMNI, OTHER_ID));

        assertThatThrownBy(() -> service.createRequest(
                new MentorshipRequestCreateRequest(MENTOR_ID.toHexString(), MESSAGE, null)))
                .isInstanceOf(InsufficientRoleException.class);
    }

    @Test
    @DisplayName("a message shorter than the minimum is bad input")
    void messageTooShortIsBadInput() {
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.STUDENT, STUDENT_ID));

        assertThatThrownBy(() -> service.createRequest(
                new MentorshipRequestCreateRequest(MENTOR_ID.toHexString(), "too short", null)))
                .isInstanceOf(InvalidRequestException.class);
    }

    // ---------------------------------------------------------------- accept

    @Test
    @DisplayName("accepting creates the active mentorship and flips the request")
    void acceptCreatesMentorship() {
        User mentor = verified(AppRole.ALUMNI, MENTOR_ID);
        User student = verified(AppRole.STUDENT, STUDENT_ID);
        MentorshipRequest request = pendingRequest();
        when(accountAccess.requireCurrentUser()).thenReturn(mentor);
        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(userService.findById(MENTOR_ID)).thenReturn(Optional.of(mentor));
        when(userService.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(profileRepository.findByUserId(MENTOR_ID)).thenReturn(Optional.of(profile(true)));
        when(mentorshipRepository.save(any())).thenAnswer(invocation -> {
            Mentorship mentorship = invocation.getArgument(0);
            mentorship.setId(new ObjectId());
            return mentorship;
        });
        UpdateResult flip = modified(1);
        when(mongoTemplate.updateFirst(any(), any(), eq(MentorshipRequest.class))).thenReturn(flip);

        MentorshipResponse response = service.accept(request.getId().toHexString());

        assertThat(response.status()).isEqualTo(MentorshipStatus.ACTIVE);
        assertThat(response.mentor().userId()).isEqualTo(MENTOR_ID.toHexString());
        assertThat(response.mentee().userId()).isEqualTo(STUDENT_ID.toHexString());
        assertThat(response.startedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("the requester is forbidden from accepting their own request")
    void requesterCannotAccept() {
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.STUDENT, STUDENT_ID));
        when(requestRepository.findById(any())).thenReturn(Optional.of(pendingRequest()));

        assertThatThrownBy(() -> service.accept("64b0000000000000000000d4"))
                .isInstanceOf(MentorshipForbiddenException.class);
    }

    @Test
    @DisplayName("a non-participant sees a not-found rather than a forbidden")
    void outsiderCannotSeeRequest() {
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.ALUMNI, OTHER_ID));
        when(requestRepository.findById(any())).thenReturn(Optional.of(pendingRequest()));

        assertThatThrownBy(() -> service.accept("64b0000000000000000000d4"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("an already-answered request cannot be accepted again")
    void acceptNonPendingIsConflict() {
        MentorshipRequest request = pendingRequest();
        request.setStatus(MentorshipRequestStatus.REJECTED);
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.ALUMNI, MENTOR_ID));
        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.accept(request.getId().toHexString()))
                .isInstanceOf(MentorshipConflictException.class);
    }

    @Test
    @DisplayName("losing the PENDING -> ACCEPTING claim is a conflict and writes no mentorship")
    void acceptLosesClaimRaceIsConflict() {
        User mentor = verified(AppRole.ALUMNI, MENTOR_ID);
        User student = verified(AppRole.STUDENT, STUDENT_ID);
        MentorshipRequest request = pendingRequest();
        when(accountAccess.requireCurrentUser()).thenReturn(mentor);
        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(userService.findById(MENTOR_ID)).thenReturn(Optional.of(mentor));
        when(userService.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(profileRepository.findByUserId(MENTOR_ID)).thenReturn(Optional.of(profile(true)));
        UpdateResult claimLost = modified(0);
        when(mongoTemplate.updateFirst(any(), any(), eq(MentorshipRequest.class))).thenReturn(claimLost);

        assertThatThrownBy(() -> service.accept(request.getId().toHexString()))
                .isInstanceOf(MentorshipConflictException.class);
        verify(mentorshipRepository, never()).save(any());
    }

    @Test
    @DisplayName("a duplicate active mentorship during accept reopens the request to PENDING")
    void acceptDuplicateMentorshipReopensRequest() {
        harnessForAccept();
        when(mentorshipRepository.save(any()))
                .thenThrow(new DuplicateKeyException("uk_mentorships_active_pair"));

        assertThatThrownBy(() -> service.accept("64b0000000000000000000d4"))
                .isInstanceOf(MentorshipConflictException.class);

        // Phase 1 claimed the request, phase 2 failed, the rollback updates it again.
        verify(mongoTemplate, times(2)).updateFirst(any(), any(), eq(MentorshipRequest.class));
    }

    @Test
    @DisplayName("if finalization cannot complete, the mentorship is kept for recovery rather than deleted")
    void acceptFinalizationFailureKeepsMentorshipForRecovery() {
        User mentor = verified(AppRole.ALUMNI, MENTOR_ID);
        User student = verified(AppRole.STUDENT, STUDENT_ID);
        MentorshipRequest request = pendingRequest();
        when(accountAccess.requireCurrentUser()).thenReturn(mentor);
        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(userService.findById(MENTOR_ID)).thenReturn(Optional.of(mentor));
        when(userService.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(profileRepository.findByUserId(MENTOR_ID)).thenReturn(Optional.of(profile(true)));
        when(mentorshipRepository.save(any())).thenAnswer(invocation -> {
            Mentorship mentorship = invocation.getArgument(0);
            if (mentorship != null) {
                mentorship.setId(new ObjectId());
            }
            return mentorship;
        });
        // Phase 1 wins the claim, phase 3 loses the finalization.
        UpdateResult claimWon = modified(1);
        UpdateResult finalLost = modified(0);
        when(mongoTemplate.updateFirst(any(), any(), eq(MentorshipRequest.class)))
                .thenReturn(claimWon, finalLost);

        assertThatThrownBy(() -> service.accept(request.getId().toHexString()))
                .isInstanceOf(MentorshipConflictException.class);
        verify(mentorshipRepository, never()).deleteById(any());
    }

    // --------------------------------------------------- reject and cancel

    @Test
    @DisplayName("the recipient rejects a pending request")
    void recipientRejects() {
        MentorshipRequest request = pendingRequest();
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.ALUMNI, MENTOR_ID));
        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        UpdateResult flip = modified(1);
        when(mongoTemplate.updateFirst(any(), any(), eq(MentorshipRequest.class))).thenReturn(flip);

        assertThat(service.reject(request.getId().toHexString()).status())
                .isEqualTo(MentorshipRequestStatus.REJECTED);
    }

    @Test
    @DisplayName("the requester is forbidden from rejecting their own request")
    void requesterCannotReject() {
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.STUDENT, STUDENT_ID));
        when(requestRepository.findById(any())).thenReturn(Optional.of(pendingRequest()));

        assertThatThrownBy(() -> service.reject("64b0000000000000000000d4"))
                .isInstanceOf(MentorshipForbiddenException.class);
    }

    @Test
    @DisplayName("the requester cancels a pending request")
    void requesterCancels() {
        MentorshipRequest request = pendingRequest();
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.STUDENT, STUDENT_ID));
        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        UpdateResult flip = modified(1);
        when(mongoTemplate.updateFirst(any(), any(), eq(MentorshipRequest.class))).thenReturn(flip);

        assertThat(service.cancel(request.getId().toHexString()).status())
                .isEqualTo(MentorshipRequestStatus.CANCELLED);
    }

    @Test
    @DisplayName("the recipient is forbidden from cancelling a request aimed at them")
    void recipientCannotCancel() {
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.ALUMNI, MENTOR_ID));
        when(requestRepository.findById(any())).thenReturn(Optional.of(pendingRequest()));

        assertThatThrownBy(() -> service.cancel("64b0000000000000000000d4"))
                .isInstanceOf(MentorshipForbiddenException.class);
    }

    // ---------------------------------------------------------------- end

    @Test
    @DisplayName("a participant ends an active mentorship")
    void participantEndsMentorship() {
        Mentorship mentorship = activeMentorship();
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.STUDENT, STUDENT_ID));
        when(mentorshipRepository.findById(mentorship.getId())).thenReturn(Optional.of(mentorship));
        UpdateResult end = modified(1);
        when(mongoTemplate.updateFirst(any(), any(), eq(Mentorship.class))).thenReturn(end);

        MentorshipResponse response = service.end(mentorship.getId().toHexString(),
                new com.ubit.mentorshiphub.mentorship.dto.EndMentorshipRequest("Goals met"));

        assertThat(response.status()).isEqualTo(MentorshipStatus.ENDED);
        assertThat(response.endedByUserId()).isEqualTo(STUDENT_ID.toHexString());
        assertThat(response.endReason()).isEqualTo("Goals met");
    }

    @Test
    @DisplayName("a non-participant cannot see the mentorship at all")
    void outsiderCannotEnd() {
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.ALUMNI, OTHER_ID));
        when(mentorshipRepository.findById(any())).thenReturn(Optional.of(activeMentorship()));

        assertThatThrownBy(() -> service.end("64b0000000000000000000e5", null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("an ended mentorship cannot be ended again")
    void cannotEndTwice() {
        Mentorship mentorship = activeMentorship();
        mentorship.setStatus(MentorshipStatus.ENDED);
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.STUDENT, STUDENT_ID));
        when(mentorshipRepository.findById(mentorship.getId())).thenReturn(Optional.of(mentorship));

        assertThatThrownBy(() -> service.end(mentorship.getId().toHexString(), null))
                .isInstanceOf(MentorshipConflictException.class);
    }

    // ---------------------------------------------------------------- listing

    @Test
    @DisplayName("a page size above the cap is refused")
    void pageSizeIsCapped() {
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.STUDENT, STUDENT_ID));

        assertThatThrownBy(() -> service.listSent(0, 51))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("active mentorships are listed for the caller")
    void listsActiveMentorships() {
        User student = verified(AppRole.STUDENT, STUDENT_ID);
        Mentorship mentorship = activeMentorship();
        when(accountAccess.requireCurrentUser()).thenReturn(student);
        when(userService.findById(any())).thenReturn(Optional.of(student));
        when(mentorshipRepository.findActiveForUser(eq(STUDENT_ID), any()))
                .thenReturn(new PageImpl<>(java.util.List.of(mentorship), PageRequest.of(0, 20), 1));

        Page<MentorshipResponse> page = service.listActive(0, 20);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).id()).isEqualTo(mentorship.getId().toHexString());
    }

    // ---------------------------------------------------------------- hardening

    @Test
    @DisplayName("a duplicate key from the pending-pair index surfaces as a conflict, not a 500")
    void createDuplicateKeyIsConflict() {
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.STUDENT, STUDENT_ID));
        when(userService.findById(MENTOR_ID)).thenReturn(Optional.of(verified(AppRole.ALUMNI, MENTOR_ID)));
        when(profileRepository.findByUserId(MENTOR_ID)).thenReturn(Optional.of(profile(true)));
        when(requestRepository.save(any()))
                .thenThrow(new DuplicateKeyException("uk_mentorship_requests_pending_pair"));

        assertThatThrownBy(() -> service.createRequest(
                new MentorshipRequestCreateRequest(MENTOR_ID.toHexString(), MESSAGE, null)))
                .isInstanceOf(MentorshipConflictException.class);
    }

    @Test
    @DisplayName("a duplicate key from the active-pair index surfaces as a conflict")
    void acceptDuplicateMentorshipIsConflict() {
        harnessForAccept();
        when(mentorshipRepository.save(any()))
                .thenThrow(new DuplicateKeyException("uk_mentorships_active_pair"));

        assertThatThrownBy(() -> service.accept("64b0000000000000000000d4"))
                .isInstanceOf(MentorshipConflictException.class);
    }

    @Test
    @DisplayName("accept re-checks that the mentor is still accepting requests")
    void acceptRefusedWhenMentorUnavailable() {
        harnessForAccept();
        when(profileRepository.findByUserId(MENTOR_ID)).thenReturn(Optional.of(profile(false)));

        assertThatThrownBy(() -> service.accept("64b0000000000000000000d4"))
                .isInstanceOf(MentorshipConflictException.class);
    }

    @Test
    @DisplayName("accept re-checks that the mentee is still a verified student")
    void acceptRefusedWhenMenteeNoLongerStudent() {
        harnessForAccept();
        when(userService.findById(STUDENT_ID)).thenReturn(Optional.of(verified(AppRole.ALUMNI, STUDENT_ID)));

        assertThatThrownBy(() -> service.accept("64b0000000000000000000d4"))
                .isInstanceOf(MentorshipConflictException.class);
    }

    @Test
    @DisplayName("accept re-checks that the pair has no active mentorship yet")
    void acceptRefusedWhenActiveExists() {
        harnessForAccept();
        when(mentorshipRepository.existsByPairKeyAndStatus(any(), eq(MentorshipStatus.ACTIVE)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.accept("64b0000000000000000000d4"))
                .isInstanceOf(MentorshipConflictException.class);
    }

    @Test
    @DisplayName("losing the reject race is a conflict")
    void rejectLostRaceIsConflict() {
        MentorshipRequest request = pendingRequest();
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.ALUMNI, MENTOR_ID));
        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        UpdateResult flip = modified(0);
        when(mongoTemplate.updateFirst(any(), any(), eq(MentorshipRequest.class))).thenReturn(flip);

        assertThatThrownBy(() -> service.reject(request.getId().toHexString()))
                .isInstanceOf(MentorshipConflictException.class);
    }

    @Test
    @DisplayName("losing the cancel race is a conflict")
    void cancelLostRaceIsConflict() {
        MentorshipRequest request = pendingRequest();
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.STUDENT, STUDENT_ID));
        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        UpdateResult flip = modified(0);
        when(mongoTemplate.updateFirst(any(), any(), eq(MentorshipRequest.class))).thenReturn(flip);

        assertThatThrownBy(() -> service.cancel(request.getId().toHexString()))
                .isInstanceOf(MentorshipConflictException.class);
    }

    @Test
    @DisplayName("losing the end race is a conflict")
    void endLostRaceIsConflict() {
        Mentorship mentorship = activeMentorship();
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.STUDENT, STUDENT_ID));
        when(mentorshipRepository.findById(mentorship.getId())).thenReturn(Optional.of(mentorship));
        UpdateResult end = modified(0);
        when(mongoTemplate.updateFirst(any(), any(), eq(Mentorship.class))).thenReturn(end);

        assertThatThrownBy(() -> service.end(mentorship.getId().toHexString(), null))
                .isInstanceOf(MentorshipConflictException.class);
    }

    @Test
    @DisplayName("the message and goal accept their exact boundary lengths")
    void acceptsBoundaryLengths() {
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.STUDENT, STUDENT_ID));
        when(userService.findById(MENTOR_ID)).thenReturn(Optional.of(verified(AppRole.ALUMNI, MENTOR_ID)));
        when(profileRepository.findByUserId(MENTOR_ID)).thenReturn(Optional.of(profile(true)));
        when(requestRepository.save(any())).thenAnswer(invocation -> {
            MentorshipRequest request = invocation.getArgument(0);
            request.setId(new ObjectId());
            return request;
        });

        assertThat(service.createRequest(new MentorshipRequestCreateRequest(
                MENTOR_ID.toHexString(), "x".repeat(10), "y".repeat(500)))).isNotNull();
        assertThat(service.createRequest(new MentorshipRequestCreateRequest(
                MENTOR_ID.toHexString(), "x".repeat(2000), null))).isNotNull();
    }

    @Test
    @DisplayName("a message over 2000 characters is refused")
    void rejectsOverlongMessage() {
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.STUDENT, STUDENT_ID));

        assertThatThrownBy(() -> service.createRequest(new MentorshipRequestCreateRequest(
                MENTOR_ID.toHexString(), "x".repeat(2001), null)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("a goal over 500 characters is refused")
    void rejectsOverlongGoal() {
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.STUDENT, STUDENT_ID));

        assertThatThrownBy(() -> service.createRequest(new MentorshipRequestCreateRequest(
                MENTOR_ID.toHexString(), MESSAGE, "y".repeat(501))))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("a participant can fetch their own request")
    void participantCanGetRequest() {
        MentorshipRequest request = pendingRequest();
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.STUDENT, STUDENT_ID));
        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        assertThat(service.getRequest(request.getId().toHexString()).id())
                .isEqualTo(request.getId().toHexString());
    }

    @Test
    @DisplayName("an outsider gets a not-found for a request they are not party to")
    void outsiderGetRequestIsNotFound() {
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.ALUMNI, OTHER_ID));
        when(requestRepository.findById(any())).thenReturn(Optional.of(pendingRequest()));

        assertThatThrownBy(() -> service.getRequest("64b0000000000000000000d4"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("sent requests are listed for the caller")
    void listsSentRequests() {
        when(accountAccess.requireCurrentUser()).thenReturn(verified(AppRole.STUDENT, STUDENT_ID));
        when(requestRepository.findByRequesterUserId(eq(STUDENT_ID), any()))
                .thenReturn(new PageImpl<>(List.of(pendingRequest()), PageRequest.of(0, 20), 1));

        Page<MentorshipRequestResponse> page = service.listSent(0, 20);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).status()).isEqualTo(MentorshipRequestStatus.PENDING);
    }

    // ---------------------------------------------------------------- helpers

    private void harnessForAccept() {
        User mentor = verified(AppRole.ALUMNI, MENTOR_ID);
        User student = verified(AppRole.STUDENT, STUDENT_ID);
        MentorshipRequest request = pendingRequest();
        when(accountAccess.requireCurrentUser()).thenReturn(mentor);
        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(userService.findById(MENTOR_ID)).thenReturn(Optional.of(mentor));
        when(userService.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(profileRepository.findByUserId(MENTOR_ID)).thenReturn(Optional.of(profile(true)));
        when(mentorshipRepository.save(any())).thenAnswer(invocation -> {
            Mentorship mentorship = invocation.getArgument(0);
            if (mentorship != null) {
                mentorship.setId(new ObjectId());
            }
            return mentorship;
        });
        UpdateResult flip = modified(1);
        when(mongoTemplate.updateFirst(any(), any(), eq(MentorshipRequest.class))).thenReturn(flip);
    }

    private UpdateResult modified(long count) {
        UpdateResult result = mock(UpdateResult.class);
        when(result.getModifiedCount()).thenReturn(count);
        return result;
    }

    private User verified(AppRole role, ObjectId id) {
        User user = new User();
        user.setId(id);
        user.setName("User " + id.toHexString().substring(20));
        user.setRole(role);
        user.setVerificationStatus(VerificationStatus.VERIFIED);
        user.setEnabled(true);
        return user;
    }

    private Profile profile(Boolean mentorshipAvailable) {
        Profile profile = new Profile();
        profile.setMentorshipAvailable(mentorshipAvailable);
        profile.setAvatarGridFsId("avatar-" + MENTOR_ID.toHexString());
        return profile;
    }

    private MentorshipRequest pendingRequest() {
        MentorshipRequest request = new MentorshipRequest();
        request.setId(new ObjectId("64b0000000000000000000d4"));
        request.setRequesterUserId(STUDENT_ID);
        request.setRecipientUserId(MENTOR_ID);
        request.setMessage(MESSAGE);
        request.setStatus(MentorshipRequestStatus.PENDING);
        request.setPairKey(PairKeys.of(STUDENT_ID, MENTOR_ID));
        request.setCreatedAt(NOW);
        request.setUpdatedAt(NOW);
        return request;
    }

    private Mentorship activeMentorship() {
        Mentorship mentorship = new Mentorship();
        mentorship.setId(new ObjectId("64b0000000000000000000e5"));
        mentorship.setMentorUserId(MENTOR_ID);
        mentorship.setMenteeUserId(STUDENT_ID);
        mentorship.setRequestId(new ObjectId("64b0000000000000000000d4"));
        mentorship.setStatus(MentorshipStatus.ACTIVE);
        mentorship.setPairKey(PairKeys.of(STUDENT_ID, MENTOR_ID));
        mentorship.setStartedAt(NOW);
        mentorship.setCreatedAt(NOW);
        mentorship.setUpdatedAt(NOW);
        return mentorship;
    }
}
