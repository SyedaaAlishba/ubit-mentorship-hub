package com.ubit.mentorshiphub.rag.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalOnnxVerificationTest {

    @Test
    void testLocalEmbeddingModel() {
        System.out.println("Initializing local in-process all-MiniLM-L6-v2 ONNX model...");
        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        
        String sampleText = "What is the minimum attendance required to sit in a final examination?";
        Embedding embedding = embeddingModel.embed(sampleText).content();
        
        float[] vector = embedding.vector();
        System.out.println("Local ONNX Embedding computed successfully!");
        System.out.println("Vector dimension: " + vector.length);
        System.out.println("First 5 values: " + vector[0] + ", " + vector[1] + ", " + vector[2] + ", " + vector[3] + ", " + vector[4]);
        
        assertThat(vector.length).isEqualTo(384);
    }
}
