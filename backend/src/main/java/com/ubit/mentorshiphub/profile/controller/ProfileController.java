package com.ubit.mentorshiphub.profile.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ubit.mentorshiphub.profile.dto.ProfilePatchRequest;
import com.ubit.mentorshiphub.profile.dto.ProfilePublicResponse;
import com.ubit.mentorshiphub.profile.dto.ProfileResponse;
import com.ubit.mentorshiphub.profile.service.ProfileService;

/**
 * Profile management endpoints.
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/me")
    public ResponseEntity<ProfileResponse> getCurrentProfile() {
        return ResponseEntity.ok(profileService.getCurrentProfile());
    }

    @PatchMapping("/me")
    public ResponseEntity<ProfileResponse> updateCurrentProfile(
            @Valid @RequestBody ProfilePatchRequest request) {
        return ResponseEntity.ok(profileService.updateCurrentProfile(request));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ProfilePublicResponse> getProfile(@PathVariable String userId) {
        return ResponseEntity.ok(profileService.getProfileForUser(userId));
    }
}
