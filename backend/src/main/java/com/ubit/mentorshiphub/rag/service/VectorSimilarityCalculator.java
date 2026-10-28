package com.ubit.mentorshiphub.rag.service;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class VectorSimilarityCalculator {

    /**
     * Computes cosine similarity between two dense vectors:
     * cos(u, v) = (u . v) / (||u|| * ||v||)
     */
    public double cosineSimilarity(List<Double> v1, List<Double> v2) {
        if (v1 == null || v2 == null || v1.isEmpty() || v2.isEmpty()) {
            return 0.0;
        }
        int minLen = Math.min(v1.size(), v2.size());
        if (minLen == 0) return 0.0;

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < minLen; i++) {
            double a = v1.get(i);
            double b = v2.get(i);
            dotProduct += a * b;
            normA += a * a;
            normB += b * b;
        }

        if (normA <= 0.0 || normB <= 0.0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
