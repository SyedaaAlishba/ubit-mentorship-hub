package com.ubit.mentorshiphub.verification.controller;

import java.util.Optional;

import com.ubit.mentorshiphub.common.security.AccountAccess;
import com.ubit.mentorshiphub.verification.dto.AlumniVerificationRequest;
import com.ubit.mentorshiphub.verification.dto.StudentVerificationRequest;
import com.ubit.mentorshiphub.verification.dto.VerificationApplicationResponse;
import com.ubit.mentorshiphub.verification.service.VerificationApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Verification submission endpoints.
 *
 * <p>Every method takes the applicant from the session via {@link AccountAccess}
 * and ignores anything identifying in the request. There is no user id parameter
 * anywhere, so there is nothing for a caller to tamper with and no ownership
 * check to forget.
 *
 * <p>Submitting moves the account to {@code PENDING} and answers
 * {@code 202 Accepted}: the claim has been recorded but nobody has decided on it
 * yet, which is exactly what a client must not read as success.
 */
@RestController
@RequestMapping("/api/verification")
public class VerificationController {

    private final AccountAccess accountAccess;
    private final VerificationApplicationService verificationService;

    public VerificationController(AccountAccess accountAccess,
                                  VerificationApplicationService verificationService) {
        this.accountAccess = accountAccess;
        this.verificationService = verificationService;
    }

    /**
     * Records a student claim and its uploaded proof.
     *
     * <p>The three claim fields are bound to {@link StudentVerificationRequest} by
     * Spring, so the constraints declared on that record are what actually rejects
     * bad input - a semester outside 1-8 or an unknown programme is a 400. The
     * upload stays a separate parameter because a model attribute cannot carry a
     * file.
     */
    @PostMapping(value = "/student", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public VerificationApplicationResponse submitStudent(
            @Valid @ModelAttribute StudentVerificationRequest request,
            @RequestParam(StudentVerificationRequest.FILE_FIELD) MultipartFile document) {

        return verificationService.submitStudent(accountAccess.requireCurrentUser(), request, document);
    }

    /** Records an alumnus claim. Supporting evidence only; no document required. */
    @PostMapping(value = "/alumni", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public VerificationApplicationResponse submitAlumni(@Valid @RequestBody AlumniVerificationRequest request) {
        return verificationService.submitAlumni(accountAccess.requireCurrentUser(), request);
    }

    /**
     * The signed-in account's most recent application, so the status screen can
     * show the rejection reason and the current attempt. Answers {@code 404} when
     * nothing has been submitted yet, which the client reads as "not applied".
     */
    @GetMapping("/me")
    public ResponseEntity<VerificationApplicationResponse> currentApplication() {
        Optional<VerificationApplicationResponse> current =
                verificationService.currentApplication(accountAccess.requireCurrentUser());
        return current.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
