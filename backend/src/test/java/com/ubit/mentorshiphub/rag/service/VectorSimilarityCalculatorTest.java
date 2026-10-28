package com.ubit.mentorshiphub.rag.service;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VectorSimilarityCalculatorTest {

    private VectorSimilarityCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new VectorSimilarityCalculator();
    }

    @Test
    @DisplayName("Identical vectors have cosine similarity of 1.0")
    void identicalVectors() {
        List<Double> v1 = List.of(0.5, 0.5, 0.5, 0.5);
        List<Double> v2 = List.of(0.5, 0.5, 0.5, 0.5);

        double sim = calculator.cosineSimilarity(v1, v2);
        assertThat(sim).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    @DisplayName("Orthogonal vectors have cosine similarity of 0.0")
    void orthogonalVectors() {
        List<Double> v1 = List.of(1.0, 0.0);
        List<Double> v2 = List.of(0.0, 1.0);

        double sim = calculator.cosineSimilarity(v1, v2);
        assertThat(sim).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    @DisplayName("Empty or null vectors return 0.0")
    void emptyOrNullVectors() {
        assertThat(calculator.cosineSimilarity(null, List.of(1.0))).isEqualTo(0.0);
        assertThat(calculator.cosineSimilarity(List.of(1.0), null)).isEqualTo(0.0);
        assertThat(calculator.cosineSimilarity(List.of(), List.of(1.0))).isEqualTo(0.0);
    }
}
