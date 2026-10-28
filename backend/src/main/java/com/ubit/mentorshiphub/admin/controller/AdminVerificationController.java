package com.ubit.mentorshiphub.admin.controller;

import java.io.InputStream;

import com.ubit.mentorshiphub.admin.dto.AdminVerificationApplicationResponse;
import com.ubit.mentorshiphub.admin.dto.AdminVerificationDecisionRequest;
import com.ubit.mentorshiphub.admin.dto.AdminVerificationDecisionResponse;
import com.ubit.mentorshiphub.admin.service.AdminVerificationService;
import com.ubit.mentorshiphub.admin.service.AdminVerificationService.DocumentDownload;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Administrator endpoints for the verification review queue.
 *
 * <p>Authorization is not declared on these handlers because the project does
 * not use method security: {@code AdminVerificationService} opens every call
 * with {@code AccountAccess.requireCurrentUser()} and the {@code ADMIN} role
 * gate, so the refusal happens before any data is loaded and cannot be
 * forgotten by a future handler that reuses the service.
 *
 * <p>The document endpoint streams GridFS bytes with
 * {@link StreamingResponseBody} rather than returning a byte array: a 5 MB
 * upload is never held on the heap, and the GridFS stream is closed by the same
 * try-with-resources that reads it, whatever the outcome. Images are offered
 * {@code inline} so a reviewer can see a student card in the browser; a PDF is
 * always an {@code attachment}, keeping the browser's PDF parser from running
 * on attacker-supplied input inside this application's own origin.
 */
@RestController
@RequestMapping("/api/admin/verification")
public class AdminVerificationController {

    private final AdminVerificationService adminVerificationService;

    public AdminVerificationController(AdminVerificationService adminVerificationService) {
        this.adminVerificationService = adminVerificationService;
    }

    /**
     * One page of the review queue.
     *
     * <p>{@code status} selects which standing to page through and defaults to
     * {@code PENDING}, the live queue; paging follows the same zero-based
     * convention as the question and alumni lists.
     */
    @GetMapping("/applications")
    public Page<AdminVerificationApplicationResponse> listApplications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return adminVerificationService.listApplications(page, size, status);
    }

    /**
     * The proof belonging to one application.
     *
     * <p>The application id is the only identifier in the request. The GridFS id
     * it maps to is read from the stored application on the server, so there is
     * nothing for a caller to substitute with another application's file.
     */
    @GetMapping("/applications/{applicationId}/document")
    public ResponseEntity<StreamingResponseBody> openDocument(
            @PathVariable String applicationId) {
        DocumentDownload download = adminVerificationService.openDocument(applicationId);
        StreamingResponseBody body = out -> {
            try (InputStream content = download.content()) {
                content.transferTo(out);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, download.contentDisposition())
                .contentLength(download.sizeBytes())
                .body(body);
    }

    /**
     * Records a decision and moves the applicant's account to match.
     *
     * <p>Answers {@code 409} when the application is no longer pending - the
     * usual cause is another administrator deciding it a moment earlier - so a
     * double-submit can never decide the same application twice.
     */
    @PostMapping("/applications/{applicationId}/decide")
    public AdminVerificationDecisionResponse decide(
            @PathVariable String applicationId,
            @Valid @RequestBody AdminVerificationDecisionRequest request) {
        return adminVerificationService.decide(applicationId, request);
    }
}
