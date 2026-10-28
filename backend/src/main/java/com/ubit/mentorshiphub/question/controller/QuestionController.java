package com.ubit.mentorshiphub.question.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.ubit.mentorshiphub.question.dto.*;
import com.ubit.mentorshiphub.question.model.QuestionCategory;
import com.ubit.mentorshiphub.question.service.QuestionService;

import java.util.List;

@RestController
@RequestMapping("/api/questions")
@Validated
public class QuestionController {
    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @PostMapping
    public ResponseEntity<QuestionResponse> create(@Valid @RequestBody QuestionCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(questionService.createQuestion(req));
    }

    @GetMapping
    public Page<QuestionResponse> list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
                                      @RequestParam(required = false) QuestionCategory category,
                                      @RequestParam(required = false) List<String> tags,
                                      @RequestParam(required = false) String authorId) {
        return questionService.listQuestions(page, size, category, tags, authorId);
    }

    @GetMapping("/{questionId}")
    public QuestionDetailResponse get(@PathVariable String questionId) {
        return questionService.getQuestion(questionId);
    }

    @PatchMapping("/{questionId}")
    public QuestionResponse update(@PathVariable String questionId, @Valid @RequestBody QuestionUpdateRequest req) {
        return questionService.updateQuestion(questionId, req);
    }

    @DeleteMapping("/{questionId}")
    public ResponseEntity<Void> delete(@PathVariable String questionId) {
        questionService.deleteQuestion(questionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{questionId}/close")
    public QuestionResponse close(@PathVariable String questionId) {
        return questionService.closeQuestion(questionId);
    }
}
