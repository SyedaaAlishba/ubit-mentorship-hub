package com.ubit.mentorshiphub.admin.controller;

import com.ubit.mentorshiphub.admin.dto.AdminVerificationApplicationResponse;
import com.ubit.mentorshiphub.admin.dto.AdminVerificationDecisionResponse;
import com.ubit.mentorshiphub.admin.service.AdminVerificationService;
import com.ubit.mentorshiphub.admin.service.AdminVerificationService.DocumentDownload;
import com.ubit.mentorshiphub.common.exception.GlobalExceptionHandler;
import com.ubit.mentorshiphub.common.exception.InsufficientRoleException;
import com.ubit.mentorshiphub.common.exception.InvalidRequestException;
import com.ubit.mentorshiphub.common.exception.ResourceNotFoundException;
import com.ubit.mentorshiphub.common.exception.VerificationStateConflictException;
import com.ubit.mentorshiphub.user.model.VerificationStatus;
import com.ubit.mentorshiphub.verification.model.ApplicationStatus;
import com.ubit.mentorshiphub.verification.model.ApplicationType;
import com.ubit.mentorshiphub.verification.model.Program;
import com.ubit.mentorshiphub.verification.model.StudentDocumentType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The administrator review endpoints over HTTP: the shape of each answer, the
 * validation that never reaches the service, and the mapping of service failures
 * onto status codes. Authorization itself is proved in the service test and, for
 * an anonymous caller, through the real filter chain in {@code SecurityConfigTest}.
 */
@ExtendWith(MockitoExtension.class)
class AdminVerificationControllerTest {

    private static final String APPLICATIONS = "/api/admin/verification/applications";

    @Mock
    private AdminVerificationService adminVerificationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminVerificationController(adminVerificationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ------------------------------------------------------------------ listing

    @Test
    @DisplayName("the queue is returned as a page of review rows")
    void listsPendingApplications() throws Exception {
        Page<AdminVerificationApplicationResponse> page =
                new PageImpl<>(List.of(reviewRow()), PageRequest.of(0, 20), 1);
        when(adminVerificationService.listApplications(0, 20, null)).thenReturn(page);

        mockMvc.perform(get(APPLICATIONS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].applicationId").value("a1"))
                .andExpect(jsonPath("$.content[0].applicantName").value("Ali Raza"))
                .andExpect(jsonPath("$.content[0].applicantEmail").value("ali@example.com"))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].documentUrl")
                        .value("/api/admin/verification/applications/a1/document"))
                .andExpect(jsonPath("$.content[0].documentId").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("an explicit status filter is passed through")
    void forwardsStatusFilter() throws Exception {
        when(adminVerificationService.listApplications(0, 20, "REJECTED"))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get(APPLICATIONS).param("status", "REJECTED"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an anonymous caller is refused by the filter chain, before the service is reached")
    void anonymousIsRefused() throws Exception {
        // Standalone MockMvc has no filter chain, so this asserts the mapping the
        // service raises when AccountAccess finds no session: a 403-shaped refusal.
        when(adminVerificationService.listApplications(anyInt(), anyInt(), isNull()))
                .thenThrow(new InsufficientRoleException("Authentication required."));

        mockMvc.perform(get(APPLICATIONS)).andExpect(status().isForbidden());
    }

    // ----------------------------------------------------------------- document

    @Test
    @DisplayName("a student card is streamed inline with its own content type and filename")
    void streamsImageInline() throws Exception {
        when(adminVerificationService.openDocument("a1")).thenReturn(new DocumentDownload(
                new ByteArrayInputStream(new byte[]{1, 2, 3}),
                "image/png", "card.png", "inline; filename=\"card.png\"", 3));

        MvcResult started = mockMvc.perform(get(APPLICATIONS + "/a1/document"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"card.png\""))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    @DisplayName("a PDF is streamed as an attachment, never inline")
    void streamsPdfAsAttachment() throws Exception {
        when(adminVerificationService.openDocument("a1")).thenReturn(new DocumentDownload(
                new ByteArrayInputStream(new byte[]{9}),
                "application/pdf", "transcript.pdf", "attachment; filename=\"transcript.pdf\"", 1));

        MvcResult started = mockMvc.perform(get(APPLICATIONS + "/a1/document"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"transcript.pdf\""));
    }

    @Test
    @DisplayName("an application with no document is a 404")
    void missingDocumentIsNotFound() throws Exception {
        when(adminVerificationService.openDocument("a1"))
                .thenThrow(new ResourceNotFoundException("This application has no uploaded document."));

        mockMvc.perform(get(APPLICATIONS + "/a1/document")).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- decisions

    @Test
    @DisplayName("an approval returns both final statuses")
    void approvalReturnsOutcome() throws Exception {
        when(adminVerificationService.decide(eq("a1"), any()))
                .thenReturn(new AdminVerificationDecisionResponse(
                        "a1", "u1", ApplicationStatus.VERIFIED, VerificationStatus.VERIFIED,
                        Instant.parse("2026-10-16T12:00:00Z"), null));

        mockMvc.perform(post(APPLICATIONS + "/a1/decide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"VERIFIED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.userVerificationStatus").value("VERIFIED"));
    }

    @Test
    @DisplayName("a rejection returns the note it stored")
    void rejectionReturnsNote() throws Exception {
        when(adminVerificationService.decide(eq("a1"), any()))
                .thenReturn(new AdminVerificationDecisionResponse(
                        "a1", "u1", ApplicationStatus.REJECTED, VerificationStatus.REJECTED,
                        Instant.parse("2026-10-16T12:00:00Z"), "Enrolment year does not match."));

        mockMvc.perform(post(APPLICATIONS + "/a1/decide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\",\"reviewerNote\":\"Enrolment year does not match.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationStatus").value("REJECTED"))
                .andExpect(jsonPath("$.reviewerNote").value("Enrolment year does not match."));
    }

    @Test
    @DisplayName("a decision without a chosen outcome is refused by validation")
    void decisionIsRequired() throws Exception {
        mockMvc.perform(post(APPLICATIONS + "/a1/decide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewerNote\":\"maybe\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an outcome that is not one of the two decisions is refused")
    void unknownDecisionIsRefused() throws Exception {
        mockMvc.perform(post(APPLICATIONS + "/a1/decide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"PENDING\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an over-long review note is refused by validation")
    void overlongNoteIsRefused() throws Exception {
        String longNote = "x".repeat(1001);
        mockMvc.perform(post(APPLICATIONS + "/a1/decide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\",\"reviewerNote\":\"" + longNote + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a rejection without a reason is a 400 from the service, not a 500")
    void blankRejectionReasonIsBadRequest() throws Exception {
        when(adminVerificationService.decide(eq("a1"), any()))
                .thenThrow(new InvalidRequestException("Give a reason when rejecting an application."));

        mockMvc.perform(post(APPLICATIONS + "/a1/decide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a repeated decision is a 409")
    void repeatedDecisionIsConflict() throws Exception {
        when(adminVerificationService.decide(eq("a1"), any()))
                .thenThrow(new VerificationStateConflictException("This application has already been decided."));

        mockMvc.perform(post(APPLICATIONS + "/a1/decide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"VERIFIED\"}"))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------- helpers

    private static AdminVerificationApplicationResponse reviewRow() {
        return new AdminVerificationApplicationResponse(
                "a1", "u1", "Ali Raza", "ali@example.com",
                ApplicationType.STUDENT, "STUDENT", ApplicationStatus.PENDING, 1,
                Instant.parse("2026-10-15T12:00:00Z"),
                Program.SE, 6, StudentDocumentType.STUDENT_CARD, "card.png",
                null, null, null, null,
                "/api/admin/verification/applications/a1/document");
    }
}
