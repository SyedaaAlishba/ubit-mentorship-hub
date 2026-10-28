package com.ubit.mentorshiphub.alumni.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.ubit.mentorshiphub.alumni.dto.AlumniDiscoveryQuery;
import com.ubit.mentorshiphub.alumni.dto.AlumniProfileResponse;
import com.ubit.mentorshiphub.alumni.repository.AlumniDiscoveryRepository;
import com.ubit.mentorshiphub.common.exception.InvalidRequestException;
import com.ubit.mentorshiphub.common.security.AccountAccess;
import com.ubit.mentorshiphub.common.security.AuthorizationGates;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.verification.model.Program;

/**
 * Discovers alumni directory entries for a verified participant.
 *
 * <p>Only the caller and validation stay here. The cross-collection query
 * lives in {@link AlumniDiscoveryRepository} and field mapping is handled so
 * that the response never exposes account internals.
 */
@Service
public class AlumniDiscoveryService {

    /** The order the directory is advertised under, matching the aggregation. */
    static final Sort DIRECTORY_SORT = Sort.by(Sort.Direction.DESC, "passoutYear")
            .and(Sort.by(Sort.Direction.ASC, "name"))
            .and(Sort.by(Sort.Direction.ASC, "id"));

    private final AlumniDiscoveryRepository repository;
    private final AccountAccess accountAccess;

    public AlumniDiscoveryService(AlumniDiscoveryRepository repository, AccountAccess accountAccess) {
        this.repository = repository;
        this.accountAccess = accountAccess;
    }

    /**
     * The alumni directory page for the current request.
     *
     * @throws com.ubit.mentorshiphub.common.exception.AccountNotFoundException if no account is signed in
     * @throws com.ubit.mentorshiphub.common.exception.AccountDisabledException if the account is disabled
     * @throws com.ubit.mentorshiphub.common.exception.VerificationRequiredException if the account is not verified
     * @throws com.ubit.mentorshiphub.common.exception.InvalidRequestException if any parameter is invalid
     */
    public Page<AlumniProfileResponse> discover(int page, int size, String role, String program,
                                                String passoutYear, List<String> skills,
                                                List<String> technologies, String company, String workArea,
                                                String jobTitle, String name, String mentorshipAvailable) {
        User current = accountAccess.requireCurrentUser();
        AuthorizationGates.requireOnboardingComplete(current);
        if (page < 0) {
            throw new InvalidRequestException("page must be >= 0");
        }
        if (size < 1 || size > 50) {
            throw new InvalidRequestException("size must be between 1 and 50");
        }

        AlumniDiscoveryQuery query = new AlumniDiscoveryQuery(
                page, size,
                parseRole(role),
                parseProgram(program),
                parsePassoutYear(passoutYear),
                normalizeTokens(skills),
                normalizeTokens(technologies),
                trimToNull(company),
                trimToNull(workArea),
                trimToNull(jobTitle),
                trimToNull(name),
                parseMentorship(mentorshipAvailable));

        AlumniDiscoveryRepository.AlumniDiscoveryResult result = repository.search(query);
        List<AlumniProfileResponse> content = result.data().stream().map(this::toResponse).toList();
        return new PageImpl<>(content, PageRequest.of(page, size, DIRECTORY_SORT), result.total());
    }

    /**
     * Maps one aggregation document into the public response. Academic fields
     * come from the user's newest <b>verified</b> application (selected in the
     * pipeline): the program is surfaced only when that claim is a STUDENT one
     * and the pass-out year only when it is an ALUMNI one, so a field the claim
     * never carried is never invented. The current semester follows whichever
     * type the claim is.
     */
    private AlumniProfileResponse toResponse(Map<String, Object> doc) {
        Map<String, Object> profile = nested(doc.get("profile"));
        Map<String, Object> verification = nested(doc.get("verification"));
        String applicationType = verification == null ? null : asString(verification.get("type"));
        boolean latestApplicationIsStudent = "STUDENT".equals(applicationType);
        boolean latestApplicationIsAlumni = "ALUMNI".equals(applicationType);

        return new AlumniProfileResponse(
                asString(doc.get("_id")),
                asString(doc.get("name")),
                asString(doc.get("role")),
                latestApplicationIsStudent ? asString(verification.get("program")) : null,
                verification == null ? null : asInteger(verification.get("currentSemester")),
                latestApplicationIsAlumni ? asInteger(verification.get("passoutYear")) : null,
                profileField(profile, "bio"),
                listField(profile, "skills"),
                profileField(profile, "interests"),
                listField(profile, "technologies"),
                profileField(profile, "jobTitle"),
                profileField(profile, "company"),
                profileField(profile, "workArea"),
                profileField(profile, "githubUrl"),
                profileField(profile, "linkedinUrl"),
                profileField(profile, "websiteUrl"),
                profileField(profile, "location"),
                profileField(profile, "preferredContact"),
                mentorshipAvailable(profile),
                profileField(profile, "avatarGridFsId"));
    }

    private String parseRole(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        String role = raw.trim().toUpperCase(Locale.ROOT);
        if (!"ALUMNI".equals(role) && !"SENIOR".equals(role)) {
            throw new InvalidRequestException("role must be ALUMNI or SENIOR");
        }
        return role;
    }

    private String parseProgram(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        String program = raw.trim().toUpperCase(Locale.ROOT);
        for (Program value : Program.values()) {
            if (value.name().equals(program)) {
                return program;
            }
        }
        throw new InvalidRequestException("program must be one of: " + Arrays.toString(Program.values()));
    }

    private Integer parsePassoutYear(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new InvalidRequestException("passoutYear must be a number");
        }
    }

    private Boolean parseMentorship(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "true" -> Boolean.TRUE;
            case "false" -> Boolean.FALSE;
            default -> throw new InvalidRequestException("mentorshipAvailable must be true or false");
        };
    }

    private List<String> normalizeTokens(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String token : tokens) {
            if (isBlank(token)) {
                continue;
            }
            normalized.add(token.trim().toLowerCase(Locale.ROOT));
        }
        return List.copyOf(normalized);
    }

    private String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String profileField(Map<String, Object> profile, String key) {
        return asString(nestedValue(profile, key));
    }

    private List<String> listField(Map<String, Object> profile, String key) {
        Object value = nestedValue(profile, key);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object element : list) {
            values.add(element == null ? null : String.valueOf(element));
        }
        return values;
    }

    private Object nestedValue(Map<String, Object> profile, String key) {
        return profile == null ? null : profile.get(key);
    }

    private boolean mentorshipAvailable(Map<String, Object> profile) {
        return profile != null && Boolean.TRUE.equals(profile.get("mentorshipAvailable"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nested(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ObjectId id) {
            return id.toHexString();
        }
        return String.valueOf(value);
    }

    private Integer asInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }
}