package com.ubit.mentorshiphub.rag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ubit.mentorshiphub.common.security.AccountAccess;
import com.ubit.mentorshiphub.rag.dto.RagIngestionResponse;
import com.ubit.mentorshiphub.rag.dto.RagQueryRequest;
import com.ubit.mentorshiphub.rag.dto.RagQueryResponse;
import com.ubit.mentorshiphub.rag.service.RagService;
import com.ubit.mentorshiphub.user.model.AppRole;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.user.model.VerificationStatus;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RagController.class)
@AutoConfigureMockMvc
class RagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RagService ragService;

    @MockitoBean
    private AccountAccess accountAccess;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(new ObjectId());
        currentUser.setEmail("student@ubit.edu.pk");
        currentUser.setName("Test Student");
        currentUser.setRole(AppRole.STUDENT);
        currentUser.setVerificationStatus(VerificationStatus.VERIFIED);
        currentUser.setCreatedAt(Instant.now());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/rag/ask with valid question returns 200 and RAG response")
    void testAskValid() throws Exception {
        when(accountAccess.requireCurrentUser()).thenReturn(currentUser);
        RagQueryResponse mockResponse = new RagQueryResponse(
                "What is the minimum attendance required in exams?",
                "At least 75% attendance is required.",
                true,
                0.85,
                List.of(new RagQueryResponse.RagSourceInfo("chunk-19", "What is the minimum attendance...", "At least 75%...", "UBIT FAQ & Guidelines", 0.85, "knowledge_base_qa.csv"))
        );
        when(ragService.query(any())).thenReturn(mockResponse);

        RagQueryRequest request = new RagQueryRequest("What is the minimum attendance required in exams?");

        mockMvc.perform(post("/api/rag/ask")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").value("What is the minimum attendance required in exams?"))
                .andExpect(jsonPath("$.answer").value("At least 75% attendance is required."))
                .andExpect(jsonPath("$.grounded").value(true))
                .andExpect(jsonPath("$.sources").isArray());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/rag/ask with blank question returns 400 validation failure")
    void testAskBlankQuestion() throws Exception {
        when(accountAccess.requireCurrentUser()).thenReturn(currentUser);
        RagQueryRequest request = new RagQueryRequest("   ");

        mockMvc.perform(post("/api/rag/ask")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/rag/ingest triggers document ingestion and returns 200")
    void testIngest() throws Exception {
        when(accountAccess.requireCurrentUser()).thenReturn(currentUser);
        when(ragService.ingestDefaultKnowledgeBase()).thenReturn(
                new RagIngestionResponse(19, "knowledge_base_qa.csv", "Successfully ingested 19 chunks")
        );

        mockMvc.perform(post("/api/rag/ingest")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIngested").value(19))
                .andExpect(jsonPath("$.sourceDocument").value("knowledge_base_qa.csv"));
    }
}
