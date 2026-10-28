package com.ubit.mentorshiphub.answer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ubit.mentorshiphub.answer.dto.AnswerCreateRequest;
import com.ubit.mentorshiphub.answer.dto.AnswerResponse;
import com.ubit.mentorshiphub.answer.model.AnswerStatus;
import com.ubit.mentorshiphub.answer.service.AnswerService;
import com.ubit.mentorshiphub.common.exception.GlobalExceptionHandler;
import com.ubit.mentorshiphub.question.dto.AuthorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AnswerControllerTest {
    @Mock private AnswerService answerService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AnswerController(answerService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        objectMapper = new ObjectMapper();
    }

    @Test
    void createAnswer() throws Exception {
        AnswerCreateRequest req = new AnswerCreateRequest("This is a valid answer body that meets minimum length.");
        AnswerResponse resp = new AnswerResponse("a1", "q1", new AuthorResponse("u1", "Ali", null, null), req.body(), AnswerStatus.ACTIVE, false, null, null, null);
        when(answerService.createAnswer(eq("q1"), any())).thenReturn(resp);
        mockMvc.perform(post("/api/questions/q1/answers").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }
}
