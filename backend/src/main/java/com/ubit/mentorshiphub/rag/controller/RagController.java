package com.ubit.mentorshiphub.rag.controller;

import com.ubit.mentorshiphub.common.security.AccountAccess;
import com.ubit.mentorshiphub.common.security.AuthorizationGates;
import com.ubit.mentorshiphub.rag.dto.RagIngestionResponse;
import com.ubit.mentorshiphub.rag.dto.RagQueryRequest;
import com.ubit.mentorshiphub.rag.dto.RagQueryResponse;
import com.ubit.mentorshiphub.rag.service.RagService;
import com.ubit.mentorshiphub.user.model.User;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;
    private final AccountAccess accountAccess;

    public RagController(RagService ragService, AccountAccess accountAccess) {
        this.ragService = ragService;
        this.accountAccess = accountAccess;
    }

    /**
     * Ingests the Q/A knowledge base document.
     * Restricted to authenticated users who completed onboarding (or admins).
     */
    @PostMapping("/ingest")
    public ResponseEntity<RagIngestionResponse> ingest() {
        User currentUser = accountAccess.requireCurrentUser();
        AuthorizationGates.requireOnboardingComplete(currentUser);
        RagIngestionResponse response = ragService.ingestDefaultKnowledgeBase();
        return ResponseEntity.ok(response);
    }

    /**
     * Query the RAG assistant with grounded semantic search.
     * Available to all authenticated users who have completed onboarding.
     */
    @PostMapping("/ask")
    public ResponseEntity<RagQueryResponse> ask(@Valid @RequestBody RagQueryRequest request) {
        User currentUser = accountAccess.requireCurrentUser();
        AuthorizationGates.requireOnboardingComplete(currentUser);
        RagQueryResponse response = ragService.query(request);
        return ResponseEntity.ok(response);
    }
}
