package com.ubit.mentorshiphub.profile.service;

import com.ubit.mentorshiphub.common.exception.ResourceNotFoundException;
import com.ubit.mentorshiphub.common.exception.VerificationRequiredException;
import com.ubit.mentorshiphub.common.security.AccountAccess;
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
import com.ubit.mentorshiphub.verification.model.ApplicationStatus;
import com.ubit.mentorshiphub.verification.model.ApplicationType;
import com.ubit.mentorshiphub.verification.model.Program;
import com.ubit.mentorshiphub.verification.model.VerificationApplication;
import com.ubit.mentorshiphub.verification.repository.VerificationApplicationRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

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

    private User user(ObjectId id, AppRole role, VerificationStatus status) {
        User u = new User();
        u.setId(id);
        u.setGoogleId("g" + id.toHexString());
        u.setEmail("u" + id.toHexString().substring(0,6) + "@example.com");
        u.setName("User" + id.toHexString().substring(0,4));
        u.setRole(role);
        u.setVerificationStatus(status);
        u.setEnabled(true);
        return u;
    }

    @Test
    @DisplayName("lazy creates profile on first GET /me")
    void lazyCreatesProfile() {
        ObjectId uid = new ObjectId("507f191e810c19729de860aa");
        User u = user(uid, null, VerificationStatus.UNVERIFIED);
        when(accountAccess.requireCurrentUser()).thenReturn(u);
        when(profileRepository.findByUserId(uid)).thenReturn(Optional.empty());
        ArgumentCaptor<Profile> captor = ArgumentCaptor.forClass(Profile.class);
        when(profileRepository.save(any(Profile.class))).thenAnswer(i -> i.getArgument(0));

        ProfileResponse resp = profileService.getCurrentProfile();

        verify(profileRepository).save(captor.capture());
        Profile saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(uid);
        assertThat(saved.getVisibility()).isEqualTo(Visibility.VERIFIED_ONLY);
        assertThat(saved.getPreferredContact()).isEqualTo(PreferredContact.NONE);
        assertThat(resp.userId()).isEqualTo(uid.toHexString());
        assertThat(resp.name()).isEqualTo(u.getName());
        assertThat(resp.email()).isEqualTo(u.getEmail());
    }

    @Test
    @DisplayName("second /me does not create duplicate")
    void noDuplicateOnSecondGet() {
        ObjectId uid = new ObjectId("507f191e810c19729de860ab");
        User u = user(uid, AppRole.STUDENT, VerificationStatus.VERIFIED);
        Profile p = new Profile();
        p.setUserId(uid);
        p.setVisibility(Visibility.VERIFIED_ONLY);
        when(accountAccess.requireCurrentUser()).thenReturn(u);
        when(profileRepository.findByUserId(uid)).thenReturn(Optional.of(p));
        when(verificationApplicationRepository.findFirstByUserIdOrderBySubmittedAtDesc(uid)).thenReturn(Optional.empty());

        profileService.getCurrentProfile();
        verify(profileRepository, times(1)).findByUserId(uid);
        verify(profileRepository, never()).save(any());
    }
}
