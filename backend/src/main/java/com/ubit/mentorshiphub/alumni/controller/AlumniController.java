package com.ubit.mentorshiphub.alumni.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ubit.mentorshiphub.alumni.dto.AlumniProfileResponse;
import com.ubit.mentorshiphub.alumni.service.AlumniDiscoveryService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * The alumni directory: a verified participant can page through the profile
 * projections of verified alumni and senior mentors.
 */
@RestController
@RequestMapping("/api/alumni")
@Validated
public class AlumniController {

    private final AlumniDiscoveryService alumniDiscoveryService;

    public AlumniController(AlumniDiscoveryService alumniDiscoveryService) {
        this.alumniDiscoveryService = alumniDiscoveryService;
    }

    /** The role, program, pass-out year and presence filters are parsed as
     * strings and validated by the service so an unusable value is a 400,
     * not a binding failure. */
    @GetMapping
    public Page<AlumniProfileResponse> discover(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String program,
            @RequestParam(required = false) String passoutYear,
            @RequestParam(required = false) List<String> skills,
            @RequestParam(required = false) List<String> technologies,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String workArea,
            @RequestParam(required = false) String jobTitle,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String mentorshipAvailable) {
        return alumniDiscoveryService.discover(page, size, role, program, passoutYear,
                skills, technologies, company, workArea, jobTitle, name, mentorshipAvailable);
    }
}