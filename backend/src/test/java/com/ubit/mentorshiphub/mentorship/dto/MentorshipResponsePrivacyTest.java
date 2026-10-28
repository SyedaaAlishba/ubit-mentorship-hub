package com.ubit.mentorshiphub.mentorship.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ubit.mentorshiphub.mentorship.model.MentorshipRequestStatus;
import com.ubit.mentorshiphub.mentorship.model.MentorshipStatus;
import com.ubit.mentorshiphub.user.model.AppRole;

/**
 * Guards the wire shape of the mentorship DTOs.
 *
 * <p>The service resolves display data from the account and profile into a
 * deliberately narrow participant record. These tests assert that the internal
 * pair identity and any account-private field can never appear in the JSON, so a
 * future refactor that reaches for the entity by mistake fails here rather than
 * in production.
 */
class MentorshipResponsePrivacyTest {

    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    @DisplayName("a serialized request exposes only the agreed fields")
    void requestJsonHasOnlyAgreedFields() throws Exception {
        String json = mapper.writeValueAsString(new MentorshipRequestResponse(
                "req1", participant("Student"), participant("Mentor"), "Please mentor me.",
                "Learn Spring", MentorshipRequestStatus.PENDING, NOW, NOW, null));

        assertThat(fieldNames(json)).containsExactlyInAnyOrder(
                "id", "requester", "recipient", "message", "goal", "status", "createdAt", "updatedAt", "respondedAt");
        assertThat(json).doesNotContain("pairKey", "requesterUserId", "recipientUserId");
    }

    @Test
    @DisplayName("a serialized mentorship exposes only the agreed fields")
    void mentorshipJsonHasOnlyAgreedFields() throws Exception {
        String json = mapper.writeValueAsString(new MentorshipResponse(
                "m1", participant("Mentor"), participant("Student"), "req1",
                MentorshipStatus.ACTIVE, NOW, null, null, null, NOW, NOW));

        assertThat(fieldNames(json)).containsExactlyInAnyOrder(
                "id", "mentor", "mentee", "requestId", "status", "startedAt", "endedAt",
                "endedByUserId", "endReason", "createdAt", "updatedAt");
        assertThat(json).doesNotContain("pairKey", "mentorUserId", "menteeUserId");
    }

    @Test
    @DisplayName("a participant carries no account-private or moderation fields")
    void participantJsonIsNarrow() throws Exception {
        String json = mapper.writeValueAsString(participant("Mentor"));

        assertThat(fieldNames(json)).containsExactlyInAnyOrder("userId", "name", "role", "avatarGridFsId");
        assertThat(json).doesNotContain(
                "email", "googleId", "verificationStatus", "reviewerNote", "enabled");
    }

    private List<String> fieldNames(String json) throws Exception {
        JsonNode node = mapper.readTree(json);
        List<String> names = new java.util.ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private MentorshipParticipantResponse participant(String name) {
        return new MentorshipParticipantResponse("64b0000000000000000000a1", name, AppRole.ALUMNI, "avatar-1");
    }
}
