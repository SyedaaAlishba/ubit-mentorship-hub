package com.ubit.mentorshiphub.profile.service;

import java.net.URI;
import java.util.regex.Pattern;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import com.ubit.mentorshiphub.common.exception.InvalidRequestException;
import com.ubit.mentorshiphub.common.exception.ResourceNotFoundException;
import com.ubit.mentorshiphub.common.exception.VerificationRequiredException;
import com.ubit.mentorshiphub.common.security.AccountAccess;
import com.ubit.mentorshiphub.common.security.AuthorizationGates;
import com.ubit.mentorshiphub.profile.dto.ProfileContext;
import com.ubit.mentorshiphub.profile.dto.ProfilePatchRequest;
import com.ubit.mentorshiphub.profile.dto.ProfilePublicResponse;
import com.ubit.mentorshiphub.profile.dto.ProfileResponse;
import com.ubit.mentorshiphub.profile.model.PreferredContact;
import com.ubit.mentorshiphub.profile.model.Profile;
import com.ubit.mentorshiphub.profile.model.Visibility;
import com.ubit.mentorshiphub.profile.repository.ProfileRepository;
import com.ubit.mentorshiphub.user.model.AppRole;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.user.model.VerificationStatus;
import com.ubit.mentorshiphub.user.service.UserService;
import com.ubit.mentorshiphub.verification.model.ApplicationType;
import com.ubit.mentorshiphub.verification.model.VerificationApplication;
import com.ubit.mentorshiphub.verification.repository.VerificationApplicationRepository;

@Service
public class ProfileService {

    private static final int MAX_SKILLS = 20;
    private static final int MAX_INTERESTS = 20;
    private static final Pattern LOCATION_PATTERN = Pattern.compile("^[a-zA-Z0-9\\s,.'\\-–/()#]{2,100}$");

    private static final int MAX_TAG_LENGTH = 50;

    private final ProfileRepository profileRepository;
    private final UserService userService;
    private final VerificationApplicationRepository verificationApplicationRepository;
    private final AccountAccess accountAccess;
    private final Clock clock;

    public ProfileService(ProfileRepository profileRepository,
                          UserService userService,
                          VerificationApplicationRepository verificationApplicationRepository,
                          AccountAccess accountAccess,
                          Clock clock) {
        this.profileRepository = profileRepository;
        this.userService = userService;
        this.verificationApplicationRepository = verificationApplicationRepository;
        this.accountAccess = accountAccess;
        this.clock = clock;
    }

    public ProfileResponse getCurrentProfile() {
        User current = accountAccess.requireCurrentUser();
        Profile profile = findOrCreateProfile(current.getId());
        return toResponse(profile, current);
    }

    public ProfileResponse updateCurrentProfile(ProfilePatchRequest request) {
        User current = accountAccess.requireCurrentUser();
        Profile profile = findOrCreateProfile(current.getId());
        applyPatch(profile, request);
        profile.setUpdatedAt(clock.instant());
        profile = profileRepository.save(profile);
        return toResponse(profile, current);
    }

    public ProfilePublicResponse getProfileForUser(String userIdHex) {
        ObjectId targetUserId = parseUserId(userIdHex);
        User requester = accountAccess.requireCurrentUser();
        User target = userService.findById(targetUserId).orElse(null);

        if (target == null) {
            throw new ResourceNotFoundException("Profile not found.");
        }
        if (!target.isEnabled()) {
            throw new ResourceNotFoundException("Profile not found.");
        }

        if (requester.getId().equals(target.getId())) {
            Profile profile = findOrCreateProfile(target.getId());
            return toPublicResponse(profile, target);
        }

        Profile profile = profileRepository.findByUserId(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found."));

        Visibility visibility = profile.getVisibility();
        if (visibility == null) {
            visibility = Visibility.VERIFIED_ONLY;
        }
        if (visibility == Visibility.PRIVATE) {
            throw new ResourceNotFoundException("Profile not found.");
        }
        if (visibility == Visibility.VERIFIED_ONLY) {
            AuthorizationGates.requireOnboardingComplete(requester);
        }
        if (visibility == Visibility.PUBLIC) {
            AuthorizationGates.requireOnboardingComplete(requester);
        }

        return toPublicResponse(profile, target);
    }

    public Profile findOrCreateProfile(ObjectId userId) {
        return profileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Instant now = clock.instant();
                    Profile profile = new Profile();
                    profile.setUserId(userId);
                    profile.setVisibility(Visibility.VERIFIED_ONLY);
                    profile.setPreferredContact(PreferredContact.NONE);
                    profile.setSkills(new ArrayList<>());
                    profile.setInterests(null);
                    profile.setTechnologies(new ArrayList<>());
                    profile.setMentorshipAvailable(Boolean.FALSE);
                    profile.setCreatedAt(now);
                    profile.setUpdatedAt(now);
                    return profileRepository.save(profile);
                });
    }
    private void applyPatch(Profile profile, ProfilePatchRequest request) {
        if (request.getBio() != null) {
            profile.setBio(request.getBio());
        }

        if (request.getSkills() != null) {
            profile.setSkills(normalizeTags(request.getSkills()));
        }

        if (request.getMentorshipAvailable() != null) {
            profile.setMentorshipAvailable(request.getMentorshipAvailable());
        }

        if (request.getJobTitle() != null) {
            String t = request.getJobTitle().trim();
            if (t.isBlank()) throw new InvalidRequestException("jobTitle must not be blank");
            if (t.length() > 100) throw new InvalidRequestException("jobTitle too long");
            profile.setJobTitle(t);
        }

        if (request.getCompany() != null) {
            String t = request.getCompany().trim();
            if (t.isBlank()) throw new InvalidRequestException("company must not be blank");
            if (t.length() > 100) throw new InvalidRequestException("company too long");
            profile.setCompany(t);
        }

        if (request.getWorkArea() != null) {
            String t = request.getWorkArea().trim();
            if (t.isBlank()) throw new InvalidRequestException("workArea must not be blank");
            if (t.length() > 100) throw new InvalidRequestException("workArea too long");
            profile.setWorkArea(t);
        }

        if (request.getTechnologies() != null) {
            profile.setTechnologies(normalizeTechnologies(request.getTechnologies()));
        }

        if (request.getInterests() != null) {
            profile.setInterests(request.getInterests());
        }

        if (request.getLinkedinUrl() != null) {
            profile.setLinkedinUrl(sanitizeLinkedinUrl(request.getLinkedinUrl()));
        }

        if (request.getGithubUrl() != null) {
            profile.setGithubUrl(sanitizeGithubUrl(request.getGithubUrl()));
        }

        if (request.getWebsiteUrl() != null) {
            profile.setWebsiteUrl(sanitizeUrl(request.getWebsiteUrl()));
        }

        if (request.getLocation() != null) {
            profile.setLocation(request.getLocation());
        }

        if (request.getVisibility() != null) {
            profile.setVisibility(Visibility.valueOf(request.getVisibility().toUpperCase()));
        }

        if (request.getPreferredContact() != null) {
            profile.setPreferredContact(PreferredContact.valueOf(request.getPreferredContact().toUpperCase()));
        }

        if (request.getAvatarGridFsId() != null) {
            profile.setAvatarGridFsId(request.getAvatarGridFsId());
        }
    }
    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) return Collections.emptyList();
        Set<String> result = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag == null) continue;
            String trimmed = tag.trim();
            if (trimmed.isEmpty() || trimmed.length() > MAX_TAG_LENGTH) {
                throw new InvalidRequestException("Invalid tag length");
            }
            result.add(trimmed);
        }
        if (result.size() > MAX_SKILLS) {
            throw new InvalidRequestException("Too many tags");
        }
        return new ArrayList<>(result);
    }

    private List<String> normalizeTechnologies(List<String> in) {
        if (in == null) return new ArrayList<>();
        Set<String> set = new LinkedHashSet<>();
        for (String s : in) {
            if (s == null) continue;
            String t = s.trim();
            if (t.isBlank() || t.length() > MAX_TAG_LENGTH) {
                throw new InvalidRequestException("Invalid technology");
            }
            set.add(t.toLowerCase());
        }
        if (set.size() > 20) {
            throw new InvalidRequestException("Too many technologies");
        }
        return new ArrayList<>(set);
    }

    private static final java.util.regex.Pattern LINKEDIN_PATTERN =
            java.util.regex.Pattern.compile("^https?://(www\\.)?linkedin\\.com/in/[A-Za-z0-9_\\-%.]+/?$", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern GITHUB_PATTERN =
            java.util.regex.Pattern.compile("^https?://(www\\.)?github\\.com/[A-Za-z0-9_\\-%.]+/?$", java.util.regex.Pattern.CASE_INSENSITIVE);

    private String sanitizeLinkedinUrl(String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        if (trimmed.isEmpty()) return null;
        if (!LINKEDIN_PATTERN.matcher(trimmed).matches()) {
            throw new InvalidRequestException("Please enter a valid LinkedIn profile URL.");
        }
        return trimmed;
    }

    private String sanitizeGithubUrl(String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        if (trimmed.isEmpty()) return null;
        if (!GITHUB_PATTERN.matcher(trimmed).matches()) {
            throw new InvalidRequestException("Please enter a valid GitHub profile URL.");
        }
        return trimmed;
    }

    private String sanitizeUrl(String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        if (trimmed.isEmpty()) return null;
        String lower = trimmed.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new InvalidRequestException("Invalid URL");
        }
        return trimmed;
    }

    private ObjectId parseUserId(String hex) {
        try {
            return new ObjectId(hex);
        } catch (Exception e) {
            throw new ResourceNotFoundException("Profile not found.");
        }
    }
    private ProfileResponse toResponse(Profile profile, User user) {
        ProgramInfo info = programInfo(user.getId());
        ProfileResponse r = new ProfileResponse();
        r.setId(profile.getId() != null ? profile.getId().toHexString() : null);
        r.setUserId(profile.getUserId() != null ? profile.getUserId().toHexString() : null);
        r.setName(user.getName());
        r.setEmail(user.getEmail());
        r.setRole(user.getRole());
        r.setVerificationStatus(user.getVerificationStatus());
        r.setEnabled(user.isEnabled());
        r.setOnboardingComplete(AuthorizationGates.hasCompletedOnboarding(user));
        r.setProgram(info.program());
        r.setCurrentSemester(info.currentSemester());
        r.setPassoutYear(info.passoutYear());
        r.setBio(profile.getBio());
        r.setSkills(profile.getSkills());
        r.setMentorshipAvailable(profile.getMentorshipAvailable() != null ? profile.getMentorshipAvailable() : Boolean.FALSE);
        r.setJobTitle(profile.getJobTitle());
        r.setCompany(profile.getCompany());
        r.setWorkArea(profile.getWorkArea());
        r.setTechnologies(profile.getTechnologies());
        r.setInterests(profile.getInterests());
        r.setLinkedinUrl(profile.getLinkedinUrl());
        r.setGithubUrl(profile.getGithubUrl());
        r.setWebsiteUrl(profile.getWebsiteUrl());
        r.setLocation(profile.getLocation());
        r.setVisibility(profile.getVisibility());
        r.setPreferredContact(profile.getPreferredContact());
        r.setAvatarGridFsId(profile.getAvatarGridFsId());
        r.setCreatedAt(profile.getCreatedAt());
        r.setUpdatedAt(profile.getUpdatedAt());
        return r;
    }

    private ProfilePublicResponse toPublicResponse(Profile profile, User user) {
        ProfilePublicResponse r = new ProfilePublicResponse();
        r.setId(profile.getId() != null ? profile.getId().toHexString() : null);
        r.setUserId(profile.getUserId() != null ? profile.getUserId().toHexString() : null);
        r.setName(user.getName());
        r.setRole(user.getRole() != null ? user.getRole().name() : null);
        r.setBio(profile.getBio());
        r.setSkills(profile.getSkills());
        r.setMentorshipAvailable(profile.getMentorshipAvailable() != null ? profile.getMentorshipAvailable() : Boolean.FALSE);
        r.setJobTitle(profile.getJobTitle());
        r.setCompany(profile.getCompany());
        r.setWorkArea(profile.getWorkArea());
        r.setTechnologies(profile.getTechnologies());
        r.setInterests(profile.getInterests());
        r.setLinkedinUrl(profile.getLinkedinUrl());
        r.setGithubUrl(profile.getGithubUrl());
        r.setWebsiteUrl(profile.getWebsiteUrl());
        r.setLocation(profile.getLocation());
        r.setVisibility(profile.getVisibility());
        r.setPreferredContact(profile.getPreferredContact());
        r.setAvatarGridFsId(profile.getAvatarGridFsId());
        return r;
    }

    public ProfileContext getProfileContext(User user) {
        Profile profile = findOrCreateProfile(user.getId());
        ProgramInfo info = programInfo(user.getId());
        return new ProfileContext(
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getVerificationStatus(),
                user.isEnabled(),
                profile.getBio(),
                profile.getSkills(),
                profile.getMentorshipAvailable() != null ? profile.getMentorshipAvailable() : Boolean.FALSE,
                profile.getJobTitle(),
                profile.getCompany(),
                profile.getWorkArea(),
                profile.getTechnologies(),
                profile.getInterests(),
                profile.getLinkedinUrl(),
                profile.getGithubUrl(),
                profile.getWebsiteUrl(),
                profile.getLocation(),
                profile.getVisibility(),
                profile.getPreferredContact(),
                profile.getAvatarGridFsId(),
                info.program(),
                info.currentSemester(),
                info.passoutYear());
    }

    private record ProgramInfo(String program, Integer currentSemester, Integer passoutYear) {
    }

    private ProgramInfo programInfo(ObjectId userId) {
        VerificationApplication app = verificationApplicationRepository
                .findFirstByUserIdOrderBySubmittedAtDesc(userId)
                .orElse(null);
        if (app == null) {
            return new ProgramInfo(null, null, null);
        }
        String program = app.getType() == ApplicationType.STUDENT && app.getProgram() != null
                ? app.getProgram().name()
                : null;
        Integer currentSemester = app.getCurrentSemester();
        Integer passoutYear = app.getType() == ApplicationType.ALUMNI ? app.getPassoutYear() : null;
        return new ProgramInfo(program, currentSemester, passoutYear);
    }
}
