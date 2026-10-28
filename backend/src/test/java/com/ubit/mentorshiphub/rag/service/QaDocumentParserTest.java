package com.ubit.mentorshiphub.rag.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QaDocumentParserTest {

    private final QaDocumentParser parser = new QaDocumentParser();

    @Test
    @DisplayName("Parses standard and quoted CSV rows properly")
    void testParseCsv() {
        String csv = """
                Questions,Answers
                "What is mentorship?","Mentorship is a professional relationship."
                What is attendance requirement?,At least 75% attendance is required.
                """;

        List<QaDocumentParser.ParsedQa> parsed = parser.parseCsv(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(parsed).hasSize(2);
        assertThat(parsed.get(0).question()).isEqualTo("What is mentorship?");
        assertThat(parsed.get(0).answer()).isEqualTo("Mentorship is a professional relationship.");
        assertThat(parsed.get(1).question()).isEqualTo("What is attendance requirement?");
        assertThat(parsed.get(1).answer()).isEqualTo("At least 75% attendance is required.");
    }
}
