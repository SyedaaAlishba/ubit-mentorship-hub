package com.ubit.mentorshiphub.admin.service;

import com.ubit.mentorshiphub.admin.dto.AdminVerificationApplicationResponse;
import com.ubit.mentorshiphub.admin.dto.AdminVerificationDecisionRequest;
import com.ubit.mentorshiphub.admin.dto.AdminVerificationDecisionRequest.Decision;
import com.ubit.mentorshiphub.admin.dto.AdminVerificationDecisionResponse;
import com.ubit.mentorshiphub.common.exception.InsufficientRoleException;
import com.ubit.mentorshiphub.common.exception.InvalidRequestException;
import com.ubit.mentorshiphub.common.exception.ResourceNotFoundException;
import com.ubit.mentorshiphub.common.exception.VerificationRequiredException;
import com.ubit.mentorshiphub.common.exception.VerificationStateConflictException;
import com.ubit.mentorshiphub.common.security.AccountAccess;
import com.ubit.mentorshiphub.user.model.AppRole;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.user.model.VerificationStatus;
import com.ubit.mentorshiphub.user.service.UserService;
import com.ubit.mentorshiphub.verification.model.ApplicationStatus;
import com.ubit.mentorshiphub.verification.model.ApplicationType;
import com.ubit.mentorshiphub.verification.model.Program;
import com.ubit.mentorshiphub.verification.model.VerificationApplication;
import com.ubit.mentorshiphub.verification.repository.VerificationApplicationRepository;
import com.ubit.mentorshiphub.verification.storage.VerificationDocumentService;
import com.ubit.mentorshiphub.verification.storage.VerificationDocumentService.ReviewableDocument;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The administrator review service, with the rules that matter most under test:
 * only an administrator reaches the queue, a rejection needs a reason, the
 * account and application move together, and a second decision is refused.
 *
 * <p>The concurrency guard itself is a conditional MongoDB update, so it cannot
 * be proved here against a mock; {@code AdminVerificationIntegrationTest} covers
 * that against a real database.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");

    @Mock private VerificationApplicationRepository applicationRepository;
    @Mock private VerificationDocumentService documentService;
    @Mock private UserService userService;
    @Mock private AccountAccess accountAccess;
    @Mock private MongoTemplate mongoTemplate;

    private AdminVerificationService service;

    private final ObjectId applicationId = new ObjectId();
    private final ObjectId applicantId = new ObjectId();
    private final ObjectId adminId = new ObjectId();

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW, ZoneId.of("UTC"));
        service = new AdminVerificationService(applicationRepository, documentService,
                userService, accountAccess, mongoTemplate, fixedClock);
        when(accountAccess.requireCurrentUser()).thenReturn(admin());
    }

    // ------------------------------------------------------------- authorization

    @Test
    @DisplayName("a verified administrator sees the pending queue")
    void administratorSeesQueue() {
        when(applicationRepository.findByStatus(eq(ApplicationStatus.PENDING), any()))
                .thenReturn(new PageImpl<>(List.of(studentApplication(ApplicationStatus.PENDING))));
        when(userService.findById(applicantId)).thenReturn(Optional.of(applicant(VerificationStatus.PENDING)));

        Page<AdminVerificationApplicationResponse> page = service.listApplications(0, 20, null);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).applicantName()).isEqualTo("Ali Raza");
        assertThat(page.getContent().get(0).applicantEmail()).isEqualTo("ali@example.com");
        assertThat(page.getContent().get(0).documentUrl()).isEqualTo(
                "/api/admin/verification/applications/" + applicationId.toHexString() + "/document");
    }

    @Test
    @DisplayName("a verified student is refused before any application is loaded")
    void nonAdministratorIsRefused() {
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.STUDENT, VerificationStatus.VERIFIED));

        assertThatThrownBy(() -> service.listApplications(0, 20, null))
                .isInstanceOf(InsufficientRoleException.class);

        verify(applicationRepository, never()).findByStatus(any(), any());
    }

    @Test
    @DisplayName("an unverified administrator claim is refused even with the ADMIN role")
    void unverifiedAdminIsRefused() {
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.ADMIN, VerificationStatus.PENDING));

        assertThatThrownBy(() -> service.listApplications(0, 20, null))
                .isInstanceOf(VerificationRequiredException.class)
                .hasMessageContaining("must be verified");
    }

    // ------------------------------------------------------------------ listing

    @Test
    @DisplayName("an unknown status filter is a 400, not an empty page")
    void unknownStatusIsRefused() {
        assertThatThrownBy(() -> service.listApplications(0, 20, "MAYBE"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("an oversized page is refused the same way the rest of the API refuses it")
    void oversizedPageIsRefused() {
        assertThatThrownBy(() -> service.listApplications(0, 51, null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("the queue is requested oldest-first, so the earliest claim is reviewed first")
    void queueIsOldestFirst() {
        when(applicationRepository.findByStatus(eq(ApplicationStatus.PENDING), any()))
                .thenReturn(Page.empty());

        service.listApplications(0, 20, null);

        var pageable = org.mockito.ArgumentCaptor.forClass(PageRequest.class);
        verify(applicationRepository).findByStatus(eq(ApplicationStatus.PENDING), pageable.capture());
        assertThat(pageable.getValue().getSort())
                .containsExactly(
                        org.springframework.data.domain.Sort.Order.asc("submittedAt"),
                        org.springframework.data.domain.Sort.Order.asc("_id"));
    }

    @Test
    @DisplayName("a decided row never exposes the GridFS id, only the review URL")
    void neverExposesDocumentId() {
        when(applicationRepository.findByStatus(eq(ApplicationStatus.PENDING), any()))
                .thenReturn(new PageImpl<>(List.of(studentApplication(ApplicationStatus.PENDING))));
        when(userService.findById(applicantId)).thenReturn(Optional.of(applicant(VerificationStatus.PENDING)));

        AdminVerificationApplicationResponse response =
                service.listApplications(0, 20, null).getContent().get(0);

        assertThat(response.documentUrl()).startsWith("/api/admin/verification/applications/");
        assertThat(java.util.Arrays.stream(AdminVerificationApplicationResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .doesNotContain("documentId", "googleId");
    }

    // ----------------------------------------------------------------- document

    @Test
    @DisplayName("an administrator opens the proof that belongs to the application")
    void opensOwnDocument() {
        when(applicationRepository.findById(applicationId))
                .thenReturn(Optional.of(studentApplication(ApplicationStatus.PENDING)));
        when(documentService.openForReview(any()))
                .thenReturn(Optional.of(new ReviewableDocument(
                        new ByteArrayInputStream(new byte[]{1}), "image/png", "card.png", 1)));

        AdminVerificationService.DocumentDownload download = service.openDocument(applicationId.toHexString());

        assertThat(download.contentType()).isEqualTo("image/png");
        assertThat(download.contentDisposition()).isEqualTo("inline; filename=\"card.png\"");
    }

    @Test
    @DisplayName("a PDF is offered as an attachment, never rendered inline")
    void pdfIsAttachment() {
        when(applicationRepository.findById(applicationId))
                .thenReturn(Optional.of(studentApplication(ApplicationStatus.PENDING)));
        when(documentService.openForReview(any()))
                .thenReturn(Optional.of(new ReviewableDocument(
                        new ByteArrayInputStream(new byte[]{1}), "application/pdf", "transcript.pdf", 1)));

        assertThat(service.openDocument(applicationId.toHexString()).contentDisposition())
                .startsWith("attachment;");
    }

    @Test
    @DisplayName("an application without a document - every alumni claim - is a 404")
    void missingDocumentIsNotFound() {
        VerificationApplication alumni = studentApplication(ApplicationStatus.PENDING);
        alumni.setType(ApplicationType.ALUMNI);
        alumni.setDocumentId(null);
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(alumni));

        assertThatThrownBy(() -> service.openDocument(applicationId.toHexString()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("no uploaded document");
    }

    @Test
    @DisplayName("a document that vanished from GridFS is a 404, not a 500")
    void vanishedDocumentIsNotFound() {
        when(applicationRepository.findById(applicationId))
                .thenReturn(Optional.of(studentApplication(ApplicationStatus.PENDING)));
        when(documentService.openForReview(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.openDocument(applicationId.toHexString()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("a student cannot open any application's document")
    void nonAdministratorCannotOpenDocument() {
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.STUDENT, VerificationStatus.VERIFIED));

        assertThatThrownBy(() -> service.openDocument(applicationId.toHexString()))
                .isInstanceOf(InsufficientRoleException.class);

        verify(documentService, never()).openForReview(any());
    }

    // ---------------------------------------------------------------- decisions

    @Test
    @DisplayName("approving moves the application and the account together")
    void approveMovesBoth() {
        givenPendingApplication();
        User applicant = applicant(VerificationStatus.PENDING);
        when(userService.requireById(applicantId)).thenReturn(applicant);
        when(userService.applyVerificationDecision(applicant, VerificationStatus.VERIFIED))
                .thenAnswer(invocation -> {
                    applicant.setVerificationStatus(VerificationStatus.VERIFIED);
                    return applicant;
                });
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(VerificationApplication.class)))
                .thenReturn(decidedApplication(ApplicationStatus.VERIFIED, null));

        AdminVerificationDecisionResponse response =
                service.decide(applicationId.toHexString(), new AdminVerificationDecisionRequest(Decision.VERIFIED, null));

        assertThat(response.applicationStatus()).isEqualTo(ApplicationStatus.VERIFIED);
        assertThat(response.userVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        verify(userService).applyVerificationDecision(applicant, VerificationStatus.VERIFIED);
    }

    @Test
    @DisplayName("rejecting requires a reason")
    void rejectionRequiresReason() {
        givenPendingApplication();

        assertThatThrownBy(() -> service.decide(applicationId.toHexString(),
                new AdminVerificationDecisionRequest(Decision.REJECTED, "   ")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("reason");

        verify(mongoTemplate, never()).findAndModify(any(), any(), any(), eq(VerificationApplication.class));
    }

    @Test
    @DisplayName("a rejection records the trimmed reason and the reviewer's account id")
    void rejectionRecordsNote() {
        givenPendingApplication();
        User applicant = applicant(VerificationStatus.PENDING);
        when(userService.requireById(applicantId)).thenReturn(applicant);
        when(userService.applyVerificationDecision(applicant, VerificationStatus.REJECTED))
                .thenAnswer(invocation -> {
                    applicant.setVerificationStatus(VerificationStatus.REJECTED);
                    return applicant;
                });
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(VerificationApplication.class)))
                .thenReturn(decidedApplication(ApplicationStatus.REJECTED, "Enrolment year does not match."));

        AdminVerificationDecisionResponse response = service.decide(applicationId.toHexString(),
                new AdminVerificationDecisionRequest(Decision.REJECTED, "  Enrolment year does not match.  "));

        assertThat(response.reviewerNote()).isEqualTo("Enrolment year does not match.");
        assertThat(response.userVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
        verify(userService).applyVerificationDecision(applicant, VerificationStatus.REJECTED);
    }

    @Test
    @DisplayName("a second decision on the same application is a conflict")
    void repeatedDecisionIsConflict() {
        when(applicationRepository.findById(applicationId))
                .thenReturn(Optional.of(decidedApplication(ApplicationStatus.VERIFIED, null)));

        assertThatThrownBy(() -> service.decide(applicationId.toHexString(),
                new AdminVerificationDecisionRequest(Decision.VERIFIED, null)))
                .isInstanceOf(VerificationStateConflictException.class)
                .hasMessageContaining("already been decided");

        verify(mongoTemplate, never()).findAndModify(any(), any(), any(), eq(VerificationApplication.class));
    }

    @Test
    @DisplayName("when the conditional claim matches nothing - a concurrent decision won - the conflict is reported")
    void lostRaceIsConflict() {
        givenPendingApplication();
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(VerificationApplication.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> service.decide(applicationId.toHexString(),
                new AdminVerificationDecisionRequest(Decision.VERIFIED, null)))
                .isInstanceOf(VerificationStateConflictException.class)
                .hasMessageContaining("no longer pending");

        verify(userService, never()).applyVerificationDecision(any(), any());
    }

    @Test
    @DisplayName("when the account refuses the transition, the claim is reverted so the two never disagree")
    void accountRefusalRevertsClaim() {
        givenPendingApplication();
        User applicant = applicant(VerificationStatus.PENDING);
        when(userService.requireById(applicantId)).thenReturn(applicant);
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(VerificationApplication.class)))
                .thenReturn(decidedApplication(ApplicationStatus.VERIFIED, null));
        org.mockito.Mockito.doThrow(new VerificationStateConflictException("Account is not pending."))
                .when(userService).applyVerificationDecision(applicant, VerificationStatus.VERIFIED);

        assertThatThrownBy(() -> service.decide(applicationId.toHexString(),
                new AdminVerificationDecisionRequest(Decision.VERIFIED, null)))
                .isInstanceOf(VerificationStateConflictException.class);

        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(VerificationApplication.class));
    }

    @Test
    @DisplayName("an unknown application id is a 404 that reveals nothing")
    void unknownApplicationIsNotFound() {
        when(applicationRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.decide(new ObjectId().toHexString(),
                new AdminVerificationDecisionRequest(Decision.VERIFIED, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("a malformed application id is a 404, not a parse error")
    void malformedIdIsNotFound() {
        assertThatThrownBy(() -> service.openDocument("not-an-id"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------- helpers

    private void givenPendingApplication() {
        when(applicationRepository.findById(applicationId))
                .thenReturn(Optional.of(studentApplication(ApplicationStatus.PENDING)));
    }

    private User admin() {
        return user(AppRole.ADMIN, VerificationStatus.VERIFIED);
    }

    private static User user(AppRole role, VerificationStatus status) {
        User user = new User();
        user.setId(new ObjectId());
        user.setEmail("user@example.com");
        user.setName("Someone");
        user.setRole(role);
        user.setVerificationStatus(status);
        user.setEnabled(true);
        return user;
    }

    private User applicant(VerificationStatus status) {
        User user = new User();
        user.setId(applicantId);
        user.setEmail("ali@example.com");
        user.setName("Ali Raza");
        user.setRole(AppRole.STUDENT);
        user.setVerificationStatus(status);
        user.setEnabled(true);
        return user;
    }

    private VerificationApplication studentApplication(ApplicationStatus status) {
        VerificationApplication application = new VerificationApplication();
        application.setId(applicationId);
        application.setUserId(applicantId);
        application.setType(ApplicationType.STUDENT);
        application.setClaimedRole("STUDENT");
        application.setStatus(status);
        application.setProgram(Program.SE);
        application.setCurrentSemester(6);
        application.setAttemptNumber(1);
        application.setSubmittedAt(NOW.minusSeconds(3600));
        application.setDocumentId(new ObjectId());
        application.setDocumentOriginalFilename("card.png");
        return application;
    }

    private VerificationApplication decidedApplication(ApplicationStatus status, String note) {
        VerificationApplication application = studentApplication(status);
        application.setDecidedAt(NOW);
        application.setReviewerNote(note);
        return application;
    }
}
