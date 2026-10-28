package com.ubit.mentorshiphub.rag.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.ubit.mentorshiphub.common.exception.InvalidRequestException;
import com.ubit.mentorshiphub.rag.dto.RagQueryRequest;
import com.ubit.mentorshiphub.rag.dto.RagQueryResponse;
import com.ubit.mentorshiphub.rag.model.RagKnowledgeChunk;
import com.ubit.mentorshiphub.rag.repository.RagKnowledgeChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    @Mock
    private RagKnowledgeChunkRepository chunkRepository;
    @Mock
    private LocalOnnxEmbeddingService localOnnxEmbeddingService;
    @Mock
    private ResourceLoader resourceLoader;

    private QaDocumentParser documentParser;
    private VectorSimilarityCalculator similarityCalculator;
    private RagService ragService;

    private List<RagKnowledgeChunk> mockChunks;

    @BeforeEach
    void setUp() {
        documentParser = new QaDocumentParser();
        similarityCalculator = new VectorSimilarityCalculator();

        ragService = new RagService(
                chunkRepository,
                localOnnxEmbeddingService,
                documentParser,
                similarityCalculator,
                resourceLoader,
                Clock.systemUTC()
        );

        mockChunks = new ArrayList<>();

        // Generate synthetic 384-dimensional test vectors
        List<Double> vec1 = new ArrayList<>(384);
        List<Double> vec2 = new ArrayList<>(384);
        for (int i = 0; i < 384; i++) {
            vec1.add(i == 0 ? 1.0 : 0.0);
            vec2.add(i == 1 ? 1.0 : 0.0);
        }

        RagKnowledgeChunk chunk1 = new RagKnowledgeChunk();
        chunk1.setChunkId("chunk-19");
        chunk1.setQuestion("What is the minimum attendance required to sit in a final examination?");
        chunk1.setAnswer("At least 75% attendance in each course is required to be eligible for that course’s terminal examination.");
        chunk1.setContent("Question: " + chunk1.getQuestion() + "\nAnswer: " + chunk1.getAnswer());
        chunk1.setEmbedding(vec1);
        mockChunks.add(chunk1);

        RagKnowledgeChunk chunk2 = new RagKnowledgeChunk();
        chunk2.setChunkId("chunk-4");
        chunk2.setQuestion("How can I determine whether I am ready to apply for an internship?");
        chunk2.setAnswer("You do not need to know everything before applying. You should understand programming fundamentals and use Git.");
        chunk2.setContent("Question: " + chunk2.getQuestion() + "\nAnswer: " + chunk2.getAnswer());
        chunk2.setEmbedding(vec2);
        mockChunks.add(chunk2);
    }

    @Test
    @DisplayName("Known question retrieves relevant chunk and returns grounded answer")
    void testKnownQuestion() {
        when(chunkRepository.count()).thenReturn((long) mockChunks.size());
        when(chunkRepository.findAll()).thenReturn(mockChunks);

        List<Double> queryVec = new ArrayList<>(384);
        for (int i = 0; i < 384; i++) queryVec.add(i == 0 ? 0.95 : 0.01);
        when(localOnnxEmbeddingService.generateEmbedding(anyString())).thenReturn(queryVec);

        RagQueryResponse response = ragService.query(
                new RagQueryRequest("What is the minimum attendance required to sit in a final examination?")
        );

        assertThat(response.grounded()).isTrue();
        assertThat(response.similarityScore()).isGreaterThanOrEqualTo(0.50);
        assertThat(response.answer()).contains("75% attendance");
        assertThat(response.sources()).isNotEmpty();
        assertThat(response.sources().get(0).chunkId()).isEqualTo("chunk-19");
    }

    @Test
    @DisplayName("Similar/paraphrased question retrieves relevant context via semantic similarity")
    void testSimilarParaphrasedQuestion() {
        when(chunkRepository.count()).thenReturn((long) mockChunks.size());
        when(chunkRepository.findAll()).thenReturn(mockChunks);

        List<Double> queryVec = new ArrayList<>(384);
        for (int i = 0; i < 384; i++) queryVec.add(i == 0 ? 0.85 : 0.05);
        when(localOnnxEmbeddingService.generateEmbedding(anyString())).thenReturn(queryVec);

        RagQueryResponse response = ragService.query(
                new RagQueryRequest("How much attendance is needed for terminal semester exams?")
        );

        assertThat(response.grounded()).isTrue();
        assertThat(response.similarityScore()).isGreaterThanOrEqualTo(0.40);
        assertThat(response.answer()).contains("75% attendance");
        assertThat(response.sources()).isNotEmpty();
        assertThat(response.sources().get(0).chunkId()).isEqualTo("chunk-19");
    }

    @Test
    @DisplayName("Unknown or unrelated question refuses to fabricate an answer")
    void testUnknownQuestion() {
        when(chunkRepository.count()).thenReturn((long) mockChunks.size());
        when(chunkRepository.findAll()).thenReturn(mockChunks);

        List<Double> queryVec = new ArrayList<>(384);
        for (int i = 0; i < 384; i++) queryVec.add(i == 200 ? 1.0 : 0.0);
        when(localOnnxEmbeddingService.generateEmbedding(anyString())).thenReturn(queryVec);

        RagQueryResponse response = ragService.query(
                new RagQueryRequest("What is the recipe for chocolate cake?")
        );

        assertThat(response.grounded()).isFalse();
        assertThat(response.similarityScore()).isLessThan(0.40);
        assertThat(response.answer()).isEqualTo(
                "I couldn't find enough information in the available UBIT knowledge base to answer this question."
        );
        assertThat(response.sources()).isEmpty();
    }

    @Test
    @DisplayName("Empty or blank question throws InvalidRequestException")
    void testEmptyQuestion() {
        assertThatThrownBy(() -> ragService.query(new RagQueryRequest("   ")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("Greeting-only message returns friendly standard response without generating embeddings")
    void testStandardGreetingOnly() {
        RagQueryResponse resp1 = ragService.query(new RagQueryRequest("Hi"));
        assertThat(resp1.grounded()).isTrue();
        assertThat(resp1.answer()).contains("Hi! 👋 I'm your UBIT RAG-based assistant.");
        assertThat(resp1.sources()).isEmpty();

        RagQueryResponse resp2 = ragService.query(new RagQueryRequest("hello!!"));
        assertThat(resp2.grounded()).isTrue();
        assertThat(resp2.answer()).contains("Hi! 👋 I'm your UBIT RAG-based assistant.");

        RagQueryResponse resp3 = ragService.query(new RagQueryRequest("Good Morning!"));
        assertThat(resp3.grounded()).isTrue();
        assertThat(resp3.answer()).contains("Hi! 👋 I'm your UBIT RAG-based assistant.");

        // verify localOnnxEmbeddingService was never invoked for greeting-only messages
        org.mockito.Mockito.verifyNoInteractions(localOnnxEmbeddingService);
    }

    @Test
    @DisplayName("Islamic greeting-only message returns friendly Islamic response without generating embeddings")
    void testIslamicGreetingOnly() {
        RagQueryResponse resp1 = ragService.query(new RagQueryRequest("Salam"));
        assertThat(resp1.grounded()).isTrue();
        assertThat(resp1.answer()).contains("Wa Alaikum Assalam! 👋 I'm your UBIT RAG-based assistant.");
        assertThat(resp1.sources()).isEmpty();

        RagQueryResponse resp2 = ragService.query(new RagQueryRequest("Assalam o Alaikum!"));
        assertThat(resp2.grounded()).isTrue();
        assertThat(resp2.answer()).contains("Wa Alaikum Assalam! 👋");

        RagQueryResponse resp3 = ragService.query(new RagQueryRequest("as-salamu alaykum"));
        assertThat(resp3.grounded()).isTrue();
        assertThat(resp3.answer()).contains("Wa Alaikum Assalam! 👋");

        org.mockito.Mockito.verifyNoInteractions(localOnnxEmbeddingService);
    }

    @Test
    @DisplayName("Mixed greeting with a real question continues through normal retrieval")
    void testGreetingWithRealQuestion() {
        when(chunkRepository.count()).thenReturn((long) mockChunks.size());
        when(chunkRepository.findAll()).thenReturn(mockChunks);

        List<Double> queryVec = new ArrayList<>(384);
        for (int i = 0; i < 384; i++) queryVec.add(i == 0 ? 0.95 : 0.01);
        when(localOnnxEmbeddingService.generateEmbedding(anyString())).thenReturn(queryVec);

        RagQueryResponse response = ragService.query(
                new RagQueryRequest("Hi, what is the minimum attendance required to sit in a final examination?")
        );

        assertThat(response.grounded()).isTrue();
        assertThat(response.answer()).contains("75% attendance");
        assertThat(response.sources()).isNotEmpty();
        assertThat(response.sources().get(0).chunkId()).isEqualTo("chunk-19");
    }
}
