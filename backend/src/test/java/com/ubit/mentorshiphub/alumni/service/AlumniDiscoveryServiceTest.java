package com.ubit.mentorshiphub.alumni.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

import com.ubit.mentorshiphub.alumni.dto.AlumniDiscoveryQuery;
import com.ubit.mentorshiphub.alumni.dto.AlumniProfileResponse;
import com.ubit.mentorshiphub.alumni.repository.AlumniDiscoveryRepository;
import com.ubit.mentorshiphub.alumni.repository.AlumniDiscoveryRepository.AlumniDiscoveryResult;
import com.ubit.mentorshiphub.common.exception.AccountDisabledException;
import com.ubit.mentorshiphub.common.exception.AccountNotFoundException;
import com.ubit.mentorshiphub.common.exception.InvalidRequestException;
import com.ubit.mentorshiphub.common.exception.VerificationRequiredException;
import com.ubit.mentorshiphub.common.security.AccountAccess;
import com.ubit.mentorshiphub.user.model.AppRole;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.user.model.VerificationStatus;

@ExtendWith(MockitoExtension.class)
class AlumniDiscoveryServiceTest {

    @Mock
    private AlumniDiscoveryRepository repository;
    @Mock
    private AccountAccess accountAccess;

    private AlumniDiscoveryService service;

    @BeforeEach
    void setUp() {
        service = new AlumniDiscoveryService(repository, accountAccess);
    }

    private User user(AppRole role, VerificationStatus status) {
        User u = new User();
        u.setId(new ObjectId("507f191e810c19729de860a1"));
        u.setName("Ali Raza");
        u.setRole(role);
        u.setVerificationStatus(status);
        u.setEnabled(true);
        return u;
    }

    private Page<AlumniProfileResponse> discover() {
        return service.discover(0, 20, null, null, null, null, null, null, null, null, null, null);
    }

    private void emptyDirectory() {
        when(repository.search(any())).thenReturn(new AlumniDiscoveryResult(List.of(), 0L));
    }

    @Test
    @DisplayName("a request with no signed-in account surfaces the account not-found failure")
    void anonymousRequestSurfacesAccountNotFound() {
        when(accountAccess.requireCurrentUser()).thenThrow(
                new AccountNotFoundException("No account is signed in for the current request."));
        assertThatThrownBy(this::discover).isInstanceOf(AccountNotFoundException.class);
        verify(repository, never()).search(any());
    }

    @Test
    @DisplayName("a disabled account is cut off before the directory is queried")
    void disabledRequesterIsCutOff() {
        when(accountAccess.requireCurrentUser()).thenThrow(
                new AccountDisabledException("This account has been disabled."));
        assertThatThrownBy(this::discover).isInstanceOf(AccountDisabledException.class);
        verify(repository, never()).search(any());
    }

    @Test
    @DisplayName("an onboarded pending student can browse the directory")
    void pendingStudentMayDiscover() {
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.STUDENT, VerificationStatus.PENDING));
        emptyDirectory();
        Page<AlumniProfileResponse> page = discover();
        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("a user who has not completed onboarding is refused")
    void nonOnboardedUserIsRefused() {
        User unOnboarded = new User();
        unOnboarded.setId(new ObjectId("507f191e810c19729de860a1"));
        unOnboarded.setEnabled(true);
        when(accountAccess.requireCurrentUser()).thenReturn(unOnboarded);
        assertThatThrownBy(this::discover).isInstanceOf(com.ubit.mentorshiphub.common.exception.OnboardingRequiredException.class);
        verify(repository, never()).search(any());
    }

    @Test
    @DisplayName("a verified student can browse the directory")
    void verifiedStudentMayDiscover() {
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.STUDENT, VerificationStatus.VERIFIED));
        emptyDirectory();
        Page<AlumniProfileResponse> page = discover();
        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("a verified senior can browse the directory")
    void verifiedSeniorMayDiscover() {
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.SENIOR, VerificationStatus.VERIFIED));
        emptyDirectory();
        assertThat(discover().getContent()).isEmpty();
    }

    @Test
    @DisplayName("a verified alumni can browse the directory")
    void verifiedAlumniMayDiscover() {
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.ALUMNI, VerificationStatus.VERIFIED));
        emptyDirectory();
        assertThat(discover().getContent()).isEmpty();
    }

    @Test
    @DisplayName("a negative page is rejected")
    void negativePageIsRejected() {
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.ALUMNI, VerificationStatus.VERIFIED));
        assertThatThrownBy(() -> service.discover(-1, 20, null, null, null, null, null,
                null, null, null, null, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("page");
        verify(repository, never()).search(any());
    }

    @Test
    @DisplayName("a size outside 1..50 is rejected")
    void sizeOutOfRangeIsRejected() {
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.ALUMNI, VerificationStatus.VERIFIED));
        assertThatThrownBy(() -> service.discover(0, 0, null, null, null, null, null,
                null, null, null, null, null)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.discover(0, 51, null, null, null, null, null,
                null, null, null, null, null)).isInstanceOf(InvalidRequestException.class);
        verify(repository, never()).search(any());
    }

    @Test
    @DisplayName("a role other than ALUMNI or SENIOR is rejected")
    void invalidRoleIsRejected() {
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.ALUMNI, VerificationStatus.VERIFIED));
        assertThatThrownBy(() -> service.discover(0, 20, "ADMIN", null, null, null, null,
                null, null, null, null, null)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.discover(0, 20, "STUDENT", null, null, null, null,
                null, null, null, null, null)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.discover(0, 20, "anyone", null, null, null, null,
                null, null, null, null, null)).isInstanceOf(InvalidRequestException.class);
        verify(repository, never()).search(any());
    }

    @Test
    @DisplayName("a program outside the supported enum is rejected")
    void invalidProgramIsRejected() {
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.ALUMNI, VerificationStatus.VERIFIED));
        assertThatThrownBy(() -> service.discover(0, 20, null, "PHARMACY", null, null, null,
                null, null, null, null, null)).isInstanceOf(InvalidRequestException.class);
        verify(repository, never()).search(any());
    }

    @Test
    @DisplayName("a non-numeric pass-out year is rejected")
    void nonNumericPassoutYearIsRejected() {
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.ALUMNI, VerificationStatus.VERIFIED));
        assertThatThrownBy(() -> service.discover(0, 20, null, null, "yesterday", null, null,
                null, null, null, null, null)).isInstanceOf(InvalidRequestException.class);
        verify(repository, never()).search(any());
    }

    @Test
    @DisplayName("an unparseable mentorship flag is rejected")
    void invalidMentorshipIsRejected() {
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.ALUMNI, VerificationStatus.VERIFIED));
        assertThatThrownBy(() -> service.discover(0, 20, null, null, null, null, null,
                null, null, null, null, "maybe")).isInstanceOf(InvalidRequestException.class);
        verify(repository, never()).search(any());
    }

    @Test
    @DisplayName("role, program and pass-out year are normalised to stop values")
    void filtersAreNormalisedToStrictValues() {
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.ALUMNI, VerificationStatus.VERIFIED));
        emptyDirectory();
        service.discover(1, 10, " senior ", "cs", "2024", List.of(" Java ", "spring, "),
                List.of(" MongoDB "), "  Acme  ", null, null, null, "  true  ");

        ArgumentCaptor<AlumniDiscoveryQuery> captor = ArgumentCaptor.forClass(AlumniDiscoveryQuery.class);
        verify(repository).search(captor.capture());
        AlumniDiscoveryQuery query = captor.getValue();
        assertThat(query.page()).isEqualTo(1);
        assertThat(query.size()).isEqualTo(10);
        assertThat(query.role()).isEqualTo("SENIOR");
        assertThat(query.program()).isEqualTo("CS");
        assertThat(query.passoutYear()).isEqualTo(2024);
        assertThat(query.skills()).containsExactly("java", "spring,");
        assertThat(query.technologies()).containsExactly("mongodb");
        assertThat(query.company()).isEqualTo("Acme");
        assertThat(query.name()).isNull();
        assertThat(query.mentorshipAvailable()).isTrue();
    }

    @Test
    @DisplayName("blank filters stay null and empty token lists stay empty")
    void blankFiltersBecomeNull() {
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.ALUMNI, VerificationStatus.VERIFIED));
        emptyDirectory();
        service.discover(0, 20, "  ", " ", null, List.of("", "  "), null, "  ", "", null, null, null);

        ArgumentCaptor<AlumniDiscoveryQuery> captor = ArgumentCaptor.forClass(AlumniDiscoveryQuery.class);
        verify(repository).search(captor.capture());
        AlumniDiscoveryQuery query = captor.getValue();
        assertThat(query.role()).isNull();
        assertThat(query.program()).isNull();
        assertThat(query.passoutYear()).isNull();
        assertThat(query.skills()).isEmpty();
        assertThat(query.technologies()).isEmpty();
        assertThat(query.company()).isNull();
        assertThat(query.workArea()).isNull();
        assertThat(query.jobTitle()).isNull();
        assertThat(query.name()).isNull();
        assertThat(query.mentorshipAvailable()).isNull();
    }

    @Test
    @DisplayName("the page metadata is calculated from the filtered total")
    void pageMetadataMatchesFilteredTotal() {
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.STUDENT, VerificationStatus.VERIFIED));
        List<Map<String, Object>> data = List.of(entry("a"), entry("b"), entry("c"));
        when(repository.search(any())).thenReturn(new AlumniDiscoveryResult(data, 23L));

        Page<AlumniProfileResponse> page = service.discover(1, 10, null, null, null, null, null,
                null, null, null, null, null);
        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getNumber()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(10);
        assertThat(page.getTotalElements()).isEqualTo(23);
        assertThat(page.getTotalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("the advertised sort is pass-out year descending, then name, then id")
    void advertisedSortMatchesTheAggregation() {
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.STUDENT, VerificationStatus.VERIFIED));
        emptyDirectory();
        Page<AlumniProfileResponse> page = discover();
        assertThat(page.getSort().getOrderFor("passoutYear").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(page.getSort().getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(page.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("the response exposes only intended fields and ignores account internals")
    void responseContainsOnlyDirectoryFields() {
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.STUDENT, VerificationStatus.VERIFIED));
        Map<String, Object> doc = entry("mapped");
        Map<String, Object> profile = nested(doc, "profile");
        profile.put("mentorshipAvailable", true);
        Map<String, Object> verification = nested(doc, "verification");
        verification.put("type", "ALUMNI");
        verification.put("program", "CS");
        verification.put("currentSemester", 6);
        verification.put("passoutYear", 2024);
        when(repository.search(any())).thenReturn(new AlumniDiscoveryResult(List.of(doc), 1L));

        AlumniProfileResponse response = discover().getContent().get(0);
        assertThat(response.userId()).isEqualTo("507f191e810c19729de860aa");
        assertThat(response.name()).isEqualTo("Ali Raza");
        assertThat(response.role()).isEqualTo("ALUMNI");
        assertThat(response.program()).isNull();
        assertThat(response.currentSemester()).isEqualTo(6);
        assertThat(response.passoutYear()).isEqualTo(2024);
        assertThat(response.bio()).isEqualTo("Backend engineer");
        assertThat(response.skills()).containsExactly("java", "spring");
        assertThat(response.interests()).isEqualTo("Machine learning");
        assertThat(response.technologies()).containsExactly("mongo", "docker");
        assertThat(response.jobTitle()).isEqualTo("Software Engineer");
        assertThat(response.company()).isEqualTo("Acme");
        assertThat(response.workArea()).isEqualTo("Backend");
        assertThat(response.githubUrl()).isEqualTo("https://github.com/ali");
        assertThat(response.linkedinUrl()).isEqualTo("https://linkedin.com/in/ali");
        assertThat(response.websiteUrl()).isEqualTo("https://ali.dev");
        assertThat(response.location()).isEqualTo("Karachi");
        assertThat(response.preferredContact()).isEqualTo("EMAIL");
        assertThat(response.mentorshipAvailable()).isTrue();
        assertThat(response.avatarGridFsId()).isEqualTo("gridfs-1");

        assertThat(response.toString())
                .doesNotContain("aliraza@gmail.com")
                .doesNotContain("google-123")
                .doesNotContain("VERIFIED");
    }

    @Test
    @DisplayName("academic fields follow the latest STUDENT application")
    void academicFieldsFollowLatestStudentApplication() {
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.STUDENT, VerificationStatus.VERIFIED));
        Map<String, Object> doc = entry("student");
        Map<String, Object> verification = nested(doc, "verification");
        verification.put("type", "STUDENT");
        verification.put("program", "CS");
        verification.put("currentSemester", 5);
        verification.put("passoutYear", 2024);
        when(repository.search(any())).thenReturn(new AlumniDiscoveryResult(List.of(doc), 1L));

        AlumniProfileResponse response = discover().getContent().get(0);
        assertThat(response.program()).isEqualTo("CS");
        assertThat(response.currentSemester()).isEqualTo(5);
        assertThat(response.passoutYear()).isNull();
    }

    @Test
    @DisplayName("academic fields are null when there is no verification application")
    void academicFieldsAreNullWithoutAnApplication() {
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.ALUMNI, VerificationStatus.VERIFIED));
        Map<String, Object> doc = entry("no-verification");
        doc.remove("verification");
        when(repository.search(any())).thenReturn(new AlumniDiscoveryResult(List.of(doc), 1L));

        AlumniProfileResponse response = discover().getContent().get(0);
        assertThat(response.program()).isNull();
        assertThat(response.currentSemester()).isNull();
        assertThat(response.passoutYear()).isNull();
        assertThat(response.skills()).containsExactly("java", "spring");
        assertThat(response.mentorshipAvailable()).isFalse();
    }

    private Map<String, Object> entry(String marker) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("_id", new ObjectId("507f191e810c19729de860aa"));
        doc.put("name", "Ali Raza");
        doc.put("role", "ALUMNI");
        doc.put("googleId", "google-123");
        doc.put("email", "aliraza@gmail.com");
        doc.put("verificationStatus", "VERIFIED");

        Map<String, Object> profile = new HashMap<>();
        profile.put("bio", "Backend engineer");
        profile.put("skills", List.of("java", "spring"));
        profile.put("interests", "Machine learning");
        profile.put("technologies", List.of("mongo", "docker"));
        profile.put("jobTitle", "Software Engineer");
        profile.put("company", "Acme");
        profile.put("workArea", "Backend");
        profile.put("githubUrl", "https://github.com/ali");
        profile.put("linkedinUrl", "https://linkedin.com/in/ali");
        profile.put("websiteUrl", "https://ali.dev");
        profile.put("location", "Karachi");
        profile.put("preferredContact", "EMAIL");
        profile.put("mentorshipAvailable", false);
        profile.put("avatarGridFsId", "gridfs-1");
        doc.put("profile", profile);

        Map<String, Object> verification = new HashMap<>();
        verification.put("type", "ALUMNI");
        verification.put("program", "CS");
        verification.put("currentSemester", 6);
        verification.put("passoutYear", 2024);
        doc.put("verification", verification);
        return doc;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nested(Map<String, Object> doc, String key) {
        return (Map<String, Object>) doc.get(key);
    }
}