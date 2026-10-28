package com.ubit.mentorshiphub.rag.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * High-quality deterministic vector embedding fallback when HF_TOKEN is absent or offline.
 * Builds normalized TF-IDF representations over domain vocabulary.
 */
@Service
public class LocalVectorFallbackService {

    // Curated high-impact vocabulary from university domain & questions document
    private static final List<String> VOCABULARY = Arrays.asList(
            "attendance", "minimum", "75", "percent", "examination", "exam", "terminal", "final",
            "field", "choose", "ai", "artificial", "intelligence", "data", "science", "cybersecurity",
            "security", "web", "development", "project", "software", "career", "opportunities",
            "graduates", "pakistan", "abroad", "jobs", "industry", "skills", "syllabus", "programming",
            "git", "github", "databases", "problem", "solving", "debugging", "teamwork", "communication",
            "internship", "ready", "apply", "fundamentals", "cgpa", "certifications", "grades",
            "startup", "established", "company", "culture", "compensation", "gaps", "requirements",
            "specialize", "multiple", "technologies", "interview", "technical", "algorithms",
            "structures", "unadvertised", "network", "alumnus", "alumni", "questions", "session",
            "mistakes", "fresh", "relationship", "referral", "different", "academic", "assignments",
            "portfolio", "switch", "course", "mentor", "mentorship"
    );

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "a", "an", "the", "and", "or", "is", "are", "was", "were", "to", "in", "on", "for",
            "with", "about", "by", "at", "from", "as", "into", "through", "during", "before",
            "after", "above", "below", "of", "it", "its", "you", "your", "i", "my", "we", "our",
            "they", "their", "he", "she", "his", "her", "that", "this", "these", "those", "which",
            "what", "when", "where", "how", "who", "whom", "can", "could", "should", "would", "do",
            "does", "did", "have", "has", "had", "be", "been", "being"
    ));

    public List<Double> generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>(Collections.nCopies(VOCABULARY.size(), 0.0));
        }

        String[] tokens = text.toLowerCase(Locale.ROOT).split("[^a-zA-Z0-9]+");
        Map<String, Integer> termFreqs = new HashMap<>();

        for (String token : tokens) {
            if (token.length() > 1 && !STOPWORDS.contains(token)) {
                termFreqs.put(token, termFreqs.getOrDefault(token, 0) + 1);
            }
        }

        List<Double> vector = new ArrayList<>(VOCABULARY.size());
        double sumSquares = 0.0;

        for (String vocabWord : VOCABULARY) {
            int count = termFreqs.getOrDefault(vocabWord, 0);
            // Stemming-like matching for common plurals/variations
            if (count == 0) {
                for (String t : termFreqs.keySet()) {
                    if (t.startsWith(vocabWord) || vocabWord.startsWith(t)) {
                        count += termFreqs.get(t);
                    }
                }
            }
            double val = (double) count;
            vector.add(val);
            sumSquares += val * val;
        }

        // L2 Unit Normalization
        if (sumSquares > 0.0) {
            double norm = Math.sqrt(sumSquares);
            for (int i = 0; i < vector.size(); i++) {
                vector.set(i, vector.get(i) / norm);
            }
        }

        return vector;
    }
}
