package com.ubit.mentorshiphub.verification.controller;

import com.ubit.mentorshiphub.common.exception.GlobalExceptionHandler;
import com.ubit.mentorshiphub.common.exception.InvalidDocumentException;
import com.ubit.mentorshiphub.common.exception.VerificationStateConflictException;
import com.ubit.mentorshiphub.common.security.AccountAccess;
import com.ubit.mentorshiphub.user.model.AppRole;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.user.model.VerificationStatus;
import com.ubit.mentorshiphub.verification.dto.AlumniVerificationRequest;
import com.ubit.mentorshiphub.verification.dto.StudentVerificationRequest;
import com.ubit.mentorshiphub.verification.dto.VerificationApplicationResponse;
import com.ubit.mentorshiphub.verification.model.ApplicationStatus;
import com.ubit.mentorshiphub.verification.model.ApplicationType;
import com.ubit.mentorshiphub.verification.model.Program;
import com.ubit.mentorshiphub.verification.model.StudentDocumentType;
import com.ubit.mentorshiphub.verification.service.VerificationApplicationService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The verification endpoints, with the ownership rule under test: the applicant
 * is the session's account, and a request cannot file on someone else's behalf.
 */
@ExtendWith(MockitoExtension.class)
class VerificationControllerTest {

    @Mock
    private AccountAccess accountAccess;

    @Mock
    private VerificationApplicationService verificationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // The real advice, so a rejected body is a mapped 4xx rather than an
        // exception bubbling out of standalone MockMvc.
        mockMvc = MockMvcBuilders.standaloneSetup(new VerificationController(accountAccess, verificationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @Test
    @DisplayName("a student submission is accepted and returns the recorded application")
    void studentSubmissionIsAccepted() throws Exception {
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.STUDENT, VerificationStatus.UNVERIFIED));
        when(verificationService.submitStudent(any(), any(), any()))
                .thenReturn(acceptedStudentApplication());

        mockMvc.perform(multipart("/api/verification/student")
                        .file(new MockMultipartFile("document", "card.png", "image/png", new byte[]{1, 2, 3}))
                        .param("program", "SE")
                        .param("currentSemester", "6")
                        .param("documentType", "STUDENT_CARD"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.program").value("SE"))
                .andExpect(jsonPath("$.documentType").value("STUDENT_CARD"));
    }

    @Test
    @DisplayName("the upload is required, so a missing document is refused before the service runs")
    void missingDocumentIsRefused() throws Exception {
        // No account stub and no service stub: the absent part is caught by
        // argument resolution, so neither is reached.
        mockMvc.perform(multipart("/api/verification/student")
                        .param("program", "SE")
                        .param("currentSemester", "6")
                        .param("documentType", "STUDENT_CARD"))
                .andExpect(status().is4xxClientError());

        verify(verificationService, never()).submitStudent(any(), any(), any());
    }

    @Test
    @DisplayName("a document the storage layer rejects is reported as bad input")
    void unusableDocumentIsRefused() throws Exception {
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.STUDENT, VerificationStatus.UNVERIFIED));
        when(verificationService.submitStudent(any(), any(), any()))
                .thenThrow(new InvalidDocumentException("Attach a student card or enrolment slip to continue."));

        mockMvc.perform(multipart("/api/verification/student")
                        .file(new MockMultipartFile("document", "card.png", "image/png", new byte[]{1}))
                        .param("program", "SE")
                        .param("currentSemester", "6")
                        .param("documentType", "STUDENT_CARD"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("an out-of-range semester is refused by validation")
    void refusesSemesterOutOfRange() throws Exception {
        mockMvc.perform(multipart("/api/verification/student")
                        .file(new MockMultipartFile("document", "card.png", "image/png", new byte[]{1}))
                        .param("program", "SE")
                        .param("currentSemester", "99")
                        .param("documentType", "STUDENT_CARD"))
                .andExpect(status().isBadRequest());

        // The claim is discarded before any service call, so no application and
        // above all no upload is recorded for an invalid request.
        verify(verificationService, never()).submitStudent(any(), any(), any());
    }

    @Test
    @DisplayName("an unknown programme is reported as bad input, not a server error")
    void refusesUnknownProgramme() throws Exception {
        mockMvc.perform(multipart("/api/verification/student")
                        .file(new MockMultipartFile("document", "card.png", "image/png", new byte[]{1}))
                        .param("program", "UNDERWATER_BASKETWEAVING")
                        .param("currentSemester", "6")
                        .param("documentType", "STUDENT_CARD"))
                .andExpect(status().isBadRequest());

        verify(verificationService, never()).submitStudent(any(), any(), any());
    }

    @Test
    @DisplayName("an alumni submission is accepted")
    void alumniSubmissionIsAccepted() throws Exception {
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.ALUMNI, VerificationStatus.UNVERIFIED));
        when(verificationService.submitAlumni(any(), any())).thenReturn(acceptedAlumniApplication());

        mockMvc.perform(post("/api/verification/alumni")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passoutYear\":2024,\"linkedInUrl\":\"https://www.linkedin.com/in/aliraza\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.type").value("ALUMNI"))
                .andExpect(jsonPath("$.passoutYear").value(2024));
    }

    @Test
    @DisplayName("a non-LinkedIn URL is refused by validation")
    void refusesNonLinkedInUrl() throws Exception {
        mockMvc.perform(post("/api/verification/alumni")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passoutYear\":2024,\"linkedInUrl\":\"https://evil.example.com/profile\"}"))
                .andExpect(status().is4xxClientError());

        verify(verificationService, never()).submitAlumni(any(), any());
    }

    @Test
    @DisplayName("a client-supplied user id cannot redirect an application")
    void ignoresClientSuppliedUserId() throws Exception {
        User sessionUser = user(AppRole.ALUMNI, VerificationStatus.UNVERIFIED);
        when(accountAccess.requireCurrentUser()).thenReturn(sessionUser);
        when(verificationService.submitAlumni(any(), any())).thenReturn(acceptedAlumniApplication());

        mockMvc.perform(post("/api/verification/alumni")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passoutYear\":2024,"
                                + "\"linkedInUrl\":\"https://www.linkedin.com/in/aliraza\","
                                + "\"userId\":\"64b000000000000000000099\"}"))
                .andExpect(status().isAccepted());

        verify(verificationService).submitAlumni(eq(sessionUser), any());
    }

    @Test
    @DisplayName("a duplicate submission is a conflict")
    void duplicateSubmissionIsConflict() throws Exception {
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.STUDENT, VerificationStatus.PENDING));
        when(verificationService.submitStudent(any(), any(), any()))
                .thenThrow(new VerificationStateConflictException("An application is already awaiting review."));

        mockMvc.perform(multipart("/api/verification/student")
                        .file(new MockMultipartFile("document", "card.png", "image/png", new byte[]{1}))
                        .param("program", "SE")
                        .param("currentSemester", "6")
                        .param("documentType", "STUDENT_CARD"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("the current application is returned for the status screen")
    void currentApplicationIsReturned() throws Exception {
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.STUDENT, VerificationStatus.PENDING));
        when(verificationService.currentApplication(any())).thenReturn(Optional.of(acceptedStudentApplication()));

        mockMvc.perform(get("/api/verification/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("an account that has never applied gets a 404, which the client reads as 'not applied'")
    void noApplicationIsNotFound() throws Exception {
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.STUDENT, VerificationStatus.UNVERIFIED));
        when(verificationService.currentApplication(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/verification/me")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the response never exposes the GridFS id of an uploaded document")
    void doesNotLeakDocumentId() throws Exception {
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.STUDENT, VerificationStatus.PENDING));
        when(verificationService.currentApplication(any())).thenReturn(Optional.of(acceptedStudentApplication()));

        mockMvc.perform(get("/api/verification/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").doesNotExist());
    }

    @Test
    @DisplayName("the bound request is the validated one, so a semester outside 1-8 never reaches the service")
    void bindsRequestFields() throws Exception {
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.STUDENT, VerificationStatus.UNVERIFIED));
        when(verificationService.submitStudent(any(), any(), any()))
                .thenReturn(acceptedStudentApplication());

        mockMvc.perform(multipart("/api/verification/student")
                        .file(new MockMultipartFile("document", "card.png", "image/png", new byte[]{1}))
                        .param("program", "AI")
                        .param("currentSemester", "3")
                        .param("documentType", "ENROLLMENT_SLIP"))
                .andExpect(status().isAccepted());

        verify(verificationService).submitStudent(any(),
                eq(new StudentVerificationRequest(Program.AI, 3, StudentDocumentType.ENROLLMENT_SLIP)), any());
    }

    @Test
    @DisplayName("a file exceeding the multipart limit produces 400 Bad Request with a clear message")
    void maxUploadSizeExceededReturnsBadRequest() throws Exception {
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.STUDENT, VerificationStatus.UNVERIFIED));
        when(verificationService.submitStudent(any(), any(), any()))
                .thenThrow(new org.springframework.web.multipart.MaxUploadSizeExceededException(5242880));

        mockMvc.perform(multipart("/api/verification/student")
                        .file(new MockMultipartFile("document", "oversized.pdf", "application/pdf", new byte[]{1}))
                        .param("program", "CS")
                        .param("currentSemester", "2")
                        .param("documentType", "STUDENT_CARD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The uploaded document exceeds the 5 MB limit. Please upload a smaller file."));
    }

    private static VerificationApplicationResponse acceptedStudentApplication() {
        return new VerificationApplicationResponse(
                new ObjectId().toHexString(), ApplicationType.STUDENT, ApplicationStatus.PENDING, "STUDENT",
                Program.SE, 6, StudentDocumentType.STUDENT_CARD, null, null, "card.png", 1, null, null);
    }

    private static VerificationApplicationResponse acceptedAlumniApplication() {
        return new VerificationApplicationResponse(
                new ObjectId().toHexString(), ApplicationType.ALUMNI, ApplicationStatus.PENDING, "ALUMNI",
                null, null, null, 2024, "https://www.linkedin.com/in/aliraza", null, 1, null, null);
    }

    private static User user(AppRole role, VerificationStatus status) {
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
