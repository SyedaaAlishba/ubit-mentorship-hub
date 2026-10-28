package com.ubit.mentorshiphub.alumni.dto;

import java.util.List;

/**
 * The parsed and normalised form of the discovery endpoint's query
 * parameters, produced by the service and consumed by the aggregation.
 *
 * <p>{@code page}/{@code size} are already validated, {@code role}/{@code
 * program} hold the validated enum name (or {@code null}), {@code skills} and
 * {@code technologies} are lower-cased, deduplicated, non-blank tokens, and
 * the free-text filters are trimmed (or {@code null}).
 */
public record AlumniDiscoveryQuery(
        int page,
        int size,
        String role,
        String program,
        Integer passoutYear,
        List<String> skills,
        List<String> technologies,
        String company,
        String workArea,
        String jobTitle,
        String name,
        Boolean mentorshipAvailable
) {
}