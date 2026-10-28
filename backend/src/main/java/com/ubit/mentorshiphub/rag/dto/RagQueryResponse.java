package com.ubit.mentorshiphub.rag.dto;

import java.util.List;

public record RagQueryResponse(
        String question,
        String answer,
        boolean grounded,
        double similarityScore,
        List<RagSourceInfo> sources
) {
    public record RagSourceInfo(
            String chunkId,
            String question,
            String answer,
            String category,
            double similarityScore,
            String sourceDocument
    ) {}
}
