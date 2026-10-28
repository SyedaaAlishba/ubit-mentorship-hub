package com.ubit.mentorshiphub.rag.service;

import com.ubit.mentorshiphub.rag.dto.RagIngestionResponse;
import com.ubit.mentorshiphub.rag.dto.RagQueryRequest;
import com.ubit.mentorshiphub.rag.dto.RagQueryResponse;
import com.ubit.mentorshiphub.rag.model.RagKnowledgeChunk;
import com.ubit.mentorshiphub.rag.repository.RagKnowledgeChunkRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RagRealPipelineIntegrationTest {

    @Autowired
    private RagService ragService;

    @Autowired
    private RagKnowledgeChunkRepository chunkRepository;

    @Autowired
    private LocalOnnxEmbeddingService localOnnxEmbeddingService;

    @Test
    @DisplayName("Verify full RAG pipeline: Ingestion, Retrieval, and Accuracy using Local ONNX Model")
    void testFullPipeline() {
        System.out.println("=== Local ONNX Model Check ===");
        System.out.println("Model Name: " + localOnnxEmbeddingService.getModelName());
        System.out.println("Dimension:  " + localOnnxEmbeddingService.getDimension());

        // 1. Ingestion
        RagIngestionResponse ingestionResponse = ragService.ingestDefaultKnowledgeBase();
        System.out.println("\n=== Ingestion Result ===");
        System.out.println("Total Ingested: " + ingestionResponse.totalIngested());
        System.out.println("Message: " + ingestionResponse.message());

        assertThat(ingestionResponse.totalIngested()).isEqualTo(19);

        List<RagKnowledgeChunk> chunks = chunkRepository.findAll();
        assertThat(chunks).hasSize(19);

        int vectorDim = chunks.get(0).getEmbedding().size();
        System.out.println("Stored Vector Dimension in MongoDB: " + vectorDim);
        assertThat(vectorDim).isEqualTo(384);

        // 2. Exact Question Test
        System.out.println("\n=== Test 1: Exact Question ===");
        String exactQ = "What is the minimum attendance required to sit in a final examination?";
        RagQueryResponse resp1 = ragService.query(new RagQueryRequest(exactQ));
        System.out.println("Question: " + exactQ);
        System.out.println("Grounded: " + resp1.grounded());
        System.out.println("Similarity Score: " + resp1.similarityScore());
        System.out.println("Answer: " + resp1.answer());
        System.out.println("Sources count: " + resp1.sources().size());
        if (!resp1.sources().isEmpty()) {
            System.out.println("Top matched source Q: " + resp1.sources().get(0).question());
        }

        assertThat(resp1.grounded()).isTrue();
        assertThat(resp1.similarityScore()).isGreaterThanOrEqualTo(0.50);
        assertThat(resp1.answer()).contains("75%");
        assertThat(resp1.sources().get(0).question()).contains("minimum attendance required");

        // 3. Paraphrased Question Test
        System.out.println("\n=== Test 2: Paraphrased Question ===");
        String paraQ = "How much attendance is needed for terminal semester exams?";
        RagQueryResponse resp2 = ragService.query(new RagQueryRequest(paraQ));
        System.out.println("Question: " + paraQ);
        System.out.println("Grounded: " + resp2.grounded());
        System.out.println("Similarity Score: " + resp2.similarityScore());
        System.out.println("Answer: " + resp2.answer());
        if (!resp2.sources().isEmpty()) {
            System.out.println("Top matched source Q: " + resp2.sources().get(0).question());
        }

        assertThat(resp2.grounded()).isTrue();
        assertThat(resp2.similarityScore()).isGreaterThanOrEqualTo(0.40);
        assertThat(resp2.answer()).contains("75%");
        assertThat(resp2.sources().get(0).question()).contains("minimum attendance required");

        // 4. Unknown Question Test
        System.out.println("\n=== Test 3: Unknown Question ===");
        String unknownQ = "What is the recipe for chocolate cake and brownies?";
        RagQueryResponse resp3 = ragService.query(new RagQueryRequest(unknownQ));
        System.out.println("Question: " + unknownQ);
        System.out.println("Grounded: " + resp3.grounded());
        System.out.println("Similarity Score: " + resp3.similarityScore());
        System.out.println("Answer: " + resp3.answer());
        System.out.println("Sources: " + resp3.sources());

        assertThat(resp3.grounded()).isFalse();
        assertThat(resp3.sources()).isEmpty();
        assertThat(resp3.answer()).contains("couldn't find enough information");

        // 5. Greetings in Real Pipeline
        System.out.println("\n=== Test 4: Greetings in Real Pipeline ===");
        RagQueryResponse g1 = ragService.query(new RagQueryRequest("Hey"));
        assertThat(g1.grounded()).isTrue();
        assertThat(g1.answer()).contains("Hi! 👋 I'm your UBIT RAG-based assistant.");

        RagQueryResponse g2 = ragService.query(new RagQueryRequest("Assalam o Alaikum"));
        assertThat(g2.grounded()).isTrue();
        assertThat(g2.answer()).contains("Wa Alaikum Assalam! 👋");

        // Greeting with real question must still retrieve knowledge base
        RagQueryResponse gMixed = ragService.query(new RagQueryRequest("Hello! What is the minimum attendance required?"));
        assertThat(gMixed.grounded()).isTrue();
        assertThat(gMixed.answer()).contains("75%");
        assertThat(gMixed.sources()).isNotEmpty();

        // 6. Test all Recommended Questions displayed in Frontend
        System.out.println("\n=== Test 5: All Frontend Recommended Questions ===");
        List<String> recommendedQuestions = List.of(
            "What is the minimum attendance required to sit in a final examination?",
            "How do I decide which field to choose: AI, Data Science, Cybersecurity, or Web Development?",
            "What career opportunities are available for computer science graduates in Pakistan and abroad?",
            "Which industry skills should I learn beyond my university syllabus?",
            "How can I determine whether I am ready to apply for an internship?",
            "How should I prepare for a technical interview as a computer science student?"
        );

        for (String q : recommendedQuestions) {
            RagQueryResponse recResp = ragService.query(new RagQueryRequest(q));
            System.out.println("Testing Recommended Q: " + q);
            System.out.println("  -> Grounded: " + recResp.grounded());
            System.out.println("  -> Score:    " + recResp.similarityScore());
            System.out.println("  -> Answer:   " + recResp.answer());

            assertThat(recResp.grounded())
                .as("Recommended question should always be grounded: " + q)
                .isTrue();
            assertThat(recResp.answer())
                .as("Answer should not be blank")
                .isNotBlank();
            assertThat(recResp.answer())
                .as("Must never produce refusal message")
                .doesNotContain("couldn't find enough information");
            assertThat(recResp.sources())
                .as("Sources should not be empty")
                .isNotEmpty();
        }
    }
}
