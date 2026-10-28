package com.ubit.mentorshiphub.alumni.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ubit.mentorshiphub.alumni.dto.AlumniProfileResponse;
import com.ubit.mentorshiphub.alumni.service.AlumniDiscoveryService;
import com.ubit.mentorshiphub.common.security.AccountAccess;
import com.ubit.mentorshiphub.profile.model.Profile;
import com.ubit.mentorshiphub.profile.model.Visibility;
import com.ubit.mentorshiphub.user.model.AppRole;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.user.model.VerificationStatus;
import com.ubit.mentorshiphub.verification.model.ApplicationStatus;
import com.ubit.mentorshiphub.verification.model.ApplicationType;
import com.ubit.mentorshiphub.verification.model.Program;
import com.ubit.mentorshiphub.verification.model.VerificationApplication;

/**
 * Executes the real aggregation pipeline against a real MongoDB, seeding
 * documents through the production entities so the collection names, field
 * names and enum encodings are the ones the application actually writes.
 *
 * <p>The test connects to a dedicated throwaway database and drops its
 * collections before every test, so nothing leaks between tests or into
 * neighbouring suites. When no MongoDB is reachable the test is skipped rather
 * than failed, which keeps {@code mvn clean verify} green on a machine without
 * a server while still exercising the pipeline wherever one is available.
 */
@DataMongoTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.data.mongodb.uri="
        + "mongodb://localhost:27017/ubit_mentorship_alumni_it?serverSelectionTimeoutMS=1000&connectTimeoutMS=1000")
class AlumniDiscoveryIntegrationTest {

    private static final String USERS = "users";
    private static final String PROFILES = "profiles";
    private static final String APPLICATIONS = "verification_applications";
    private static final String SECRET_REVIEWER_NOTE = "internal-reviewer-note-9f3c";

    @Autowired
    private MongoTemplate mongoTemplate;

    private AlumniDiscoveryService service;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(mongoAvailable(),
                "MongoDB is not reachable; skipping the alumni aggregation integration test");
        AccountAccess accountAccess = mock(AccountAccess.class);
        when(accountAccess.requireCurrentUser()).thenReturn(verifiedRequester());
        service = new AlumniDiscoveryService(new AlumniDiscoveryRepository(mongoTemplate), accountAccess);
        clearCollections();
    }

    // ---------------------------------------------------------------- eligibility

    @Test
    @DisplayName("only enabled, verified, visible alumni and seniors are returned")
    void returnsOnlyEligibleVisibleUsers() {
        ObjectId publicAlumni = seedAlumni("Public Alumni", Visibility.PUBLIC, true);
        ObjectId visibleSenior = seedSenior("Verified Only Senior", Visibility.VERIFIED_ONLY, true);

        ObjectId student = seedUser("Verified Student", AppRole.STUDENT, VerificationStatus.VERIFIED, true);
        seedProfile(student, Visibility.PUBLIC, true, List.of(), List.of(), null, null, null);

        ObjectId admin = seedUser("Verified Admin", AppRole.ADMIN, VerificationStatus.VERIFIED, true);
        seedProfile(admin, Visibility.PUBLIC, true, List.of(), List.of(), null, null, null);

        ObjectId disabled = seedUser("Disabled Alumni", AppRole.ALUMNI, VerificationStatus.VERIFIED, false);
        seedProfile(disabled, Visibility.PUBLIC, true, List.of(), List.of(), null, null, null);

        ObjectId unverified = seedUser("Unverified Alumni", AppRole.ALUMNI, VerificationStatus.UNVERIFIED, true);
        seedProfile(unverified, Visibility.PUBLIC, true, List.of(), List.of(), null, null, null);

        ObjectId privateAlumni = seedUser("Private Alumni", AppRole.ALUMNI, VerificationStatus.VERIFIED, true);
        seedProfile(privateAlumni, Visibility.PRIVATE, true, List.of(), List.of(), null, null, null);

        seedUser("Profileless Senior", AppRole.SENIOR, VerificationStatus.VERIFIED, true);

        Page<AlumniProfileResponse> page = discoverAll();
        assertThat(page.getContent()).extracting(AlumniProfileResponse::userId)
                .containsExactlyInAnyOrder(publicAlumni.toHexString(), visibleSenior.toHexString());
        assertThat(page.getTotalElements()).isEqualTo(2);

        assertThat(page.getContent()).extracting(AlumniProfileResponse::userId)
                .doesNotContain(student.toHexString(), admin.toHexString(), disabled.toHexString(),
                        unverified.toHexString(), privateAlumni.toHexString());
    }

    // ------------------------------------------------- verification source of truth

    @Test
    @DisplayName("academic fields come from the latest relevant verification application")
    void mapsAcademicFieldsFromTheLatestRelevantApplication() {
        Instant older = Instant.parse("2020-01-01T00:00:00Z");
        Instant newer = Instant.parse("2021-01-01T00:00:00Z");

        ObjectId latestStudent = seedAlumni("Latest Student", Visibility.PUBLIC, true);
        seedApplication(latestStudent, ApplicationType.ALUMNI, null, null, 2018, 1, older);
        seedApplication(latestStudent, ApplicationType.STUDENT, Program.CS, 5, null, 2, newer);

        ObjectId latestAlumni = seedAlumni("Latest Alumni", Visibility.PUBLIC, true);
        seedApplication(latestAlumni, ApplicationType.STUDENT, Program.SE, 7, null, 1, older);
        seedApplication(latestAlumni, ApplicationType.ALUMNI, Program.SE, null, 2024, 2, newer);

        Map<String, AlumniProfileResponse> byId = discoverAll().getContent().stream()
                .collect(Collectors.toMap(AlumniProfileResponse::userId, Function.identity()));

        AlumniProfileResponse student = byId.get(latestStudent.toHexString());
        assertThat(student.program()).isEqualTo("CS");
        assertThat(student.currentSemester()).isEqualTo(5);
        assertThat(student.passoutYear()).isNull();

        AlumniProfileResponse alumni = byId.get(latestAlumni.toHexString());
        assertThat(alumni.program()).isNull();
        assertThat(alumni.currentSemester()).isNull();
        assertThat(alumni.passoutYear()).isEqualTo(2024);
    }

    @Test
    @DisplayName("a member with no verification application still appears with null academic fields")
    void userWithoutApplicationHasNullAcademicFields() {
        seedSenior("No Application Senior", Visibility.PUBLIC, true);

        AlumniProfileResponse response = discoverAll().getContent().get(0);
        assertThat(response.program()).isNull();
        assertThat(response.currentSemester()).isNull();
        assertThat(response.passoutYear()).isNull();
        assertThat(response.mentorshipAvailable()).isTrue();
    }

    @Test
    @DisplayName("a newer unverified application never overrides the verified claim")
    void newerUnverifiedApplicationIsIgnored() {
        Instant verifiedAt = Instant.parse("2020-01-01T00:00:00Z");
        Instant pendingAt = Instant.parse("2021-01-01T00:00:00Z");

        ObjectId alumni = seedAlumni("Verified Alumni", Visibility.PUBLIC, true);
        seedApplication(alumni, ApplicationType.ALUMNI, null, null, 2020, 1, verifiedAt,
                ApplicationStatus.VERIFIED);
        // A resubmission sits above the approved one by time; its pass-out year
        // is a claim, not a fact, and must not be surfaced.
        seedApplication(alumni, ApplicationType.ALUMNI, null, null, 2025, 2, pendingAt,
                ApplicationStatus.PENDING);

        AlumniProfileResponse response = discoverAll().getContent().stream()
                .filter(row -> row.userId().equals(alumni.toHexString()))
                .findFirst()
                .orElseThrow();
        assertThat(response.passoutYear()).isEqualTo(2020);
    }

    @Test
    @DisplayName("the serialized page never exposes account-private or moderation fields")
    void serializedResponseNeverExposesPrivateFields() throws Exception {
        ObjectId alumni = seedAlumni("Private Fields Alumni", Visibility.PUBLIC, true);
        seedApplication(alumni, ApplicationType.ALUMNI, null, null, 2024, 1, Instant.now());
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("userId").is(alumni)),
                new Update().set("reviewerNote", SECRET_REVIEWER_NOTE),
                VerificationApplication.class);

        String json = new ObjectMapper().writeValueAsString(discoverAll().getContent());

        assertThat(json)
                .doesNotContain("private.fields.alumni@ubit.edu.pk")
                .doesNotContain("google-" + alumni.toHexString())
                .doesNotContain(SECRET_REVIEWER_NOTE)
                .doesNotContain("googleId")
                .doesNotContain("verificationStatus")
                .doesNotContain("reviewerNote");
    }

    // ------------------------------------------------- filtering before pagination

    @Test
    @DisplayName("filtering happens before skip/limit and the totals reflect the filtered set")
    void filtersBeforePaginationAndReportsTotals() {
        for (int i = 0; i < 5; i++) {
            ObjectId id = seedUser("Java Alumni " + i, AppRole.ALUMNI, VerificationStatus.VERIFIED, true);
            seedProfile(id, Visibility.PUBLIC, true, List.of("java", "spring"), List.of("mongo"),
                    "Acme", "Backend", "Engineer");
            seedApplication(id, ApplicationType.ALUMNI, null, null, 2024, 1, Instant.now());
        }
        for (int i = 0; i < 5; i++) {
            ObjectId id = seedUser("Cpp Alumni " + i, AppRole.ALUMNI, VerificationStatus.VERIFIED, true);
            seedProfile(id, Visibility.PUBLIC, true, List.of("c++"), List.of("qt"),
                    "Globex", "Systems", "Developer");
            seedApplication(id, ApplicationType.ALUMNI, null, null, 2023, 1, Instant.now());
        }

        Page<AlumniProfileResponse> firstPage = discover(0, 2, List.of("java"));
        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
        assertThat(firstPage.getContent()).hasSize(2);

        Page<AlumniProfileResponse> lastPage = discover(2, 2, List.of("java"));
        assertThat(lastPage.getTotalElements()).isEqualTo(5);
        assertThat(lastPage.getContent()).hasSize(1);
    }

    // -------------------------------------------------------------------- sorting

    @Test
    @DisplayName("results sort by pass-out year descending, name ascending, then id, with nulls last")
    void sortsByPassoutYearThenNameThenId() {
        ObjectId zed = seedAlumniWithPassout("Zed", 2024);
        ObjectId abe = seedAlumniWithPassout("Abe", 2024);
        ObjectId mike = seedAlumniWithPassout("Mike", 2023);

        ObjectId samLow = new ObjectId("000000000000000000000010");
        ObjectId samHigh = new ObjectId("000000000000000000000020");
        seedUserWithId(samLow, "Sam", AppRole.ALUMNI, VerificationStatus.VERIFIED, true);
        seedProfile(samLow, Visibility.PUBLIC, true, List.of(), List.of(), null, null, null);
        seedApplication(samLow, ApplicationType.ALUMNI, null, null, 2022, 1, Instant.now());
        seedUserWithId(samHigh, "Sam", AppRole.ALUMNI, VerificationStatus.VERIFIED, true);
        seedProfile(samHigh, Visibility.PUBLIC, true, List.of(), List.of(), null, null, null);
        seedApplication(samHigh, ApplicationType.ALUMNI, null, null, 2022, 1, Instant.now());

        ObjectId noPassout = seedSenior("No Passout", Visibility.PUBLIC, true);

        assertThat(discoverAll().getContent()).extracting(AlumniProfileResponse::userId)
                .containsExactly(abe.toHexString(), zed.toHexString(), mike.toHexString(),
                        samLow.toHexString(), samHigh.toHexString(), noPassout.toHexString());
    }

    // -------------------------------------------------------------------- filters

    @Test
    @DisplayName("the role filter narrows the directory to that role")
    void filtersByRole() {
        seedAlumni("An Alumnus", Visibility.PUBLIC, true);
        seedSenior("A Senior", Visibility.PUBLIC, true);

        assertThat(discover(0, 20, "SENIOR", null, null, null, null, null, null, null, null, null).getContent())
                .extracting(AlumniProfileResponse::role).containsExactly("SENIOR");
        assertThat(discover(0, 20, "ALUMNI", null, null, null, null, null, null, null, null, null).getContent())
                .extracting(AlumniProfileResponse::role).containsExactly("ALUMNI");
    }

    @Test
    @DisplayName("program and pass-out year filters use the latest verification application")
    void filtersByProgramAndPassoutYear() {
        ObjectId csAlumni = seedAlumni("CS Alumnus", Visibility.PUBLIC, true);
        seedApplication(csAlumni, ApplicationType.STUDENT, Program.CS, 3, null, 1, Instant.now());

        ObjectId seAlumni = seedAlumni("SE Alumnus", Visibility.PUBLIC, true);
        seedApplication(seAlumni, ApplicationType.ALUMNI, null, null, 2024, 1, Instant.now());

        assertThat(discover(0, 20, null, "CS", null, null, null, null, null, null, null, null).getContent())
                .extracting(AlumniProfileResponse::userId).containsExactly(csAlumni.toHexString());
        assertThat(discover(0, 20, null, null, "2024", null, null, null, null, null, null, null).getContent())
                .extracting(AlumniProfileResponse::userId).containsExactly(seAlumni.toHexString());
    }

    @Test
    @DisplayName("skills filter matches any requested whole token case-insensitively")
    void filtersBySkillsAnyAcrossTokens() {
        ObjectId java = seedAlumni("Java Alumnus", Visibility.PUBLIC, true);
        setProfile(java, List.of("Java", "Spring"), List.of(), "Acme", "Backend", "Engineer");

        ObjectId python = seedAlumni("Python Alumnus", Visibility.PUBLIC, true);
        setProfile(python, List.of("python"), List.of(), "Acme", "Data", "Analyst");

        assertThat(discover(0, 20, List.of("java")).getContent()).extracting(AlumniProfileResponse::userId)
                .containsExactly(java.toHexString());
        assertThat(discover(0, 20, List.of("python", "kotlin")).getContent()).extracting(AlumniProfileResponse::userId)
                .containsExactly(python.toHexString());
    }

    @Test
    @DisplayName("technologies filter matches any requested whole token")
    void filtersByTechnologiesAnyAcrossTokens() {
        ObjectId mongoUser = seedAlumni("Mongo Alumnus", Visibility.PUBLIC, true);
        setTechnologies(mongoUser, List.of("mongo", "docker"));

        ObjectId qt = seedAlumni("Qt Alumnus", Visibility.PUBLIC, true);
        setTechnologies(qt, List.of("qt"));

        assertThat(discoverTechnologies(List.of("docker", "aws")).getContent())
                .extracting(AlumniProfileResponse::userId).containsExactly(mongoUser.toHexString());
    }

    @Test
    @DisplayName("company, work area, job title and name filters are case-insensitive substrings")
    void filtersByContainsOnCompanyWorkAreaJobTitleAndName() {
        ObjectId matching = seedUser("Ali Raza", AppRole.ALUMNI, VerificationStatus.VERIFIED, true);
        seedProfile(matching, Visibility.PUBLIC, true, List.of(), List.of(),
                "Acme Corporation", "Backend Engineering", "Senior Engineer");

        ObjectId other = seedUser("Bilal Khan", AppRole.ALUMNI, VerificationStatus.VERIFIED, true);
        seedProfile(other, Visibility.PUBLIC, true, List.of(), List.of(),
                "Globex", "Sales", "Account Manager");

        assertThat(discover(0, 20, "acme", null, null, null)).extracting(AlumniProfileResponse::userId)
                .containsExactly(matching.toHexString());
        assertThat(discover(0, 20, null, "backend", null, null)).extracting(AlumniProfileResponse::userId)
                .containsExactly(matching.toHexString());
        assertThat(discover(0, 20, null, null, "ENGINEER", null)).extracting(AlumniProfileResponse::userId)
                .containsExactly(matching.toHexString());
        assertThat(discoverByName("ali")).extracting(AlumniProfileResponse::userId)
                .containsExactly(matching.toHexString());
    }

    @Test
    @DisplayName("mentorship true matches only explicit flags; false includes missing flags")
    void filtersByMentorshipAvailability() {
        ObjectId mentor = seedAlumni("Mentor", Visibility.PUBLIC, true);
        ObjectId nonMentor = seedAlumni("Non Mentor", Visibility.PUBLIC, false);
        ObjectId missingFlag = seedAlumni("Missing Flag", Visibility.PUBLIC, false);
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("userId").is(missingFlag)),
                new Update().unset("mentorshipAvailable"),
                Profile.class);

        assertThat(discoverByMentorship("true").getContent())
                .extracting(AlumniProfileResponse::userId).containsExactly(mentor.toHexString());
        assertThat(discoverByMentorship("false").getContent())
                .extracting(AlumniProfileResponse::userId)
                .containsExactlyInAnyOrder(nonMentor.toHexString(), missingFlag.toHexString());
    }

    // ------------------------------------------------------------------- helpers

    private boolean mongoAvailable() {
        try {
            mongoTemplate.executeCommand(new Document("ping", 1));
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private void clearCollections() {
        mongoTemplate.getCollection(USERS).drop();
        mongoTemplate.getCollection(PROFILES).drop();
        mongoTemplate.getCollection(APPLICATIONS).drop();
    }

    private User verifiedRequester() {
        User u = new User();
        u.setId(new ObjectId());
        u.setName("Requesting Senior");
        u.setRole(AppRole.SENIOR);
        u.setVerificationStatus(VerificationStatus.VERIFIED);
        u.setEnabled(true);
        return u;
    }

    private Page<AlumniProfileResponse> discoverAll() {
        return discover(0, 20, null, null, null, null, null, null, null, null, null, null);
    }

    /** The full 12-argument contract, with the filters not named in this short cut left null. */
    private Page<AlumniProfileResponse> discover(int page, int size, List<String> skills) {
        return discover(page, size, null, null, null, skills, null, null, null, null, null, null);
    }

    private Page<AlumniProfileResponse> discover(int page, int size, String company, String workArea,
                                                 String jobTitle, String mentorship) {
        return discover(page, size, null, null, null, null, null, company, workArea, jobTitle, null, mentorship);
    }

    private Page<AlumniProfileResponse> discoverTechnologies(List<String> technologies) {
        return discover(0, 20, null, null, null, null, technologies, null, null, null, null, null);
    }

    private Page<AlumniProfileResponse> discoverByName(String name) {
        return discover(0, 20, null, null, null, null, null, null, null, null, name, null);
    }

    private Page<AlumniProfileResponse> discoverByMentorship(String mentorship) {
        return discover(0, 20, null, null, null, null, null, null, null, null, null, mentorship);
    }

    private Page<AlumniProfileResponse> discover(int page, int size, String role, String program,
                                                 String passoutYear, List<String> skills, List<String> technologies,
                                                 String company, String workArea, String jobTitle, String name,
                                                 String mentorship) {
        return service.discover(page, size, role, program, passoutYear, skills, technologies,
                company, workArea, jobTitle, name, mentorship);
    }

    private ObjectId seedAlumni(String name, Visibility visibility, boolean mentorship) {
        ObjectId id = seedUser(name, AppRole.ALUMNI, VerificationStatus.VERIFIED, true);
        seedProfile(id, visibility, mentorship, List.of(), List.of(), null, null, null);
        return id;
    }

    private ObjectId seedSenior(String name, Visibility visibility, boolean mentorship) {
        ObjectId id = seedUser(name, AppRole.SENIOR, VerificationStatus.VERIFIED, true);
        seedProfile(id, visibility, mentorship, List.of(), List.of(), null, null, null);
        return id;
    }

    private ObjectId seedAlumniWithPassout(String name, int passoutYear) {
        ObjectId id = seedAlumni(name, Visibility.PUBLIC, true);
        seedApplication(id, ApplicationType.ALUMNI, null, null, passoutYear, 1, Instant.now());
        return id;
    }

    private ObjectId seedUser(String name, AppRole role, VerificationStatus status, boolean enabled) {
        return seedUserWithId(new ObjectId(), name, role, status, enabled);
    }

    private ObjectId seedUserWithId(ObjectId id, String name, AppRole role, VerificationStatus status,
                                    boolean enabled) {
        User u = new User();
        u.setId(id);
        u.setName(name);
        u.setRole(role);
        u.setVerificationStatus(status);
        u.setEnabled(enabled);
        u.setEmail(name.toLowerCase().replace(' ', '.') + "@ubit.edu.pk");
        u.setGoogleId("google-" + id.toHexString());
        mongoTemplate.insert(u);
        return id;
    }

    private void seedProfile(ObjectId userId, Visibility visibility, Boolean mentorship, List<String> skills,
                             List<String> technologies, String company, String workArea, String jobTitle) {
        Profile p = new Profile();
        p.setUserId(userId);
        p.setVisibility(visibility);
        p.setMentorshipAvailable(mentorship);
        p.setSkills(skills == null ? new ArrayList<>() : skills);
        p.setTechnologies(technologies == null ? new ArrayList<>() : technologies);
        p.setCompany(company);
        p.setWorkArea(workArea);
        p.setJobTitle(jobTitle);
        p.setBio("Bio for " + userId.toHexString());
        mongoTemplate.insert(p);
    }

    private void setProfile(ObjectId userId, List<String> skills, List<String> technologies, String company,
                            String workArea, String jobTitle) {
        seedProfile(userId, Visibility.PUBLIC, true, skills, technologies, company, workArea, jobTitle);
    }

    private void setTechnologies(ObjectId userId, List<String> technologies) {
        seedProfile(userId, Visibility.PUBLIC, true, List.of(), technologies, null, null, null);
    }

    private void seedApplication(ObjectId userId, ApplicationType type, Program program, Integer semester,
                                 Integer passoutYear, int attempt, Instant submittedAt) {
        seedApplication(userId, type, program, semester, passoutYear, attempt, submittedAt,
                ApplicationStatus.VERIFIED);
    }

    private void seedApplication(ObjectId userId, ApplicationType type, Program program, Integer semester,
                                 Integer passoutYear, int attempt, Instant submittedAt,
                                 ApplicationStatus status) {
        VerificationApplication app = new VerificationApplication();
        app.setUserId(userId);
        app.setType(type);
        app.setStatus(status);
        app.setProgram(program);
        app.setCurrentSemester(semester);
        app.setPassoutYear(passoutYear);
        app.setAttemptNumber(attempt);
        app.setSubmittedAt(submittedAt);
        mongoTemplate.insert(app);
    }
}