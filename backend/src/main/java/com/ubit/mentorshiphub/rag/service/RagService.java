package com.ubit.mentorshiphub.rag.service;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.ubit.mentorshiphub.common.exception.InvalidRequestException;
import com.ubit.mentorshiphub.rag.dto.RagIngestionResponse;
import com.ubit.mentorshiphub.rag.dto.RagQueryRequest;
import com.ubit.mentorshiphub.rag.dto.RagQueryResponse;
import com.ubit.mentorshiphub.rag.model.RagKnowledgeChunk;
import com.ubit.mentorshiphub.rag.repository.RagKnowledgeChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final String DEFAULT_SOURCE_DOC = "knowledge_base_qa.csv";
    private static final double SIMILARITY_THRESHOLD = 0.40;
    private static final int TOP_K = 3;

    private static final String STANDARD_GREETING_RESPONSE =
            "Hi! 👋 I'm your UBIT RAG-based assistant. I can help you with questions about UBIT using the information available in my knowledge base. How can I help you today?";

    private static final String ISLAMIC_GREETING_RESPONSE =
            "Wa Alaikum Assalam! 👋 I'm your UBIT RAG-based assistant. I can help you with questions about UBIT using the information available in my knowledge base. How can I help you today?";

    private static final String SYSTEM_PROMPT = """
            You are the AI assistant for the UBIT Student & Alumni Mentorship Hub.
            Answer the user's question using ONLY the supplied knowledge context.
            If the context does not contain enough information to answer the question, say that the available knowledge base does not contain enough information.
            Do not invent facts.
            Do not claim information came from the knowledge base unless it is actually present in the retrieved context.
            Keep the answer concise and useful.
            """;

    private final RagKnowledgeChunkRepository chunkRepository;
    private final LocalOnnxEmbeddingService localOnnxEmbeddingService;
    private final QaDocumentParser documentParser;
    private final VectorSimilarityCalculator similarityCalculator;
    private final ResourceLoader resourceLoader;
    private final Clock clock;

    public RagService(
            RagKnowledgeChunkRepository chunkRepository,
            LocalOnnxEmbeddingService localOnnxEmbeddingService,
            QaDocumentParser documentParser,
            VectorSimilarityCalculator similarityCalculator,
            ResourceLoader resourceLoader,
            Clock clock) {
        this.chunkRepository = chunkRepository;
        this.localOnnxEmbeddingService = localOnnxEmbeddingService;
        this.documentParser = documentParser;
        this.similarityCalculator = similarityCalculator;
        this.resourceLoader = resourceLoader;
        this.clock = clock;
    }

    public synchronized RagIngestionResponse ingestDefaultKnowledgeBase() {
        try {
            Resource resource = resourceLoader.getResource("classpath:" + DEFAULT_SOURCE_DOC);
            if (!resource.exists()) {
                throw new IllegalStateException("Default knowledge base resource not found: " + DEFAULT_SOURCE_DOC);
            }
            try (InputStream is = resource.getInputStream()) {
                List<QaDocumentParser.ParsedQa> parsedList = documentParser.parseCsv(is);
                if (parsedList.isEmpty()) {
                    return new RagIngestionResponse(0, DEFAULT_SOURCE_DOC, "No QA records found to ingest.");
                }

                // Delete existing chunks from this source document to avoid duplicates
                chunkRepository.deleteBySourceDocument(DEFAULT_SOURCE_DOC);

                Instant now = clock.instant();
                List<RagKnowledgeChunk> toSave = new ArrayList<>();

                for (int i = 0; i < parsedList.size(); i++) {
                    QaDocumentParser.ParsedQa item = parsedList.get(i);
                    String combinedContent = "Question: " + item.question() + "\nAnswer: " + item.answer();

                    // Generate local in-process ONNX embeddings (sentence-transformers/all-MiniLM-L6-v2)
                    List<Double> vector = localOnnxEmbeddingService.generateEmbedding(combinedContent);

                    RagKnowledgeChunk chunk = new RagKnowledgeChunk();
                    chunk.setChunkId("chunk-" + (i + 1));
                    chunk.setSourceDocument(DEFAULT_SOURCE_DOC);
                    chunk.setQuestion(item.question());
                    chunk.setAnswer(item.answer());
                    chunk.setCategory("UBIT FAQ & Guidelines");
                    chunk.setContent(combinedContent);
                    chunk.setEmbedding(vector);
                    chunk.setCreatedAt(now);
                    chunk.setUpdatedAt(now);
                    toSave.add(chunk);
                }

                chunkRepository.saveAll(toSave);
                log.info("Successfully ingested {} Q/A knowledge chunks using Local ONNX model [{}]",
                        toSave.size(), localOnnxEmbeddingService.getModelName());

                return new RagIngestionResponse(
                        toSave.size(),
                        DEFAULT_SOURCE_DOC,
                        "Successfully ingested " + toSave.size() + " Q/A knowledge chunks with "
                                + localOnnxEmbeddingService.getDimension() + "-dim local ONNX vectors."
                );
            }
        } catch (Exception e) {
            log.error("Failed to ingest knowledge base: {}", e.getMessage(), e);
            throw new RuntimeException("Ingestion failed: " + e.getMessage(), e);
        }
    }

    public record ScoredChunk(RagKnowledgeChunk chunk, double score) {}

    public RagQueryResponse query(RagQueryRequest request) {
        if (request == null || request.question() == null || request.question().trim().isBlank()) {
            throw new InvalidRequestException("Question cannot be blank.");
        }
        String userQuestion = request.question().trim();
        if (userQuestion.length() > 1000) {
            throw new InvalidRequestException("Question exceeds maximum length of 1000 characters.");
        }

        // Check for greeting-only message before any embedding or DB work
        String greetingResponse = detectGreetingResponse(userQuestion);
        if (greetingResponse != null) {
            return new RagQueryResponse(
                    userQuestion,
                    greetingResponse,
                    true,
                    1.0,
                    List.of()
            );
        }

        // Auto-ingest if knowledge base is empty
        long count = chunkRepository.count();
        if (count == 0) {
            log.info("Knowledge base is empty. Running initial ingestion...");
            ingestDefaultKnowledgeBase();
        }

        List<RagKnowledgeChunk> allChunks = chunkRepository.findAll();
        if (allChunks.isEmpty()) {
            return new RagQueryResponse(
                    userQuestion,
                    "I couldn't find enough information in the available UBIT knowledge base to answer this question.",
                    false,
                    0.0,
                    List.of()
            );
        }

        // Compute query embedding using the exact same local ONNX model
        List<Double> queryVector = localOnnxEmbeddingService.generateEmbedding(userQuestion);

        // Compute similarity scores against stored knowledge vectors
        List<ScoredChunk> scored = new ArrayList<>();
        for (RagKnowledgeChunk c : allChunks) {
            double score = similarityCalculator.cosineSimilarity(queryVector, c.getEmbedding());
            scored.add(new ScoredChunk(c, score));
        }

        scored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());

        ScoredChunk topMatch = scored.isEmpty() ? null : scored.get(0);
        double topScore = topMatch != null ? topMatch.score() : 0.0;

        if (topMatch == null || topScore < SIMILARITY_THRESHOLD) {
            return new RagQueryResponse(
                    userQuestion,
                    "I couldn't find enough information in the available UBIT knowledge base to answer this question.",
                    false,
                    topScore,
                    List.of()
            );
        }

        // Collect top relevant chunks
        List<ScoredChunk> relevant = scored.stream()
                .filter(s -> s.score() >= SIMILARITY_THRESHOLD)
                .limit(TOP_K)
                .toList();

        StringBuilder contextBuilder = new StringBuilder();
        List<RagQueryResponse.RagSourceInfo> sources = new ArrayList<>();

        for (ScoredChunk sc : relevant) {
            RagKnowledgeChunk c = sc.chunk();
            contextBuilder.append("Question: ").append(c.getQuestion()).append("\n");
            contextBuilder.append("Answer: ").append(c.getAnswer()).append("\n\n");

            sources.add(new RagQueryResponse.RagSourceInfo(
                    c.getChunkId(),
                    c.getQuestion(),
                    c.getAnswer(),
                    c.getCategory() != null ? c.getCategory() : "UBIT FAQ & Guidelines",
                    Math.round(sc.score() * 1000.0) / 1000.0,
                    c.getSourceDocument()
            ));
        }

        String context = contextBuilder.toString().trim();
        // Since no local LLM runner (e.g. Ollama/LMStudio) is installed on this host,
        // and remote paid APIs are disabled, return the verified retrieved chunk answer directly.
        String answer = topMatch.chunk().getAnswer();

        return new RagQueryResponse(
                userQuestion,
                answer,
                true,
                Math.round(topScore * 1000.0) / 1000.0,
                sources
        );
    }

    /**
     * Determines whether user text is purely a greeting.
     * Returns the appropriate greeting response message if matched, or null if it's not a greeting-only message.
     */
    String detectGreetingResponse(String text) {
        if (text == null) {
            return null;
        }

        // Clean punctuation, extra spaces, and emojis to normalize
        // Replace punctuation (.,!?:;~-–—) with spaces
        String normalized = text.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[.,!?:;~\\-–—_]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (normalized.isEmpty()) {
            return null;
        }

        // 1. Check Islamic Greetings
        // Covers: salam, slam, asalam, assalam, assalamu, assalam o alaikum, assalamualaikum,
        // as-salamu alaykum, as-salamu alaikum, assalam u alaikum, etc.
        if (normalized.matches("^(?:as\\s+)?(?:a+s+)?s+a*l+a+m+(?:u+)?(?:\\s*(?:o|u|wa|alay)?\\s*a+l+a+[iy]+k+u+m+)?$")
                || normalized.matches("^as+s+a*l+a+m+u+\\s*(?:a+l+a+[iy]+k+u+m+)?$")) {
            return ISLAMIC_GREETING_RESPONSE;
        }

        // 2. Check Standard Greetings
        // Covers: hi, hello, hey, heya, heyy, good morning, good afternoon, good evening, good day
        if (normalized.matches("^(h+i+|h+e+y+|h+e+l+l+o+|h+e+y+a+|good\\s+(?:morning|afternoon|evening|day))$")) {
            return STANDARD_GREETING_RESPONSE;
        }

        return null;
    }
}
