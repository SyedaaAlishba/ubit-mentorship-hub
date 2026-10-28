package com.ubit.mentorshiphub.rag.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class QaDocumentParser {

    public record ParsedQa(String question, String answer) {}

    public List<ParsedQa> parseCsv(InputStream inputStream) {
        List<ParsedQa> results = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            boolean isHeader = true;
            StringBuilder currentRecord = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }
                if (currentRecord.length() > 0) {
                    currentRecord.append("\n");
                }
                currentRecord.append(line);

                // Check if quotes are balanced
                if (isQuoteBalanced(currentRecord.toString())) {
                    List<String> tokens = parseCsvLine(currentRecord.toString());
                    if (tokens.size() >= 2) {
                        String q = cleanField(tokens.get(0));
                        String a = cleanField(tokens.get(1));
                        if (!q.isBlank() && !a.isBlank()) {
                            results.add(new ParsedQa(q, a));
                        }
                    }
                    currentRecord.setLength(0);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse QA document: " + e.getMessage(), e);
        }
        return results;
    }

    private boolean isQuoteBalanced(String s) {
        int quotes = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '"') {
                quotes++;
            }
        }
        return quotes % 2 == 0;
    }

    private String cleanField(String field) {
        if (field == null) return "";
        String trimmed = field.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed.replace("\"\"", "\"");
    }

    private List<String> parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                cur.append(c);
            } else if (c == ',' && !inQuotes) {
                tokens.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        tokens.add(cur.toString());
        return tokens;
    }
}
