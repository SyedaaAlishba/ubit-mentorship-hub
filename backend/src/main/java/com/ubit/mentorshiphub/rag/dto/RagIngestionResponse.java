package com.ubit.mentorshiphub.rag.dto;

public record RagIngestionResponse(
        int totalIngested,
        String sourceDocument,
        String message
) {}
