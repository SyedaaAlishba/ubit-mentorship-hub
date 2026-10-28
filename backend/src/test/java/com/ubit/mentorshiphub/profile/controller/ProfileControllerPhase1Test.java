package com.ubit.mentorshiphub.profile.controller;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ubit.mentorshiphub.common.exception.GlobalExceptionHandler;
import com.ubit.mentorshiphub.profile.dto.ProfilePatchRequest;
import com.ubit.mentorshiphub.profile.dto.ProfileResponse;
import com.ubit.mentorshiphub.profile.service.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProfileControllerPhase1Test {

    @Mock
    private ProfileService profileService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProfileController(profileService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("GET /api/profile/me exposes the new professional fields")
    void getMeExposesNewFields() throws Exception {
        ProfileResponse r = new ProfileResponse();
        r.setUserId("507f191e810c19729de860ea");
        r.setMentorshipAvailable(true);
        r.setJobTitle("Backend Developer");
        r.setCompany("ACME Corp");
        r.setWorkArea("Software Engineering");
        r.setTechnologies(List.of("java", "spring"));
        when(profileService.getCurrentProfile()).thenReturn(r);

        mockMvc.perform(get("/api/profile/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("507f191e810c19729de860ea"))
                .andExpect(jsonPath("$.mentorshipAvailable").value(true))
                .andExpect(jsonPath("$.jobTitle").value("Backend Developer"))
                .andExpect(jsonPath("$.company").value("ACME Corp"))
                .andExpect(jsonPath("$.workArea").value("Software Engineering"))
                .andExpect(jsonPath("$.technologies", hasItem("java")));
    }

    @Test
    @DisplayName("PATCH /api/profile/me binds the new fields into the request")
    void patchBindsNewFields() throws Exception {
        ProfileResponse r = new ProfileResponse();
        r.setUserId("507f191e810c19729de860ea");
        when(profileService.updateCurrentProfile(any())).thenReturn(r);

        String body = "{\"mentorshipAvailable\":false,\"jobTitle\":\"Dev\",\"company\":\"ACME\","
                + "\"workArea\":\"Backend\",\"technologies\":[\"Go\"]}";

        mockMvc.perform(patch("/api/profile/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk());

        verify(profileService).updateCurrentProfile(argThat(req ->
                req.getMentorshipAvailable() != null
                        && !req.getMentorshipAvailable()
                        && "Dev".equals(req.getJobTitle())
                        && "ACME".equals(req.getCompany())
                        && "Backend".equals(req.getWorkArea())
                        && req.getTechnologies() != null
                        && req.getTechnologies().contains("Go")));
    }

    @Test
    @DisplayName("PATCH ignores non-editable identity fields in the body")
    void patchIgnoresIdentityFields() throws Exception {
        ProfileResponse r = new ProfileResponse();
        r.setUserId("507f191e810c19729de860ea");
        when(profileService.updateCurrentProfile(any())).thenReturn(r);

        String body = "{\"role\":\"ADMIN\",\"email\":\"hacked@example.com\","
                + "\"googleId\":\"g-1\",\"verificationStatus\":\"VERIFIED\",\"mentorshipAvailable\":true}";

        mockMvc.perform(patch("/api/profile/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk());

        verify(profileService).updateCurrentProfile(argThat(req ->
                req.getMentorshipAvailable() != null
                        && req.getMentorshipAvailable()));
    }
}