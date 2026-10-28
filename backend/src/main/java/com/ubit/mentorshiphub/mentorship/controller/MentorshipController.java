package com.ubit.mentorshiphub.mentorship.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ubit.mentorshiphub.mentorship.dto.EndMentorshipRequest;
import com.ubit.mentorshiphub.mentorship.dto.MentorshipRequestCreateRequest;
import com.ubit.mentorshiphub.mentorship.dto.MentorshipRequestResponse;
import com.ubit.mentorshiphub.mentorship.dto.MentorshipResponse;
import com.ubit.mentorshiphub.mentorship.service.MentorshipService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * The mentorship workflow endpoints.
 *
 * <p>Every call is scoped to the authenticated principal: the requester id is
 * never read from a body, and every list is filtered to the caller's own
 * records. Authorization is decided in the service, so these methods only bind
 * input and delegate.
 */
@RestController
@RequestMapping("/api/mentorship")
@Validated
public class MentorshipController {

    private final MentorshipService mentorshipService;

    public MentorshipController(MentorshipService mentorshipService) {
        this.mentorshipService = mentorshipService;
    }

    @PostMapping("/requests")
    public ResponseEntity<MentorshipRequestResponse> create(@Valid @RequestBody MentorshipRequestCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mentorshipService.createRequest(req));
    }

    @GetMapping("/requests/sent")
    public Page<MentorshipRequestResponse> listSent(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return mentorshipService.listSent(page, size);
    }

    @GetMapping("/requests/received")
    public Page<MentorshipRequestResponse> listReceived(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return mentorshipService.listReceived(page, size);
    }

    @GetMapping("/requests/{requestId}")
    public MentorshipRequestResponse get(@PathVariable String requestId) {
        return mentorshipService.getRequest(requestId);
    }

    @PostMapping("/requests/{requestId}/accept")
    public MentorshipResponse accept(@PathVariable String requestId) {
        return mentorshipService.accept(requestId);
    }

    @PostMapping("/requests/{requestId}/reject")
    public MentorshipRequestResponse reject(@PathVariable String requestId) {
        return mentorshipService.reject(requestId);
    }

    @PostMapping("/requests/{requestId}/cancel")
    public MentorshipRequestResponse cancel(@PathVariable String requestId) {
        return mentorshipService.cancel(requestId);
    }

    @GetMapping("/active")
    public Page<MentorshipResponse> listActive(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return mentorshipService.listActive(page, size);
    }

    @PostMapping("/{mentorshipId}/end")
    public MentorshipResponse end(@PathVariable String mentorshipId,
                                  @Valid @RequestBody(required = false) EndMentorshipRequest req) {
        return mentorshipService.end(mentorshipId, req);
    }
}
