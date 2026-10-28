package com.ubit.mentorshiphub.rag.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HuggingFaceClient {

    private static final Logger log = LoggerFactory.getLogger(HuggingFaceClient.class);

    private final String hfToken;
    private final String embeddingModel;
    private final String chatModel;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HuggingFaceClient(
            @Value("${app.rag.hf-token:${HF_TOKEN:}}") String hfToken,
            @Value("${app.rag.embedding-model:sentence-transformers/all-MiniLM-L6-v2}") String embeddingModel,
            @Value("${app.rag.chat-model:Qwen/Qwen2.5-72B-Instruct}") String chatModel,
            ObjectMapper objectMapper) {
        this.hfToken = hfToken == null ? "" : hfToken.trim();
        this.embeddingModel = embeddingModel;
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public boolean hasToken() {
        return !hfToken.isBlank();
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public String getChatModel() {
        return chatModel;
    }

    /**
     * Calls Hugging Face feature-extraction to compute an embedding vector for the given text.
     */
    public List<Double> generateEmbedding(String text) {
        if (!hasToken()) {
            throw new IllegalStateException("Hugging Face API token (HF_TOKEN) is not configured.");
        }
        try {
            String url = "https://router.huggingface.co/hf-inference/models/" + embeddingModel + "/pipeline/feature-extraction";
            Map<String, Object> payload = Map.of("inputs", text);
            String jsonBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + hfToken)
                    .timeout(Duration.ofSeconds(25))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                String errMsg = response.headers().firstValue("X-Error-Message").orElse("");
                log.error("Hugging Face embedding API call failed [HTTP {}]: {} (Detail: {})",
                        response.statusCode(), response.body(), errMsg);
                throw new RuntimeException("Hugging Face embedding API returned status " + response.statusCode()
                        + (errMsg.isBlank() ? "" : " - " + errMsg));
            }

            JsonNode root = objectMapper.readTree(response.body());
            List<Double> vector = new ArrayList<>();

            if (root.isArray()) {
                // Sometimes HF returns List<Double>, sometimes List<List<Double>>
                if (root.size() > 0 && root.get(0).isArray()) {
                    JsonNode inner = root.get(0);
                    for (JsonNode n : inner) {
                        vector.add(n.asDouble());
                    }
                } else {
                    for (JsonNode n : root) {
                        vector.add(n.asDouble());
                    }
                }
            } else {
                throw new RuntimeException("Unexpected embedding response structure from Hugging Face");
            }

            return vector;
        } catch (Exception e) {
            log.error("Failed to generate embedding via Hugging Face: {}", e.getMessage());
            throw new RuntimeException("Embedding generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Calls Hugging Face OpenAI-compatible chat completions endpoint.
     */
    public String generateChatAnswer(String systemPrompt, String userMessage) {
        if (!hasToken()) {
            throw new IllegalStateException("Hugging Face API token (HF_TOKEN) is not configured.");
        }
        try {
            String url = "https://router.huggingface.co/v1/chat/completions";
            Map<String, Object> payload = Map.of(
                    "model", chatModel,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userMessage)
                    ),
                    "max_tokens", 500,
                    "temperature", 0.2
            );

            String jsonBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + hfToken)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                String errMsg = response.headers().firstValue("X-Error-Message").orElse("");
                log.error("Hugging Face chat completion API call failed [HTTP {}]: {} (Detail: {})",
                        response.statusCode(), response.body(), errMsg);
                throw new RuntimeException("Hugging Face chat API returned status " + response.statusCode()
                        + (errMsg.isBlank() ? "" : " - " + errMsg));
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode messageNode = choices.get(0).get("message");
                if (messageNode != null && messageNode.has("content")) {
                    return messageNode.get("content").asText().trim();
                }
            }
            throw new RuntimeException("Empty choices received from Hugging Face chat completion");
        } catch (Exception e) {
            log.error("Failed to generate chat answer via Hugging Face: {}", e.getMessage());
            throw new RuntimeException("Chat generation failed: " + e.getMessage(), e);
        }
    }
}
