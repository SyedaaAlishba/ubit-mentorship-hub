package com.ubit.mentorshiphub.profile.service;

import com.ubit.mentorshiphub.common.exception.InvalidRequestException;
import com.ubit.mentorshiphub.common.exception.ResourceNotFoundException;
import com.ubit.mentorshiphub.common.security.AccountAccess;
import com.ubit.mentorshiphub.profile.dto.ProfilePatchRequest;
import com.ubit.mentorshiphub.profile.dto.ProfilePublicResponse;
import com.ubit.mentorshiphub.profile.dto.ProfileResponse;
import com.ubit.mentorshiphub.profile.model.Profile;
import com.ubit.mentorshiphub.profile.model.Visibility;
import com.ubit.mentorshiphub.profile.repository.ProfileRepository;
import com.ubit.mentorshiphub.user.model.AppRole;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.user.model.VerificationStatus;
import com.ubit.mentorshiphub.user.service.UserService;
import com.ubit.mentorshiphub.verification.repository.VerificationApplicationRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServicePhase1Test {

    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private UserService userService;
    @Mock
    private VerificationApplicationRepository verificationApplicationRepository;
    @Mock
    private AccountAccess accountAccess;

    private Clock clock;
    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneId.of("UTC"));
        profileService = new ProfileService(profileRepository, userService,
                verificationApplicationRepository, accountAccess, clock);
    }

    private User verifiedUser() {
        User u = new User();
        u.setId(new ObjectId("507f191e810c19729de860aa"));
        u.setName("Ali");
        u.setEmail("ali@example.com");
        u.setRole(AppRole.STUDENT);
        u.setVerificationStatus(VerificationStatus.VERIFIED);
        u.setEnabled(true);
        return u;
    }

    private Profile existingProfile(User u) {
        Profile p = new Profile();
        p.setUserId(u.getId());
        return p;
    }

    @Test
    void newProfileDefaultsMentorshipFalse() {
        User u = verifiedUser();
        when(profileRepository.findByUserId(u.getId())).thenReturn(Optional.empty());
        when(profileRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        Profile p = profileService.findOrCreateProfile(u.getId());
        assertThat(p.getMentorshipAvailable()).isFalse();
        assertThat(p.getTechnologies()).isEmpty();
    }

    @Test
    void missingMentorshipAvailableBehavesAsFalse() {
        User u = verifiedUser();
        Profile p = existingProfile(u);
        when(accountAccess.requireCurrentUser()).thenReturn(u);
        when(profileRepository.findByUserId(u.getId())).thenReturn(Optional.of(p));
        ProfileResponse r = profileService.getCurrentProfile();
        assertThat(r.getMentorshipAvailable()).isFalse();
    }

    @Test
    void patchSetsMentorshipTrueFalse() {
        User u = verifiedUser();
        Profile p = existingProfile(u);
        when(accountAccess.requireCurrentUser()).thenReturn(u);
        when(profileRepository.findByUserId(u.getId())).thenReturn(Optional.of(p));
        when(profileRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        ProfilePatchRequest req = new ProfilePatchRequest();
        req.setMentorshipAvailable(true);
        ProfileResponse r = profileService.updateCurrentProfile(req);
        assertThat(r.getMentorshipAvailable()).isTrue();
        req.setMentorshipAvailable(false);
        r = profileService.updateCurrentProfile(req);
        assertThat(r.getMentorshipAvailable()).isFalse();
    }

    @Test
    void jobTitleCompanyWorkAreaTrimmed() {
        User u = verifiedUser();
        Profile p = existingProfile(u);
        when(accountAccess.requireCurrentUser()).thenReturn(u);
        when(profileRepository.findByUserId(u.getId())).thenReturn(Optional.of(p));
        when(profileRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        ProfilePatchRequest req = new ProfilePatchRequest();
        req.setJobTitle("  Backend Developer  ");
        req.setCompany("  ACME Corp  ");
        req.setWorkArea("  Software Engineering  ");
        ProfileResponse r = profileService.updateCurrentProfile(req);
        assertThat(r.getJobTitle()).isEqualTo("Backend Developer");
        assertThat(r.getCompany()).isEqualTo("ACME Corp");
        assertThat(r.getWorkArea()).isEqualTo("Software Engineering");
    }

    @Test
    void jobTitleCompanyWorkAreaRejectBlankAndTooLong() {
        User u = verifiedUser();
        Profile p = existingProfile(u);
        when(accountAccess.requireCurrentUser()).thenReturn(u);
        when(profileRepository.findByUserId(u.getId())).thenReturn(Optional.of(p));
        ProfilePatchRequest req = new ProfilePatchRequest();

        req.setJobTitle("");
        assertThatThrownBy(() -> profileService.updateCurrentProfile(req))
                .isInstanceOf(InvalidRequestException.class);
        req.setJobTitle("   ");
        assertThatThrownBy(() -> profileService.updateCurrentProfile(req))
                .isInstanceOf(InvalidRequestException.class);
        req.setJobTitle("a".repeat(101));
        assertThatThrownBy(() -> profileService.updateCurrentProfile(req))
                .isInstanceOf(InvalidRequestException.class);

        req.setJobTitle(null);
        req.setCompany("");
        assertThatThrownBy(() -> profileService.updateCurrentProfile(req))
                .isInstanceOf(InvalidRequestException.class);
        req.setCompany("b".repeat(101));
        assertThatThrownBy(() -> profileService.updateCurrentProfile(req))
                .isInstanceOf(InvalidRequestException.class);

        req.setCompany(null);
        req.setWorkArea("   ");
        assertThatThrownBy(() -> profileService.updateCurrentProfile(req))
                .isInstanceOf(InvalidRequestException.class);
        req.setWorkArea("c".repeat(101));
        assertThatThrownBy(() -> profileService.updateCurrentProfile(req))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void technologiesNormalizedAndLimited() {
        User u = verifiedUser();
        Profile p = existingProfile(u);
        when(accountAccess.requireCurrentUser()).thenReturn(u);
        when(profileRepository.findByUserId(u.getId())).thenReturn(Optional.of(p));
        when(profileRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        ProfilePatchRequest req = new ProfilePatchRequest();
        req.setTechnologies(List.of("Java", "java", " SPRING "));
        ProfileResponse r = profileService.updateCurrentProfile(req);
        assertThat(r.getTechnologies()).containsExactly("java", "spring");
        req.setTechnologies(List.of("a".repeat(51)));
        assertThatThrownBy(() -> profileService.updateCurrentProfile(req))
                .isInstanceOf(InvalidRequestException.class);
        req.setTechnologies(IntStream.range(0, 21).mapToObj(i -> "t" + i).toList());
        assertThatThrownBy(() -> profileService.updateCurrentProfile(req))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void publicResponseExposesMentorshipFieldsButNotSensitiveIdentity() {
        User viewer = new User();
        viewer.setId(new ObjectId("507f191e810c19729de860bb"));
        viewer.setName("Viewer");
        viewer.setRole(AppRole.ALUMNI);
        viewer.setVerificationStatus(VerificationStatus.VERIFIED);
        viewer.setEnabled(true);

        User target = verifiedUser();
        Profile p = existingProfile(target);
        p.setVisibility(Visibility.PUBLIC);
        p.setMentorshipAvailable(true);
        p.setJobTitle("Dev");
        p.setCompany("ACME");
        p.setWorkArea("Backend");
        p.setTechnologies(List.of("java"));

        when(accountAccess.requireCurrentUser()).thenReturn(viewer);
        when(userService.findById(target.getId())).thenReturn(Optional.of(target));
        when(profileRepository.findByUserId(target.getId())).thenReturn(Optional.of(p));

        ProfilePublicResponse r = profileService.getProfileForUser(target.getId().toHexString());
        assertThat(r.getUserId()).isEqualTo(target.getId().toHexString());
        assertThat(r.getMentorshipAvailable()).isTrue();
        assertThat(r.getJobTitle()).isEqualTo("Dev");
        assertThat(r.getCompany()).isEqualTo("ACME");
        assertThat(r.getWorkArea()).isEqualTo("Backend");
        assertThat(r.getTechnologies()).containsExactly("java");
    }

    @Test
    void privateProfileHiddenFromOthers() {
        User viewer = new User();
        viewer.setId(new ObjectId("507f191e810c19729de860bb"));
        viewer.setName("Viewer");
        viewer.setRole(AppRole.ALUMNI);
        viewer.setVerificationStatus(VerificationStatus.VERIFIED);
        viewer.setEnabled(true);

        User target = verifiedUser();
        Profile p = existingProfile(target);
        p.setVisibility(Visibility.PRIVATE);

        when(accountAccess.requireCurrentUser()).thenReturn(viewer);
        when(userService.findById(target.getId())).thenReturn(Optional.of(target));
        when(profileRepository.findByUserId(target.getId())).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> profileService.getProfileForUser(target.getId().toHexString()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void profileUrlValidationAcceptsValidAndRejectsInvalid() {
        User u = verifiedUser();
        Profile p = existingProfile(u);
        when(accountAccess.requireCurrentUser()).thenReturn(u);
        when(profileRepository.findByUserId(u.getId())).thenReturn(Optional.of(p));
        when(profileRepository.save(any(Profile.class))).thenAnswer(i -> i.getArgument(0));

        // Valid URLs
        ProfilePatchRequest validReq = new ProfilePatchRequest();
        validReq.setLinkedinUrl("https://www.linkedin.com/in/username");
        validReq.setGithubUrl("https://github.com/username");
        ProfileResponse validResp = profileService.updateCurrentProfile(validReq);
        assertThat(validResp.getLinkedinUrl()).isEqualTo("https://www.linkedin.com/in/username");
        assertThat(validResp.getGithubUrl()).isEqualTo("https://github.com/username");

        // Variations with http and trailing slash
        ProfilePatchRequest varReq = new ProfilePatchRequest();
        varReq.setLinkedinUrl("http://linkedin.com/in/user.name_123/");
        varReq.setGithubUrl("http://www.github.com/user-name/");
        ProfileResponse varResp = profileService.updateCurrentProfile(varReq);
        assertThat(varResp.getLinkedinUrl()).isEqualTo("http://linkedin.com/in/user.name_123/");
        assertThat(varResp.getGithubUrl()).isEqualTo("http://www.github.com/user-name/");

        // Empty / blank URLs
        ProfilePatchRequest emptyReq = new ProfilePatchRequest();
        emptyReq.setLinkedinUrl("");
        emptyReq.setGithubUrl("   ");
        ProfileResponse emptyResp = profileService.updateCurrentProfile(emptyReq);
        assertThat(emptyResp.getLinkedinUrl()).isNull();
        assertThat(emptyResp.getGithubUrl()).isNull();

        // Reject GitHub URL in LinkedIn field
        ProfilePatchRequest ghInLi = new ProfilePatchRequest();
        ghInLi.setLinkedinUrl("https://github.com/username");
        assertThatThrownBy(() -> profileService.updateCurrentProfile(ghInLi))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Please enter a valid LinkedIn profile URL.");

        // Reject LinkedIn URL in GitHub field
        ProfilePatchRequest liInGh = new ProfilePatchRequest();
        liInGh.setGithubUrl("https://www.linkedin.com/in/username");
        assertThatThrownBy(() -> profileService.updateCurrentProfile(liInGh))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Please enter a valid GitHub profile URL.");

        // Reject arbitrary website in LinkedIn field
        ProfilePatchRequest siteInLi = new ProfilePatchRequest();
        siteInLi.setLinkedinUrl("https://example.com");
        assertThatThrownBy(() -> profileService.updateCurrentProfile(siteInLi))
                .isInstanceOf(InvalidRequestException.class);

        // Reject arbitrary website in GitHub field
        ProfilePatchRequest siteInGh = new ProfilePatchRequest();
        siteInGh.setGithubUrl("https://facebook.com/user");
        assertThatThrownBy(() -> profileService.updateCurrentProfile(siteInGh))
                .isInstanceOf(InvalidRequestException.class);

        // Reject malformed URL
        ProfilePatchRequest malformed = new ProfilePatchRequest();
        malformed.setLinkedinUrl("not-a-url");
        assertThatThrownBy(() -> profileService.updateCurrentProfile(malformed))
                .isInstanceOf(InvalidRequestException.class);
    }
}