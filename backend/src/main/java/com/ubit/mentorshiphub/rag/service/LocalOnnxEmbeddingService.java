package com.ubit.mentorshiphub.rag.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Truly local, in-process embedding service using ONNX Runtime.
 * Executes sentence-transformers/all-MiniLM-L6-v2 directly on the CPU.
 * Produces 384-dimensional dense vectors without making any network or API calls.
 */
@Service
public class LocalOnnxEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(LocalOnnxEmbeddingService.class);
    private static final String MODEL_NAME = "sentence-transformers/all-MiniLM-L6-v2 (Local ONNX)";
    private static final int DIMENSION = 384;

    private final EmbeddingModel embeddingModel;

    public LocalOnnxEmbeddingService() {
        log.info("Loading in-process ONNX model: {}...", MODEL_NAME);
        try {
            this.embeddingModel = new AllMiniLmL6V2EmbeddingModel();
            log.info("Successfully loaded local in-process embedding model [{}], dimension: {}", MODEL_NAME, DIMENSION);
        } catch (Exception e) {
            log.error("Failed to initialize local ONNX embedding model: {}", e.getMessage(), e);
            throw new IllegalStateException("Could not load local ONNX embedding model", e);
        }
    }

    public String getModelName() {
        return MODEL_NAME;
    }

    public int getDimension() {
        return DIMENSION;
    }

    /**
     * Computes the 384-dimensional embedding vector locally.
     *
     * @param text input text
     * @return normalized 384-dimensional vector as List of Doubles
     */
    public List<Double> generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text to embed cannot be null or blank");
        }
        try {
            Embedding embedding = embeddingModel.embed(text).content();
            float[] vector = embedding.vector();
            List<Double> result = new ArrayList<>(vector.length);
            for (float val : vector) {
                result.add((double) val);
            }
            return result;
        } catch (Exception e) {
            log.error("Local ONNX embedding generation failed for text: {}", e.getMessage(), e);
            throw new RuntimeException("Local embedding failed: " + e.getMessage(), e);
        }
    }
}
