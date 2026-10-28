package com.ubit.mentorshiphub.verification.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.ubit.mentorshiphub.common.exception.VerificationStateConflictException;
import com.ubit.mentorshiphub.user.model.AppRole;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.user.model.VerificationStatus;
import com.ubit.mentorshiphub.user.service.UserService;
import com.ubit.mentorshiphub.verification.dto.AlumniVerificationRequest;
import com.ubit.mentorshiphub.verification.dto.StudentVerificationRequest;
import com.ubit.mentorshiphub.verification.dto.VerificationApplicationResponse;
import com.ubit.mentorshiphub.verification.model.ApplicationStatus;
import com.ubit.mentorshiphub.verification.model.ApplicationType;
import com.ubit.mentorshiphub.verification.model.Program;
import com.ubit.mentorshiphub.verification.model.StudentDocumentType;
import com.ubit.mentorshiphub.verification.model.VerificationApplication;
import com.ubit.mentorshiphub.verification.repository.VerificationApplicationRepository;
import com.ubit.mentorshiphub.verification.storage.VerificationDocumentService;
import com.ubit.mentorshiphub.verification.storage.VerificationDocumentService.StoredDocument;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The submission rules: who may file what, when, and what happens to the upload.
 */
@ExtendWith(MockitoExtension.class)
class VerificationApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final ObjectId DOCUMENT_ID = new ObjectId();

    @Mock
    private VerificationApplicationRepository applicationRepository;

    @Mock
    private VerificationDocumentService documentService;

    @Mock
    private UserService userService;

    @Mock
    private MultipartFile document;

    private VerificationApplicationService verificationService;

    @BeforeEach
    void setUp() {
        verificationService = new VerificationApplicationService(
                applicationRepository, documentService, userService, Clock.fixed(NOW, ZoneOffset.UTC));
        // save() echoes the entity, as Spring Data does. Without this the mock
        // returns null and the response mapping fails for reasons unrelated to
        // the rule under test.
        lenient().when(applicationRepository.save(any(VerificationApplication.class)))
                .thenAnswer(invocation -> {
                    VerificationApplication saved = invocation.getArgument(0);
                    if (saved.getId() == null) {
                        saved.setId(new ObjectId());
                    }
                    return saved;
                });
    }

    @Test
    @DisplayName("a student submission is recorded and moves the account to PENDING")
    void studentSubmissionStartsVerification() {
        givenStoredDocument();
        User student = givenUser(AppRole.STUDENT, VerificationStatus.UNVERIFIED);

        VerificationApplicationResponse response =
                verificationService.submitStudent(student, studentRequest(), document);

        assertThat(response.status()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(response.type()).isEqualTo(ApplicationType.STUDENT);
        assertThat(response.program()).isEqualTo(Program.SE);
        assertThat(response.currentSemester()).isEqualTo(6);
        assertThat(response.documentType()).isEqualTo(StudentDocumentType.STUDENT_CARD);
        assertThat(response.attemptNumber()).isEqualTo(1);
        assertThat(response.claimedRole()).isEqualTo("STUDENT");
        verify(userService).startVerification(student);
    }

    @Test
    @DisplayName("a student cannot file as an alumnus to skip the document")
    void studentCannotFileAsAlumnus() {
        User student = givenUser(AppRole.STUDENT, VerificationStatus.UNVERIFIED);

        assertThatThrownBy(() -> verificationService.submitAlumni(student, alumniRequest()))
                .isInstanceOf(VerificationStateConflictException.class)
                .hasMessageContaining("registered as STUDENT");

        verify(documentService, never()).store(any(), any());
        verify(userService, never()).startVerification(any());
    }

    @Test
    @DisplayName("an alumnus cannot file a student claim")
    void alumnusCannotFileAsStudent() {
        User alumnus = givenUser(AppRole.ALUMNI, VerificationStatus.UNVERIFIED);

        assertThatThrownBy(() -> verificationService.submitStudent(alumnus, studentRequest(), document))
                .isInstanceOf(VerificationStateConflictException.class);

        verify(documentService, never()).store(any(), any());
    }

    @Test
    @DisplayName("an un-onboarded account cannot submit anything")
    void unOnboardedAccountCannotSubmit() {
        User noRole = givenUser(null, VerificationStatus.UNVERIFIED);

        assertThatThrownBy(() -> verificationService.submitStudent(noRole, studentRequest(), document))
                .isInstanceOf(VerificationStateConflictException.class);
    }

    @Test
    @DisplayName("a pending application cannot be replaced by a second submission")
    void pendingCannotBeReplaced() {
        User student = givenUser(AppRole.STUDENT, VerificationStatus.PENDING);

        assertThatThrownBy(() -> verificationService.submitStudent(student, studentRequest(), document))
                .isInstanceOf(VerificationStateConflictException.class)
                .hasMessageContaining("awaiting review");

        verify(documentService, never()).store(any(), any());
    }

    @Test
    @DisplayName("a verified account has nothing left to apply for")
    void verifiedCannotSubmit() {
        User student = givenUser(AppRole.STUDENT, VerificationStatus.VERIFIED);

        assertThatThrownBy(() -> verificationService.submitStudent(student, studentRequest(), document))
                .isInstanceOf(VerificationStateConflictException.class)
                .hasMessageContaining("already verified");
    }

    @Test
    @DisplayName("a rejected student may resubmit, and the attempt number increases")
    void resubmissionIncrementsAttemptNumber() {
        givenStoredDocument();
        User student = givenUser(AppRole.STUDENT, VerificationStatus.REJECTED);
        when(applicationRepository.findFirstByUserIdOrderBySubmittedAtDesc(student.getId()))
                .thenReturn(java.util.Optional.of(previousAttempt(1)));

        VerificationApplicationResponse response =
                verificationService.submitStudent(student, studentRequest(), document);

        assertThat(response.attemptNumber()).isEqualTo(2);
        assertThat(response.status()).isEqualTo(ApplicationStatus.PENDING);
        verify(userService).startVerification(student);
    }

    @Test
    @DisplayName("an orphan upload is removed when the application cannot be written")
    void cleansUpUploadWhenApplicationWriteFails() {
        givenStoredDocument();
        User student = givenUser(AppRole.STUDENT, VerificationStatus.UNVERIFIED);
        when(applicationRepository.save(any(VerificationApplication.class)))
                .thenThrow(new VerificationStateConflictException("write failed"));

        assertThatThrownBy(() -> verificationService.submitStudent(student, studentRequest(), document))
                .isInstanceOf(VerificationStateConflictException.class);

        verify(documentService).deleteQuietly(DOCUMENT_ID);
        verify(userService, never()).startVerification(any());
    }

    @Test
    @DisplayName("an alumnus submission stores no document")
    void alumniSubmissionStoresNoDocument() {
        User alumnus = givenUser(AppRole.ALUMNI, VerificationStatus.UNVERIFIED);

        VerificationApplicationResponse response = verificationService.submitAlumni(alumnus, alumniRequest());

        assertThat(response.type()).isEqualTo(ApplicationType.ALUMNI);
        assertThat(response.passoutYear()).isEqualTo(2024);
        assertThat(response.linkedInUrl()).isEqualTo("https://www.linkedin.com/in/aliraza");
        assertThat(response.documentFilename()).isNull();
        verify(documentService, never()).store(any(), any());
    }

    @Test
    @DisplayName("a passout year in the future is refused")
    void refusesImpossiblePassoutYear() {
        User alumnus = givenUser(AppRole.ALUMNI, VerificationStatus.UNVERIFIED);
        AlumniVerificationRequest future = new AlumniVerificationRequest(
                java.time.Year.now().getValue() + 5, "https://www.linkedin.com/in/aliraza");

        assertThatThrownBy(() -> verificationService.submitAlumni(alumnus, future))
                .isInstanceOf(VerificationStateConflictException.class)
                .hasMessageContaining("has not happened yet");
    }

    @Test
    @DisplayName("the applicant cannot choose the account it applies to")
    void applicantComesFromTheSessionNotTheRequest() {
        givenStoredDocument();
        User student = givenUser(AppRole.STUDENT, VerificationStatus.UNVERIFIED);
        student.setId(new ObjectId("64b000000000000000000001"));

        verificationService.submitStudent(student, studentRequest(), document);

        ArgumentCaptor<ObjectId> owner = ArgumentCaptor.forClass(ObjectId.class);
        verify(documentService).store(owner.capture(), any());
        assertThat(owner.getValue())
                .as("the upload must be owned by the session's account")
                .isEqualTo(student.getId());
    }

    private void givenStoredDocument() {
        when(documentService.store(any(), any()))
                .thenReturn(new StoredDocument(DOCUMENT_ID, "card.png", "image/png", 120_000L));
    }

    private static StudentVerificationRequest studentRequest() {
        return new StudentVerificationRequest(Program.SE, 6, StudentDocumentType.STUDENT_CARD);
    }

    private static AlumniVerificationRequest alumniRequest() {
        return new AlumniVerificationRequest(2024, "https://www.linkedin.com/in/aliraza");
    }

    private static VerificationApplication previousAttempt(int attemptNumber) {
        VerificationApplication application = new VerificationApplication();
        application.setId(new ObjectId());
        application.setAttemptNumber(attemptNumber);
        application.setSubmittedAt(NOW.minusSeconds(86_400));
        application.setStatus(ApplicationStatus.REJECTED);
        return application;
    }

    private static User givenUser(AppRole role, VerificationStatus status) {
        User user = new User();
        user.setId(new ObjectId());
        user.setEmail("ali@example.com");
        user.setName("Ali Raza");
        user.setRole(role);
        user.setVerificationStatus(status);
        user.setEnabled(true);
        return user;
    }
}
