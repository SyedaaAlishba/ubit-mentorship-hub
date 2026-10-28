package com.ubit.mentorshiphub.admin.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.ubit.mentorshiphub.admin.dto.AdminVerificationApplicationResponse;
import com.ubit.mentorshiphub.admin.dto.AdminVerificationDecisionRequest;
import com.ubit.mentorshiphub.admin.dto.AdminVerificationDecisionRequest.Decision;
import com.ubit.mentorshiphub.admin.dto.AdminVerificationDecisionResponse;
import com.ubit.mentorshiphub.admin.service.AdminVerificationService;
import com.ubit.mentorshiphub.common.exception.VerificationStateConflictException;
import com.ubit.mentorshiphub.common.security.AccountAccess;
import com.ubit.mentorshiphub.config.MongoIndexInitializer;
import com.ubit.mentorshiphub.user.model.AppRole;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.user.model.VerificationStatus;
import com.ubit.mentorshiphub.user.repository.UserRepository;
import com.ubit.mentorshiphub.user.service.UserService;
import com.ubit.mentorshiphub.verification.model.ApplicationStatus;
import com.ubit.mentorshiphub.verification.model.ApplicationType;
import com.ubit.mentorshiphub.verification.model.VerificationApplication;
import com.ubit.mentorshiphub.verification.repository.VerificationApplicationRepository;
import com.ubit.mentorshiphub.verification.storage.VerificationDocumentService;

/**
 * The administrator review service against a real MongoDB: the conditional
 * update that guards a decision, and the consistency it produces between the
 * application and the account.
 *
 * <p>A standalone {@code mongod} cannot run multi-document transactions, so the
 * "only one administrator decides an application" rule is a conditional
 * {@code findAndModify}. A mock cannot prove that condition, so this test drives
 * the real service over the real repository and the real {@code UserService},
 * and runs two decisions at once on a latch to show exactly one wins - the same
 * shape as the mentorship integration test's acceptance race.
 *
 * <p>Runs against a dedicated throwaway database, drops its collections first,
 * and is skipped rather than failed when no MongoDB is reachable, so
 * {@code mvn clean verify} stays green without one. Only the authorization
 * lookups are mocked: everything that touches the database is the production
 * code path.
 */
@DataMongoTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.data.mongodb.uri="
        + "mongodb://localhost:27017/ubit_admin_verification_it?serverSelectionTimeoutMS=1000&connectTimeoutMS=1000")
class AdminVerificationIntegrationTest {

    private static final String APPLICATIONS = "verification_applications";
    private static final String USERS = "users";
    private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");

    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private VerificationApplicationRepository applicationRepository;
    @Autowired private UserRepository userRepository;

    private AdminVerificationService service;
    private UserService userService;
    private AccountAccess accountAccess;
    private User administrator;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(mongoAvailable(),
                "MongoDB is not reachable; skipping the admin verification integration test");
        mongoTemplate.getCollection(APPLICATIONS).drop();
        mongoTemplate.getCollection(USERS).drop();
        new MongoIndexInitializer(mongoTemplate, true).createIndexes();

        Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));
        userService = new UserService(userRepository, clock);
        accountAccess = mock(AccountAccess.class);
        administrator = userService.grantBootstrapAdmin(persistUser(AppRole.ADMIN, VerificationStatus.PENDING));
        when(accountAccess.requireCurrentUser()).thenReturn(administrator);
        service = new AdminVerificationService(applicationRepository,
                mock(VerificationDocumentService.class), userService, accountAccess, mongoTemplate, clock);
    }

    // -------------------------------------------------------------------- index

    @Test
    @DisplayName("the review-queue index exists, so the paged status filter is covered")
    void createsReviewQueueIndex() {
        assertThat(mongoTemplate.indexOps(APPLICATIONS).getIndexInfo())
                .extracting(IndexInfo::getName)
                .contains("ix_verification_applications_status_queue",
                        "ix_verification_applications_user_recent");
    }

    // ------------------------------------------------------------------ listing

    @Test
    @DisplayName("the pending queue is ordered oldest first and excludes decided rows")
    void listsPendingOldestFirst() {
        User older = persistUser(AppRole.STUDENT, VerificationStatus.PENDING);
        User newer = persistUser(AppRole.STUDENT, VerificationStatus.PENDING);
        persistApplication(older.getId(), ApplicationStatus.PENDING, NOW.minusSeconds(600));
        persistApplication(newer.getId(), ApplicationStatus.PENDING, NOW.minusSeconds(60));
        persistApplication(older.getId(), ApplicationStatus.VERIFIED, NOW.minusSeconds(30));

        Page<AdminVerificationApplicationResponse> page = service.listApplications(0, 20, null);

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).submittedAt()).isBefore(page.getContent().get(1).submittedAt());
        assertThat(page.getContent()).allSatisfy(row ->
                assertThat(row.status()).isEqualTo(ApplicationStatus.PENDING));
        assertThat(page.getContent().get(0).applicantEmail()).isEqualTo(older.getEmail());
    }

    // ---------------------------------------------------------------- decisions

    @Test
    @DisplayName("approving records the decision and moves the account in one service call")
    void approveMovesApplicationAndAccount() {
        User applicant = persistUser(AppRole.STUDENT, VerificationStatus.PENDING);
        VerificationApplication application = persistApplication(applicant.getId(),
                ApplicationStatus.PENDING, NOW.minusSeconds(120));

        AdminVerificationDecisionResponse response = service.decide(application.getId().toHexString(),
                new AdminVerificationDecisionRequest(Decision.VERIFIED, null));

        assertThat(response.applicationStatus()).isEqualTo(ApplicationStatus.VERIFIED);
        assertThat(response.userVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(response.decidedAt()).isNotNull();

        VerificationApplication stored = applicationRepository.findById(application.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ApplicationStatus.VERIFIED);
        assertThat(stored.getReviewerUserId()).isEqualTo(administrator.getId());
        assertThat(stored.getDecidedAt()).isNotNull();

        assertThat(userRepository.findById(applicant.getId()).orElseThrow().getVerificationStatus())
                .isEqualTo(VerificationStatus.VERIFIED);
    }

    @Test
    @DisplayName("rejecting stores the reason, moves the account, and leaves the role untouched")
    void rejectStoresReasonAndMovesAccount() {
        User applicant = persistUser(AppRole.STUDENT, VerificationStatus.PENDING);
        VerificationApplication application = persistApplication(applicant.getId(),
                ApplicationStatus.PENDING, NOW.minusSeconds(120));

        service.decide(application.getId().toHexString(),
                new AdminVerificationDecisionRequest(Decision.REJECTED, "Enrolment year does not match."));

        VerificationApplication stored = applicationRepository.findById(application.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(stored.getReviewerNote()).isEqualTo("Enrolment year does not match.");

        User reloaded = userRepository.findById(applicant.getId()).orElseThrow();
        assertThat(reloaded.getVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(reloaded.getRole()).isEqualTo(AppRole.STUDENT);
    }

    @Test
    @DisplayName("a rejected applicant can be decided again after resubmitting, so rejection is not terminal")
    void rejectedApplicantCanResubmit() {
        User applicant = persistUser(AppRole.STUDENT, VerificationStatus.PENDING);
        VerificationApplication first = persistApplication(applicant.getId(),
                ApplicationStatus.PENDING, NOW.minusSeconds(300));
        service.decide(first.getId().toHexString(),
                new AdminVerificationDecisionRequest(Decision.REJECTED, "Blurry upload."));

        // Resubmission: the account goes back to PENDING and a new attempt is filed.
        applicant.setVerificationStatus(VerificationStatus.PENDING);
        userRepository.save(applicant);
        VerificationApplication second = persistApplication(applicant.getId(),
                ApplicationStatus.PENDING, NOW.minusSeconds(10));

        AdminVerificationDecisionResponse response = service.decide(second.getId().toHexString(),
                new AdminVerificationDecisionRequest(Decision.VERIFIED, null));

        assertThat(response.applicationStatus()).isEqualTo(ApplicationStatus.VERIFIED);
        assertThat(response.userVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
    }

    @Test
    @DisplayName("deciding an already-decided application is a conflict and changes nothing")
    void repeatedDecisionIsConflict() {
        User applicant = persistUser(AppRole.STUDENT, VerificationStatus.PENDING);
        VerificationApplication application = persistApplication(applicant.getId(),
                ApplicationStatus.PENDING, NOW.minusSeconds(120));
        service.decide(application.getId().toHexString(),
                new AdminVerificationDecisionRequest(Decision.VERIFIED, null));

        assertThatThrownBy(() -> service.decide(application.getId().toHexString(),
                new AdminVerificationDecisionRequest(Decision.REJECTED, "Changed my mind.")))
                .isInstanceOf(VerificationStateConflictException.class);

        assertThat(applicationRepository.findById(application.getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.VERIFIED);
    }

    @Test
    @DisplayName("two administrators deciding at once: exactly one wins, the account moves once")
    void concurrentDecisionsHaveOneWinner() throws Exception {
        User applicant = persistUser(AppRole.STUDENT, VerificationStatus.PENDING);
        VerificationApplication application = persistApplication(applicant.getId(),
                ApplicationStatus.PENDING, NOW.minusSeconds(120));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<Boolean> approve = decisionTask(ready, go, application.getId().toHexString(), Decision.VERIFIED);
        Callable<Boolean> reject = decisionTask(ready, go, application.getId().toHexString(), Decision.REJECTED);

        Future<Boolean> first = pool.submit(approve);
        Future<Boolean> second = pool.submit(reject);
        ready.await(5, TimeUnit.SECONDS);
        go.countDown();

        boolean firstWon = first.get(10, TimeUnit.SECONDS);
        boolean secondWon = second.get(10, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(firstWon ^ secondWon)
                .as("exactly one decision should succeed")
                .isTrue();

        VerificationApplication stored = applicationRepository.findById(application.getId()).orElseThrow();
        assertThat(stored.getStatus()).isIn(ApplicationStatus.VERIFIED, ApplicationStatus.REJECTED);
        assertThat(userRepository.findById(applicant.getId()).orElseThrow().getVerificationStatus())
                .isEqualTo(stored.getStatus() == ApplicationStatus.VERIFIED
                        ? VerificationStatus.VERIFIED : VerificationStatus.REJECTED);
    }

    // ------------------------------------------------------------------- helpers

    private Callable<Boolean> decisionTask(CountDownLatch ready, CountDownLatch go,
                                           String applicationId, Decision decision) {
        return () -> {
            ready.countDown();
            go.await(5, TimeUnit.SECONDS);
            String note = decision == Decision.REJECTED ? "Evidence did not match the register." : null;
            try {
                service.decide(applicationId, new AdminVerificationDecisionRequest(decision, note));
                return true;
            } catch (VerificationStateConflictException ex) {
                return false;
            }
        };
    }

    private boolean mongoAvailable() {
        try {
            mongoTemplate.executeCommand(new Document("ping", 1));
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private User persistUser(AppRole role, VerificationStatus status) {
        User user = new User();
        user.setGoogleId("google-" + new ObjectId().toHexString());
        user.setEmail(new ObjectId().toHexString() + "@example.com");
        user.setName("Applicant");
        user.setRole(role);
        user.setVerificationStatus(status);
        user.setEnabled(true);
        user.setCreatedAt(NOW);
        user.setUpdatedAt(NOW);
        return userRepository.save(user);
    }

    private VerificationApplication persistApplication(ObjectId userId, ApplicationStatus status, Instant submittedAt) {
        VerificationApplication application = new VerificationApplication();
        application.setUserId(userId);
        application.setType(ApplicationType.STUDENT);
        application.setClaimedRole("STUDENT");
        application.setStatus(status);
        application.setAttemptNumber(1);
        application.setSubmittedAt(submittedAt);
        application.setDocumentId(new ObjectId());
        application.setDocumentOriginalFilename("card.png");
        return applicationRepository.save(application);
    }
}
