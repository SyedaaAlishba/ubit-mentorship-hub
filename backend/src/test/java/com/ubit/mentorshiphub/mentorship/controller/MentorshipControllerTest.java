package com.ubit.mentorshiphub.mentorship.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ubit.mentorshiphub.common.exception.GlobalExceptionHandler;
import com.ubit.mentorshiphub.common.exception.InvalidRequestException;
import com.ubit.mentorshiphub.common.exception.MentorshipConflictException;
import com.ubit.mentorshiphub.common.exception.MentorshipForbiddenException;
import com.ubit.mentorshiphub.mentorship.dto.MentorshipParticipantResponse;
import com.ubit.mentorshiphub.mentorship.dto.MentorshipRequestCreateRequest;
import com.ubit.mentorshiphub.mentorship.dto.MentorshipRequestResponse;
import com.ubit.mentorshiphub.mentorship.dto.MentorshipResponse;
import com.ubit.mentorshiphub.mentorship.model.MentorshipRequestStatus;
import com.ubit.mentorshiphub.mentorship.model.MentorshipStatus;
import com.ubit.mentorshiphub.mentorship.service.MentorshipService;
import com.ubit.mentorshiphub.user.model.AppRole;

/**
 * The mentorship endpoints: status codes and the shape of the JSON, with the
 * business rules stubbed out. The rules themselves are covered by the service
 * tests.
 */
@ExtendWith(MockitoExtension.class)
class MentorshipControllerTest {

    private static final String STUDENT = "64b0000000000000000000a1";
    private static final String MENTOR = "64b0000000000000000000b2";
    private static final String REQUEST_ID = "64b0000000000000000000d4";
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    @Mock
    private MentorshipService mentorshipService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MentorshipController(mentorshipService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("POST /api/mentorship/requests creates a pending request")
    void createReturnsCreated() throws Exception {
        when(mentorshipService.createRequest(any())).thenReturn(pendingResponse());

        mockMvc.perform(post("/api/mentorship/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MentorshipRequestCreateRequest(
                                MENTOR, "I would value your guidance on backend engineering.", "Learn Spring"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requester.userId").value(STUDENT))
                .andExpect(jsonPath("$.recipient.userId").value(MENTOR))
                .andExpect(jsonPath("$.pairKey").doesNotExist());
    }

    @Test
    @DisplayName("a message below the minimum length is refused before the service runs")
    void shortMessageIsRejected() throws Exception {
        mockMvc.perform(post("/api/mentorship/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MentorshipRequestCreateRequest(
                                MENTOR, "short", null))))
                .andExpect(status().isBadRequest());

        verify(mentorshipService, never()).createRequest(any());
    }

    @Test
    @DisplayName("GET /api/mentorship/requests/sent returns the caller's requests")
    void listSentReturnsPage() throws Exception {
        Page<MentorshipRequestResponse> page =
                new PageImpl<>(List.of(pendingResponse()), PageRequest.of(0, 20), 1);
        when(mentorshipService.listSent(eq(0), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/mentorship/requests/sent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/mentorship/requests/{id} returns a single request")
    void getReturnsRequest() throws Exception {
        when(mentorshipService.getRequest(REQUEST_ID)).thenReturn(pendingResponse());

        mockMvc.perform(get("/api/mentorship/requests/" + REQUEST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(REQUEST_ID));
    }

    @Test
    @DisplayName("POST accept returns the new mentorship")
    void acceptReturnsMentorship() throws Exception {
        when(mentorshipService.accept(REQUEST_ID)).thenReturn(activeResponse());

        mockMvc.perform(post("/api/mentorship/requests/" + REQUEST_ID + "/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.mentor.userId").value(MENTOR))
                .andExpect(jsonPath("$.mentee.userId").value(STUDENT));
    }

    @Test
    @DisplayName("a disallowed participant action surfaces as a 403")
    void forbiddenActionIsMapped() throws Exception {
        when(mentorshipService.accept(REQUEST_ID))
                .thenThrow(new MentorshipForbiddenException("Only the recipient can accept this request."));

        mockMvc.perform(post("/api/mentorship/requests/" + REQUEST_ID + "/accept"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a state conflict surfaces as a 409")
    void conflictIsMapped() throws Exception {
        when(mentorshipService.cancel(REQUEST_ID))
                .thenThrow(new MentorshipConflictException("This request is no longer pending and cannot be cancelled."));

        mockMvc.perform(post("/api/mentorship/requests/" + REQUEST_ID + "/cancel"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /api/mentorship/active returns the caller's active mentorships")
    void listActiveReturnsPage() throws Exception {
        Page<MentorshipResponse> page = new PageImpl<>(List.of(activeResponse()), PageRequest.of(0, 20), 1);
        when(mentorshipService.listActive(eq(0), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/mentorship/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("64b0000000000000000000e5"));
    }

    @Test
    @DisplayName("POST end accepts an optional reason")
    void endReturnsEndedMentorship() throws Exception {
        when(mentorshipService.end(eq("64b0000000000000000000e5"), any())).thenReturn(endedResponse());

        mockMvc.perform(post("/api/mentorship/64b0000000000000000000e5/end")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Goals met\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"))
                .andExpect(jsonPath("$.endReason").value("Goals met"));
    }

    @Test
    @DisplayName("POST end with no body is accepted and the reason is absent")
    void endWithoutBodyIsAccepted() throws Exception {
        when(mentorshipService.end(eq("64b0000000000000000000e5"), isNull())).thenReturn(endedResponse());

        mockMvc.perform(post("/api/mentorship/64b0000000000000000000e5/end"))
                .andExpect(status().isOk());

        verify(mentorshipService).end(eq("64b0000000000000000000e5"), isNull());
    }

    @Test
    @DisplayName("the pagination parameters are forwarded to the service")
    void paginationIsForwarded() throws Exception {
        when(mentorshipService.listReceived(2, 15))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 15), 0));

        mockMvc.perform(get("/api/mentorship/requests/received")
                        .param("page", "2")
                        .param("size", "15"))
                .andExpect(status().isOk());

        verify(mentorshipService).listReceived(2, 15);
    }

    @Test
    @DisplayName("a bad-input failure surfaces as a 400")
    void badInputIsMapped() throws Exception {
        when(mentorshipService.createRequest(any()))
                .thenThrow(new InvalidRequestException("recipientUserId is not a valid id."));

        mockMvc.perform(post("/api/mentorship/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MentorshipRequestCreateRequest(
                                "not-an-object-id", "I would value your guidance on backend engineering.", null))))
                .andExpect(status().isBadRequest());
    }

    private MentorshipResponse endedResponse() {
        return new MentorshipResponse("64b0000000000000000000e5",
                participant(MENTOR, "Mentor", AppRole.ALUMNI),
                participant(STUDENT, "Student", AppRole.STUDENT),
                REQUEST_ID, MentorshipStatus.ENDED, NOW, NOW, STUDENT, "Goals met", NOW, NOW);
    }

    private MentorshipRequestResponse pendingResponse() {
        return new MentorshipRequestResponse(REQUEST_ID,
                participant(STUDENT, "Student", AppRole.STUDENT),
                participant(MENTOR, "Mentor", AppRole.ALUMNI),
                "I would value your guidance on backend engineering.", "Learn Spring",
                MentorshipRequestStatus.PENDING, NOW, NOW, null);
    }

    private MentorshipResponse activeResponse() {
        return new MentorshipResponse("64b0000000000000000000e5",
                participant(MENTOR, "Mentor", AppRole.ALUMNI),
                participant(STUDENT, "Student", AppRole.STUDENT),
                REQUEST_ID, MentorshipStatus.ACTIVE, NOW, null, null, null, NOW, NOW);
    }

    private MentorshipParticipantResponse participant(String id, String name, AppRole role) {
        return new MentorshipParticipantResponse(id, name, role, null);
    }
}
