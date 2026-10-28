package com.ubit.mentorshiphub.answer.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.ubit.mentorshiphub.answer.dto.AnswerCreateRequest;
import com.ubit.mentorshiphub.answer.dto.AnswerResponse;
import com.ubit.mentorshiphub.answer.dto.AnswerUpdateRequest;
import com.ubit.mentorshiphub.answer.service.AnswerService;
import com.ubit.mentorshiphub.question.service.QuestionService;

@RestController
@RequestMapping("/api")
@Validated
public class AnswerController {
    private final AnswerService answerService;

    public AnswerController(AnswerService answerService) {
        this.answerService = answerService;
    }

    @GetMapping("/questions/{questionId}/answers")
    public Page<AnswerResponse> list(@PathVariable String questionId,
                                    @RequestParam(defaultValue = "0") @Min(0) int page,
                                    @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return answerService.listAnswers(questionId, page, size);
    }

    @PostMapping("/questions/{questionId}/answers")
    public ResponseEntity<AnswerResponse> create(@PathVariable String questionId,
                                                 @Valid @RequestBody AnswerCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(answerService.createAnswer(questionId, req));
    }

    @PatchMapping("/answers/{answerId}")
    public AnswerResponse update(@PathVariable String answerId, @Valid @RequestBody AnswerUpdateRequest req) {
        return answerService.updateAnswer(answerId, req);
    }

    @DeleteMapping("/answers/{answerId}")
    public ResponseEntity<Void> delete(@PathVariable String answerId) {
        answerService.deleteAnswer(answerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/questions/{questionId}/answers/{answerId}/accept")
    public ResponseEntity<Object> accept(@PathVariable String questionId, @PathVariable String answerId) {
        answerService.acceptAnswer(questionId, answerId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/questions/{questionId}/answers/{answerId}/accept")
    public ResponseEntity<Void> unaccept(@PathVariable String questionId, @PathVariable String answerId) {
        answerService.unacceptAnswer(questionId, answerId);
        return ResponseEntity.noContent().build();
    }
}
