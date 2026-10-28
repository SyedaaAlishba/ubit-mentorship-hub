package com.ubit.mentorshiphub.profile.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ubit.mentorshiphub.common.exception.GlobalExceptionHandler;
import com.ubit.mentorshiphub.profile.dto.ProfilePatchRequest;
import com.ubit.mentorshiphub.profile.dto.ProfilePublicResponse;
import com.ubit.mentorshiphub.profile.dto.ProfileResponse;
import com.ubit.mentorshiphub.profile.model.PreferredContact;
import com.ubit.mentorshiphub.profile.model.Visibility;
import com.ubit.mentorshiphub.profile.service.ProfileService;
import com.ubit.mentorshiphub.user.model.AppRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    private ProfileService profileService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProfileController(profileService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

private ProfileResponse sampleResponse() {
        ProfileResponse r = new ProfileResponse();
        r.setUserId("507f191e810c19729de860ea");
        r.setName("Ali");
        r.setEmail("ali@example.com");
        r.setRole(AppRole.STUDENT);
        r.setVisibility(Visibility.VERIFIED_ONLY);
        r.setPreferredContact(PreferredContact.EMAIL);
        return r;
    }

    @Test
    @DisplayName("GET /api/profile/me returns current profile")
    void getCurrentProfile() throws Exception {
        when(profileService.getCurrentProfile()).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/profile/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("507f191e810c19729de860ea"))
                .andExpect(jsonPath("$.visibility").value("VERIFIED_ONLY"));
    }

@Test
    @DisplayName("PATCH /api/profile/me updates profile")
    void updateCurrentProfile() throws Exception {
        ProfilePatchRequest req = new ProfilePatchRequest();
        req.setBio("Updated bio");
        req.setGithubUrl("https://github.com/new");
        req.setVisibility("PRIVATE");
        ProfileResponse resp = sampleResponse();
        when(profileService.updateCurrentProfile(any())).thenReturn(resp);

        mockMvc.perform(patch("/api/profile/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
        verify(profileService).updateCurrentProfile(any(ProfilePatchRequest.class));
    }

@Test
    @DisplayName("GET /api/profile/{userId} returns public profile")
    void getPublicProfile() throws Exception {
        ProfilePublicResponse pub = new ProfilePublicResponse();
        pub.setUserId("507f191e810c19729de860eb");
        pub.setName("Sara");
        pub.setRole("ALUMNI");
        pub.setVisibility(Visibility.PUBLIC);
        pub.setPreferredContact(PreferredContact.NONE);
        when(profileService.getProfileForUser(eq("507f191e810c19729de860eb"))).thenReturn(pub);

mockMvc.perform(get("/api/profile/507f191e810c19729de860eb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.verificationStatus").doesNotExist())
                .andExpect(jsonPath("$.googleId").doesNotExist())
                .andExpect(jsonPath("$.userId").value("507f191e810c19729de860eb"));
    }
}
