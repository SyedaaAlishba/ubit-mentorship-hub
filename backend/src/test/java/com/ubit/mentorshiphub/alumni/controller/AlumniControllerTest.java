package com.ubit.mentorshiphub.alumni.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ubit.mentorshiphub.alumni.dto.AlumniProfileResponse;
import com.ubit.mentorshiphub.alumni.service.AlumniDiscoveryService;
import com.ubit.mentorshiphub.common.exception.GlobalExceptionHandler;

@ExtendWith(MockitoExtension.class)
class AlumniControllerTest {

    @Mock
    private AlumniDiscoveryService alumniDiscoveryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AlumniController(alumniDiscoveryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Page<AlumniProfileResponse> pageOfOne() {
        return new PageImpl<>(List.of(new AlumniProfileResponse(
                "507f191e810c19729de860aa", "Ali Raza", "ALUMNI", "CS", 5, 2024,
                "Backend engineer", List.of("java"), "Machine learning", List.of("mongo"),
                "Software Engineer", "Acme", "Backend", "https://github.com/ali",
                "https://linkedin.com/in/ali", "https://ali.dev", "Karachi", "EMAIL",
                true, "gridfs-1")), PageRequest.of(0, 20), 1);
    }

    private void stubDirectory(Page<AlumniProfileResponse> page) {
        when(alumniDiscoveryService.discover(ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt(),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any())).thenReturn(page);
    }

    @Test
    @DisplayName("GET /api/alumni returns the directory fields and never the account internals")
    void directoryJsonContainsOnlySafeFields() throws Exception {
        stubDirectory(pageOfOne());

        mockMvc.perform(get("/api/alumni"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].userId").value("507f191e810c19729de860aa"))
                .andExpect(jsonPath("$.content[0].name").value("Ali Raza"))
                .andExpect(jsonPath("$.content[0].role").value("ALUMNI"))
                .andExpect(jsonPath("$.content[0].program").value("CS"))
                .andExpect(jsonPath("$.content[0].currentSemester").value(5))
                .andExpect(jsonPath("$.content[0].passoutYear").value(2024))
                .andExpect(jsonPath("$.content[0].bio").value("Backend engineer"))
                .andExpect(jsonPath("$.content[0].skills[0]").value("java"))
                .andExpect(jsonPath("$.content[0].technologies[0]").value("mongo"))
                .andExpect(jsonPath("$.content[0].jobTitle").value("Software Engineer"))
                .andExpect(jsonPath("$.content[0].company").value("Acme"))
                .andExpect(jsonPath("$.content[0].mentorshipAvailable").value(true))
                .andExpect(jsonPath("$.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.content[0].googleId").doesNotExist())
                .andExpect(jsonPath("$.content[0].verificationStatus").doesNotExist());
    }

    @Test
    @DisplayName("every query parameter is forwarded to the discovery service")
    void allQueryParametersAreForwarded() throws Exception {
        stubDirectory(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/alumni")
                        .param("page", "2")
                        .param("size", "15")
                        .param("role", "SENIOR")
                        .param("program", "cs")
                        .param("passoutYear", "2024")
                        .param("skills", "java,spring")
                        .param("technologies", "mongo")
                        .param("company", "Acme")
                        .param("workArea", "Backend")
                        .param("jobTitle", "Engineer")
                        .param("name", "Ali")
                        .param("mentorshipAvailable", "true"))
                .andExpect(status().isOk());

        ArgumentCaptor<Integer> page = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> size = ArgumentCaptor.forClass(Integer.class);
        verify(alumniDiscoveryService).discover(page.capture(), size.capture(),
                eq("SENIOR"), eq("cs"), eq("2024"),
                eq(List.of("java", "spring")), eq(List.of("mongo")),
                eq("Acme"), eq("Backend"), eq("Engineer"), eq("Ali"), eq("true"));
        assertThat(page.getValue()).isEqualTo(2);
        assertThat(size.getValue()).isEqualTo(15);
    }

    @Test
    @DisplayName("default pagination is page 0 of 20 with no filters")
    void defaultPaginationIsPageZeroOfTwenty() throws Exception {
        stubDirectory(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/alumni")).andExpect(status().isOk());

        ArgumentCaptor<Integer> page = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> size = ArgumentCaptor.forClass(Integer.class);
        verify(alumniDiscoveryService).discover(page.capture(), size.capture(),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull());
        assertThat(page.getValue()).isZero();
        assertThat(size.getValue()).isEqualTo(20);
    }
}