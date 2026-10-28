package com.ubit.mentorshiphub.mentorship.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.ubit.mentorshiphub.common.exception.MentorshipConflictException;
import com.ubit.mentorshiphub.common.security.AccountAccess;
import com.ubit.mentorshiphub.config.MongoIndexInitializer;
import com.ubit.mentorshiphub.mentorship.dto.MentorshipRequestCreateRequest;
import com.ubit.mentorshiphub.mentorship.dto.MentorshipRequestResponse;
import com.ubit.mentorshiphub.mentorship.dto.MentorshipResponse;
import com.ubit.mentorshiphub.mentorship.model.Mentorship;
import com.ubit.mentorshiphub.mentorship.model.MentorshipRequest;
import com.ubit.mentorshiphub.mentorship.model.MentorshipRequestStatus;
import com.ubit.mentorshiphub.mentorship.model.MentorshipStatus;
import com.ubit.mentorshiphub.mentorship.model.PairKeys;
import com.ubit.mentorshiphub.mentorship.service.MentorshipRecoveryService;
import com.ubit.mentorshiphub.mentorship.service.MentorshipService;
import com.ubit.mentorshiphub.profile.model.Profile;
import com.ubit.mentorshiphub.profile.repository.ProfileRepository;
import com.ubit.mentorshiphub.user.model.AppRole;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.user.model.VerificationStatus;
import com.ubit.mentorshiphub.user.repository.UserRepository;
import com.ubit.mentorshiphub.user.service.UserService;

/**
 * The mentorship persistence contract against a real MongoDB: the unique partial
 * indexes the acceptance flow leans on are the production ones, created by
 * driving {@link MongoIndexInitializer} rather than restating the definitions
 * here.
 *
 * <p>A standalone {@code mongod} cannot run multi-document transactions, so the
 * "one pending request per pair" and "one active mentorship per pair" rules are
 * enforced by the database. These tests prove the guards actually reject
 * duplicates - including under real concurrency - and that a delivered
 * mentorship is written and the request flipped in the same operation.
 *
 * <p>Like the alumni integration test, it runs against a dedicated throwaway
 * database, drops its collections first, and is skipped rather than failed when
 * no MongoDB is reachable, so {@code mvn clean verify} stays green without one.
 */
@DataMongoTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.data.mongodb.uri="
        + "mongodb://localhost:27017/ubit_mentorship_it?serverSelectionTimeoutMS=1000&connectTimeoutMS=1000")
class MentorshipIntegrationTest {

    private static final String REQUESTS = "mentorship_requests";
    private static final String MENTORSHIPS = "mentorships";
    private static final String USERS = "users";
    private static final String PROFILES = "profiles";
    private static final String MESSAGE = "I would value your guidance on backend engineering.";
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private MentorshipRequestRepository requestRepository;
    @Autowired private MentorshipRepository mentorshipRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private UserRepository userRepository;

    private final ObjectId student = new ObjectId("64b0000000000000000000a1");
    private final ObjectId mentor = new ObjectId("64b0000000000000000000b2");

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(mongoAvailable(),
                "MongoDB is not reachable; skipping the mentorship integration test");
        mongoTemplate.getCollection(REQUESTS).drop();
        mongoTemplate.getCollection(MENTORSHIPS).drop();
        mongoTemplate.getCollection(USERS).drop();
        mongoTemplate.getCollection(PROFILES).drop();
        new MongoIndexInitializer(mongoTemplate, true).createIndexes();
    }

    // ------------------------------------------------------------ index guards

    @Test
    @DisplayName("the expected unique partial indexes are present")
    void createsTheExpectedIndexes() {
        assertThat(mongoTemplate.indexOps(REQUESTS).getIndexInfo())
                .extracting(IndexInfo::getName)
                .contains("uk_mentorship_requests_open_pair",
                        "ix_mentorship_requests_requester_created",
                        "ix_mentorship_requests_recipient_created");
        assertThat(mongoTemplate.indexOps(MENTORSHIPS).getIndexInfo())
                .extracting(IndexInfo::getName)
                .contains("uk_mentorships_active_pair",
                        "ix_mentorships_mentor_status",
                        "ix_mentorships_mentee_status");

        assertThat(mongoTemplate.indexOps(REQUESTS).getIndexInfo())
                .filteredOn(index -> index.getName().equals("uk_mentorship_requests_open_pair"))
                .singleElement().satisfies(index -> assertThat(index.isUnique()).isTrue());
        assertThat(mongoTemplate.indexOps(MENTORSHIPS).getIndexInfo())
                .filteredOn(index -> index.getName().equals("uk_mentorships_active_pair"))
                .singleElement().satisfies(index -> assertThat(index.isUnique()).isTrue());
    }

    @Test
    @DisplayName("an ACCEPTING request blocks a new pending request for the same pair")
    void acceptingRequestBlocksNewPendingForThePair() {
        requestRepository.save(requestWithStatus(student, mentor, MentorshipRequestStatus.ACCEPTING));

        assertThatThrownBy(() -> requestRepository.save(pending(student, mentor)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("a second pending request for the same pair is rejected by the index")
    void duplicatePendingPairIsRejected() {
        requestRepository.save(pending(student, mentor));

        assertThatThrownBy(() -> requestRepository.save(pending(student, mentor)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("the reverse direction collides because the pair key is order-independent")
    void reverseDirectionCollides() {
        requestRepository.save(pending(student, mentor));

        assertThatThrownBy(() -> requestRepository.save(pending(mentor, student)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("a cancelled request leaves the pair free for a new pending one")
    void cancelledPendingFreesThePair() {
        MentorshipRequest first = requestRepository.save(pending(student, mentor));
        first.setStatus(MentorshipRequestStatus.CANCELLED);
        requestRepository.save(first);

        requestRepository.save(pending(student, mentor));

        assertThat(requestRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("a second active mentorship for the same pair is rejected by the index")
    void duplicateActiveMentorshipIsRejected() {
        mentorshipRepository.save(activeMentorship(mentor, student));

        assertThatThrownBy(() -> mentorshipRepository.save(activeMentorship(mentor, student)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("an ended mentorship does not block a new active one for the pair")
    void endedMentorshipFreesThePair() {
        Mentorship first = mentorshipRepository.save(activeMentorship(mentor, student));
        first.setStatus(MentorshipStatus.ENDED);
        mentorshipRepository.save(first);

        mentorshipRepository.save(activeMentorship(mentor, student));

        assertThat(mentorshipRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("only one of many concurrent pending inserts for the pair wins")
    void concurrentPendingInsertsAdmitExactlyOne() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> attempts = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                attempts.add(pool.submit(() -> {
                    start.await();
                    try {
                        requestRepository.save(pending(student, mentor));
                        return true;
                    } catch (DuplicateKeyException ex) {
                        return false;
                    }
                }));
            }
            start.countDown();

            int successes = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get(10, TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            assertThat(successes).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(requestRepository.count()).isEqualTo(1);
    }

    // ------------------------------------------------------------ query shape

    @Test
    @DisplayName("active mentorships are returned for either participant, and ended ones are excluded")
    void findActiveForUserCoversBothRoles() {
        mentorshipRepository.save(activeMentorship(mentor, student));
        Mentorship ended = mentorshipRepository.save(activeMentorship(mentor, new ObjectId()));
        ended.setStatus(MentorshipStatus.ENDED);
        mentorshipRepository.save(ended);

        Page<Mentorship> asMentor = mentorshipRepository.findActiveForUser(mentor, PageRequest.of(0, 20));
        Page<Mentorship> asMentee = mentorshipRepository.findActiveForUser(student, PageRequest.of(0, 20));

        assertThat(asMentor.getTotalElements()).isEqualTo(1);
        assertThat(asMentee.getTotalElements()).isEqualTo(1);
        assertThat(asMentee.getContent().get(0).getMentorUserId()).isEqualTo(mentor);
    }

    // ------------------------------------------------- end-to-end over real Mongo

    @Test
    @DisplayName("accept writes the mentorship and flips the request, all against real Mongo")
    void acceptFlowPersistsBothDocuments() {
        seedUser(student, "Student", AppRole.STUDENT);
        seedUser(mentor, "Mentor", AppRole.ALUMNI);
        seedProfile(mentor, true);

        UserService userService = new UserService(userRepository, Clock.fixed(NOW, ZoneId.of("UTC")));
        AccountAccess accountAccess = mock(AccountAccess.class);
        MentorshipService service = new MentorshipService(requestRepository, mentorshipRepository,
                userService, profileRepository, accountAccess, mongoTemplate, Clock.fixed(NOW, ZoneId.of("UTC")));

        User studentUser = userRepository.findById(student).orElseThrow();
        User mentorUser = userRepository.findById(mentor).orElseThrow();
        when(accountAccess.requireCurrentUser()).thenReturn(studentUser);
        MentorshipRequestResponse created = service.createRequest(
                new MentorshipRequestCreateRequest(mentor.toHexString(), MESSAGE, "Learn Spring"));

        when(accountAccess.requireCurrentUser()).thenReturn(mentorUser);
        MentorshipResponse accepted = service.accept(created.id());

        assertThat(accepted.status()).isEqualTo(MentorshipStatus.ACTIVE);
        MentorshipRequest stored = requestRepository.findById(new ObjectId(created.id())).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(MentorshipRequestStatus.ACCEPTED);
        assertThat(mentorshipRepository.findActiveForUser(mentor, PageRequest.of(0, 20)).getTotalElements())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrent acceptance attempts cannot create duplicate active mentorships and only one wins")
    void concurrentAcceptAttemptsProduceExactlyOneActiveAndValidOutcome() throws Exception {
        seedUser(student, "Student", AppRole.STUDENT);
        seedUser(mentor, "Mentor", AppRole.ALUMNI);
        seedProfile(mentor, true);

        UserService userService = new UserService(userRepository, Clock.fixed(NOW, ZoneId.of("UTC")));
        AccountAccess accountAccess = mock(AccountAccess.class);
        MentorshipService service = new MentorshipService(requestRepository, mentorshipRepository,
                userService, profileRepository, accountAccess, mongoTemplate, Clock.fixed(NOW, ZoneId.of("UTC")));

        User studentUser = userRepository.findById(student).orElseThrow();
        User mentorUser = userRepository.findById(mentor).orElseThrow();
        when(accountAccess.requireCurrentUser()).thenReturn(studentUser);
        MentorshipRequestResponse created = service.createRequest(
                new MentorshipRequestCreateRequest(mentor.toHexString(), MESSAGE, "Learn Spring"));

        int threads = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            java.util.ArrayList<Future<Boolean>> results = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        when(accountAccess.requireCurrentUser()).thenReturn(mentorUser);
                        service.accept(created.id());
                        return true;
                    } catch (com.ubit.mentorshiphub.common.exception.MentorshipConflictException ex) {
                        return false;
                    }
                }));
            }
            start.countDown();

            int successes = 0;
            for (Future<Boolean> r : results) {
                if (r.get(10, TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            assertThat(successes).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(mentorshipRepository.count()).isEqualTo(1);
        MentorshipRequest stored = requestRepository.findById(new ObjectId(created.id())).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(MentorshipRequestStatus.ACCEPTED);
        assertThat(mentorshipRepository.findAll()).hasSize(1);
        assertThat(mentorshipRepository.findAll().get(0).getStatus()).isEqualTo(MentorshipStatus.ACTIVE);
    }

    // ------------------------------------------------- active/pending consistency

    @Test
    @DisplayName("an active mentorship stops a new pending request from being created")
    void activeMentorshipBlocksNewPendingRequest() {
        seedUser(student, "Student", AppRole.STUDENT);
        seedUser(mentor, "Mentor", AppRole.ALUMNI);
        seedProfile(mentor, true);
        mentorshipRepository.save(activeMentorship(mentor, student));

        MentorshipService service = serviceFor(userRepository.findById(student).orElseThrow());

        assertThatThrownBy(() -> service.createRequest(
                new MentorshipRequestCreateRequest(mentor.toHexString(), MESSAGE, null)))
                .isInstanceOf(MentorshipConflictException.class);
        assertThat(requestRepository.findByStatus(MentorshipRequestStatus.PENDING)).isEmpty();
    }

    @Test
    @DisplayName("a failed accept never leaves a second active mentorship behind")
    void failedAcceptNeverCreatesASecondMentorship() {
        seedUser(student, "Student", AppRole.STUDENT);
        seedUser(mentor, "Mentor", AppRole.ALUMNI);
        seedProfile(mentor, true);

        MentorshipRequest request = requestRepository.save(pending(student, mentor));
        mentorshipRepository.save(activeMentorshipForRequest(mentor, student, request.getId()));

        MentorshipService service = serviceFor(userRepository.findById(mentor).orElseThrow());

        assertThatThrownBy(() -> service.accept(request.getId().toHexString()))
                .isInstanceOf(MentorshipConflictException.class);
        assertThat(mentorshipRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("concurrent accept and cancel leave a deterministic, internally consistent outcome")
    void concurrentAcceptAndCancelHaveDeterministicOutcome() throws Exception {
        seedUser(student, "Student", AppRole.STUDENT);
        seedUser(mentor, "Mentor", AppRole.ALUMNI);
        seedProfile(mentor, true);

        MentorshipService studentService = serviceFor(userRepository.findById(student).orElseThrow());
        MentorshipService mentorService = serviceFor(userRepository.findById(mentor).orElseThrow());

        MentorshipRequestResponse created = studentService.createRequest(
                new MentorshipRequestCreateRequest(mentor.toHexString(), MESSAGE, "Learn Spring"));

        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            java.util.ArrayList<Future<Boolean>> results = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                boolean accept = i % 2 == 0;
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        if (accept) {
                            mentorService.accept(created.id());
                        } else {
                            studentService.cancel(created.id());
                        }
                        return true;
                    } catch (MentorshipConflictException ex) {
                        return false;
                    }
                }));
            }
            start.countDown();
            for (Future<Boolean> r : results) {
                r.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        MentorshipRequestStatus finalStatus = requestRepository
                .findById(new ObjectId(created.id())).orElseThrow().getStatus();
        if (finalStatus == MentorshipRequestStatus.ACCEPTED) {
            assertThat(mentorshipRepository.count()).isEqualTo(1);
            assertThat(mentorshipRepository.findAll().get(0).getStatus()).isEqualTo(MentorshipStatus.ACTIVE);
        } else {
            assertThat(finalStatus).isEqualTo(MentorshipRequestStatus.CANCELLED);
            assertThat(mentorshipRepository.count()).isZero();
        }
    }

    // ------------------------------------------------------------ recovery

    @Test
    @DisplayName("recovery completes an interrupted acceptance and preserves the mentorship")
    void recoveryCompletesInterruptedAcceptanceAndPreservesMentorship() {
        MentorshipRequest request = requestRepository.save(
                requestWithStatus(student, mentor, MentorshipRequestStatus.ACCEPTING));
        mentorshipRepository.save(activeMentorshipForRequest(mentor, student, request.getId()));

        MentorshipRecoveryService recovery = recoveryService();
        assertThat(recovery.recoverInterruptedAcceptances()).isEqualTo(1);

        assertThat(requestRepository.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(MentorshipRequestStatus.ACCEPTED);
        assertThat(mentorshipRepository.count()).isEqualTo(1);
        assertThat(mentorshipRepository.findAll().get(0).getStatus()).isEqualTo(MentorshipStatus.ACTIVE);
    }

    @Test
    @DisplayName("recovery reopens an interrupted acceptance that never wrote a mentorship")
    void recoveryReopensAcceptanceThatWroteNoMentorship() {
        MentorshipRequest request = requestRepository.save(
                requestWithStatus(student, mentor, MentorshipRequestStatus.ACCEPTING));

        MentorshipRecoveryService recovery = recoveryService();
        assertThat(recovery.recoverInterruptedAcceptances()).isEqualTo(1);
        assertThat(requestRepository.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(MentorshipRequestStatus.PENDING);
        assertThat(mentorshipRepository.count()).isZero();
        assertThat(recovery.recoverInterruptedAcceptances()).isZero();
    }

    @Test
    @DisplayName("recovery cancels a pending request that shares a pair with an active mentorship")
    void recoveryCancelsPendingBesideActiveMentorship() {
        MentorshipRequest request = requestRepository.save(pending(student, mentor));
        Mentorship mentorship = mentorshipRepository.save(
                activeMentorshipForRequest(mentor, student, request.getId()));

        MentorshipRecoveryService recovery = recoveryService();
        assertThat(recovery.recoverInterruptedAcceptances()).isEqualTo(1);

        assertThat(requestRepository.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(MentorshipRequestStatus.CANCELLED);
        assertThat(mentorshipRepository.findById(mentorship.getId()).orElseThrow().getStatus())
                .isEqualTo(MentorshipStatus.ACTIVE);
    }

    @Test
    @DisplayName("recovery is idempotent and never corrupts completed or healthy records")
    void recoveryIsIdempotentAndLeavesCompletedRecordsUntouched() {
        MentorshipRequest interrupted = requestRepository.save(
                requestWithStatus(student, mentor, MentorshipRequestStatus.ACCEPTING));
        mentorshipRepository.save(activeMentorshipForRequest(mentor, student, interrupted.getId()));

        ObjectId otherStudent = new ObjectId("64b0000000000000000000d4");
        ObjectId otherMentor = new ObjectId("64b0000000000000000000e5");
        MentorshipRequest accepted = requestRepository.save(
                requestWithStatus(otherStudent, otherMentor, MentorshipRequestStatus.ACCEPTED));
        mentorshipRepository.save(activeMentorshipForRequest(otherMentor, otherStudent, accepted.getId()));
        ObjectId thirdStudent = new ObjectId("64b0000000000000000000f6");
        MentorshipRequest plainPending = requestRepository.save(
                requestWithStatus(thirdStudent, mentor, MentorshipRequestStatus.PENDING));

        MentorshipRecoveryService recovery = recoveryService();
        assertThat(recovery.recoverInterruptedAcceptances()).isEqualTo(1);
        assertThat(recovery.recoverInterruptedAcceptances()).isZero();

        assertThat(requestRepository.findById(interrupted.getId()).orElseThrow().getStatus())
                .isEqualTo(MentorshipRequestStatus.ACCEPTED);
        assertThat(requestRepository.findById(accepted.getId()).orElseThrow().getStatus())
                .isEqualTo(MentorshipRequestStatus.ACCEPTED);
        assertThat(requestRepository.findById(plainPending.getId()).orElseThrow().getStatus())
                .isEqualTo(MentorshipRequestStatus.PENDING);
        assertThat(mentorshipRepository.count()).isEqualTo(2);
    }

    // ---------------------------------------------------------------- helpers

    private MentorshipService serviceFor(User currentUser) {
        UserService userService = new UserService(userRepository, Clock.fixed(NOW, ZoneId.of("UTC")));
        AccountAccess accountAccess = mock(AccountAccess.class);
        when(accountAccess.requireCurrentUser()).thenReturn(currentUser);
        return new MentorshipService(requestRepository, mentorshipRepository, userService,
                profileRepository, accountAccess, mongoTemplate, Clock.fixed(NOW, ZoneId.of("UTC")));
    }

    private MentorshipRecoveryService recoveryService() {
        return new MentorshipRecoveryService(requestRepository, mentorshipRepository,
                mongoTemplate, Clock.fixed(NOW, ZoneId.of("UTC")), true);
    }

    private boolean mongoAvailable() {
        try {
            mongoTemplate.executeCommand(new Document("ping", 1));
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private MentorshipRequest pending(ObjectId requester, ObjectId recipient) {
        MentorshipRequest request = new MentorshipRequest();
        request.setRequesterUserId(requester);
        request.setRecipientUserId(recipient);
        request.setMessage(MESSAGE);
        request.setStatus(MentorshipRequestStatus.PENDING);
        request.setPairKey(PairKeys.of(requester, recipient));
        request.setCreatedAt(NOW);
        request.setUpdatedAt(NOW);
        return request;
    }

    private MentorshipRequest requestWithStatus(ObjectId requester, ObjectId recipient,
                                                MentorshipRequestStatus status) {
        MentorshipRequest request = pending(requester, recipient);
        request.setStatus(status);
        return request;
    }

    private Mentorship activeMentorship(ObjectId mentorId, ObjectId menteeId) {
        Mentorship mentorship = new Mentorship();
        mentorship.setMentorUserId(mentorId);
        mentorship.setMenteeUserId(menteeId);
        mentorship.setStatus(MentorshipStatus.ACTIVE);
        mentorship.setPairKey(PairKeys.of(mentorId, menteeId));
        mentorship.setStartedAt(NOW);
        mentorship.setCreatedAt(NOW);
        mentorship.setUpdatedAt(NOW);
        return mentorship;
    }

    private Mentorship activeMentorshipForRequest(ObjectId mentorId, ObjectId menteeId, ObjectId requestId) {
        Mentorship mentorship = activeMentorship(mentorId, menteeId);
        mentorship.setRequestId(requestId);
        return mentorship;
    }

    private void seedUser(ObjectId id, String name, AppRole role) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setRole(role);
        user.setVerificationStatus(VerificationStatus.VERIFIED);
        user.setEnabled(true);
        user.setEmail(name.toLowerCase() + "@ubit.edu.pk");
        user.setGoogleId("google-" + id.toHexString());
        userRepository.save(user);
    }

    private void seedProfile(ObjectId userId, boolean mentorshipAvailable) {
        Profile profile = new Profile();
        profile.setUserId(userId);
        profile.setMentorshipAvailable(mentorshipAvailable);
        profileRepository.save(profile);
    }
}
