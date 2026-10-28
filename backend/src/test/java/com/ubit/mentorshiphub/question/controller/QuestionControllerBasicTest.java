package com.ubit.mentorshiphub.question.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ubit.mentorshiphub.common.exception.GlobalExceptionHandler;
import com.ubit.mentorshiphub.question.dto.QuestionCreateRequest;
import com.ubit.mentorshiphub.question.dto.QuestionResponse;
import com.ubit.mentorshiphub.question.model.QuestionCategory;
import com.ubit.mentorshiphub.question.model.QuestionStatus;
import com.ubit.mentorshiphub.question.service.QuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class QuestionControllerTest {
    @Mock
    private QuestionService questionService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new QuestionController(questionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("POST /api/questions creates question")
    void createQuestion() throws Exception {
        QuestionCreateRequest req = new QuestionCreateRequest("Title test", "Body text that is long enough", QuestionCategory.ACADEMIC, List.of("java"));
        QuestionResponse resp = new QuestionResponse("q1", null, "Title test", "Body text that is long enough", QuestionCategory.ACADEMIC, List.of("java"), QuestionStatus.ACTIVE, null, null, null, null);
        when(questionService.createQuestion(any())).thenReturn(resp);
        mockMvc.perform(post("/api/questions").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }
}
