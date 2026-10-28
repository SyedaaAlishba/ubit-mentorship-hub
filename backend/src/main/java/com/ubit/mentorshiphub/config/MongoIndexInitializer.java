package com.ubit.mentorshiphub.config;

import com.ubit.mentorshiphub.mentorship.model.Mentorship;
import com.ubit.mentorshiphub.mentorship.model.MentorshipRequest;
import com.ubit.mentorshiphub.mentorship.model.MentorshipRequestStatus;
import com.ubit.mentorshiphub.mentorship.model.MentorshipStatus;
import com.ubit.mentorshiphub.profile.model.Profile;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.verification.model.VerificationApplication;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

/**
 * Creates the indexes this slice depends on.
 *
 * <p>Auto-index creation is switched off deliberately: an application that quietly
 * builds indexes on every startup can lock a production collection at an
 * inconvenient moment. The cost of that decision is that {@code @Indexed}
 * annotations are inert, so the two uniqueness rules that carry real weight are
 * declared here instead.
 *
 * <p>Those rules are ones the design cannot work without. Without a unique index
 * on {@code googleId}, two concurrent first-time sign-ins for the same Google
 * identity can both pass the "does this account exist" check and both insert,
 * leaving two rows for one person. Without a unique index on {@code email}, the
 * deliberate refusal to merge a re-used address across different {@code sub} values
 * would be a race rather than a rule.
 *
 * <p>Index creation is idempotent, so running this on every start is safe. A
 * failure is logged at error level and swallowed, because a missing index must not
 * stop the application from serving sign-in and the application-level checks still
 * refuse a duplicate insert. The residual risk is the concurrent sign-in race
 * above, which is why the failure is not logged quietly.
 */
@Component
public class MongoIndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(MongoIndexInitializer.class);

    private final MongoTemplate mongoTemplate;
    private final boolean enabled;

    public MongoIndexInitializer(MongoTemplate mongoTemplate,
                                 @Value("${app.mongodb.ensure-indexes:true}") boolean enabled) {
        this.mongoTemplate = mongoTemplate;
        this.enabled = enabled;
    }

    /**
     * Public so an integration test can drive the real index bootstrap against a
     * throwaway database instead of restating the index definitions.
     */
    @PostConstruct
    public void createIndexes() {
        // Off in tests, where no MongoDB is reachable and a connection attempt
        // would cost seconds per suite run for no assertion.
        if (!enabled) {
            log.debug("Index creation is disabled for this profile");
            return;
        }
        ensureUniqueUserIndexes();
        ensureVerificationApplicationIndex();
        ensureProfileIndexes();
        ensureQuestionIndexes();
        ensureAnswerIndexes();
        ensureMentorshipIndexes();
    }

    private void ensureUniqueUserIndexes() {
        var users = mongoTemplate.indexOps(mongoTemplate.getCollectionName(User.class));
        try {
            users.ensureIndex(new Index().on("googleId", Sort.Direction.ASC)
                    .unique().named("uk_users_google_id"));
            users.ensureIndex(new Index().on("email", Sort.Direction.ASC)
                    .unique().named("uk_users_email"));
            log.info("Ensured unique indexes on users.googleId and users.email");
        } catch (RuntimeException ex) {
            log.error("Could not create the unique indexes on the users collection. "
                    + "Concurrent first-time sign-ins for one Google identity could duplicate an account.", ex);
        }
    }

    /**
     * Serves {@code findFirstByUserIdOrderBySubmittedAtDesc}, the query behind the
     * status screen and the attempt counter, and the administrator review queue's
     * paged status filter.
     */
    private void ensureVerificationApplicationIndex() {
        try {
            mongoTemplate.indexOps(mongoTemplate.getCollectionName(VerificationApplication.class))
                    .ensureIndex(new Index().on("userId", Sort.Direction.ASC)
                            .on("submittedAt", Sort.Direction.DESC)
                            .named("ix_verification_applications_user_recent"));
            mongoTemplate.indexOps(mongoTemplate.getCollectionName(VerificationApplication.class))
                    .ensureIndex(new Index().on("status", Sort.Direction.ASC)
                            .on("submittedAt", Sort.Direction.ASC)
                            .named("ix_verification_applications_status_queue"));
            log.info("Ensured status-screen and review-queue indexes on verificationApplications");
        } catch (RuntimeException ex) {
            log.error("Could not create the status-screen and review-queue indexes on verificationApplications", ex);
        }
    }

    private void ensureProfileIndexes() {
        try {
            mongoTemplate.indexOps(mongoTemplate.getCollectionName(Profile.class))
                    .ensureIndex(new Index().on("userId", Sort.Direction.ASC)
                            .unique().named("uk_profiles_user_id"));
            log.info("Ensured unique index on profiles.userId");
        } catch (RuntimeException ex) {
            log.error("Could not create the unique index on profiles.userId", ex);
        }
    }

    private void ensureQuestionIndexes() {
        try {
            mongoTemplate.indexOps(mongoTemplate.getCollectionName(com.ubit.mentorshiphub.question.model.Question.class))
                    .ensureIndex(new Index().on("authorUserId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC).named("ix_questions_author_created"));
            mongoTemplate.indexOps(mongoTemplate.getCollectionName(com.ubit.mentorshiphub.question.model.Question.class))
                    .ensureIndex(new Index().on("status", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC).named("ix_questions_status_created"));
            mongoTemplate.indexOps(mongoTemplate.getCollectionName(com.ubit.mentorshiphub.question.model.Question.class))
                    .ensureIndex(new Index().on("category", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC).named("ix_questions_category_created"));
            mongoTemplate.indexOps(mongoTemplate.getCollectionName(com.ubit.mentorshiphub.question.model.Question.class))
                    .ensureIndex(new Index().on("createdAt", Sort.Direction.DESC).named("ix_questions_created"));
            mongoTemplate.indexOps(mongoTemplate.getCollectionName(com.ubit.mentorshiphub.question.model.Question.class))
                    .ensureIndex(new Index().on("tags", Sort.Direction.ASC).named("ix_questions_tags"));
            mongoTemplate.indexOps(mongoTemplate.getCollectionName(com.ubit.mentorshiphub.question.model.Question.class))
                    .ensureIndex(new Index().on("acceptedAnswerId", Sort.Direction.ASC).named("ix_questions_accepted"));
            log.info("Ensured indexes on questions");
        } catch (RuntimeException ex) {
            log.error("Could not create indexes on questions", ex);
        }
    }

    private void ensureAnswerIndexes() {
        try {
            mongoTemplate.indexOps(mongoTemplate.getCollectionName(com.ubit.mentorshiphub.answer.model.Answer.class))
                    .ensureIndex(new Index().on("questionId", Sort.Direction.ASC).on("createdAt", Sort.Direction.ASC).named("ix_answers_question_created"));
            mongoTemplate.indexOps(mongoTemplate.getCollectionName(com.ubit.mentorshiphub.answer.model.Answer.class))
                    .ensureIndex(new Index().on("questionId", Sort.Direction.ASC).on("status", Sort.Direction.ASC).named("ix_answers_question_status"));
            mongoTemplate.indexOps(mongoTemplate.getCollectionName(com.ubit.mentorshiphub.answer.model.Answer.class))
                    .ensureIndex(new Index().on("authorUserId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC).named("ix_answers_author_created"));
            mongoTemplate.indexOps(mongoTemplate.getCollectionName(com.ubit.mentorshiphub.answer.model.Answer.class))
                    .ensureIndex(new Index().on("status", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC).named("ix_answers_status_created"));
            log.info("Ensured indexes on answers");
        } catch (RuntimeException ex) {
            log.error("Could not create indexes on answers", ex);
        }
    }

    /**
     * The mentorship workflow's two load-bearing rules and its list queries.
     *
     * <p>Two are unique <em>partial</em> indexes, and they are the whole
     * concurrency story for acceptance: a standalone {@code mongod} cannot run
     * multi-document transactions, so "at most one open request per pair" and
     * "at most one active mentorship per pair" are enforced by the database
     * rather than by an {@code exists}-then-{@code insert} check that would race.
     * The request filter covers both non-terminal states, {@code PENDING} and
     * {@code ACCEPTING}, so a pair cannot gain a second open request while one is
     * mid-acceptance. Each filter compares {@code status} against the enum's
     * literal name, which is how Spring Data stores the enum, so the index sees
     * exactly the values the application writes.
     */
    private void ensureMentorshipIndexes() {
        String requests = mongoTemplate.getCollectionName(MentorshipRequest.class);
        String mentorships = mongoTemplate.getCollectionName(Mentorship.class);
        try {
            // Superseded by the open-pair index below, which also covers ACCEPTING.
            // Drop it when present so the collection does not carry a redundant
            // unique index; a fresh database has nothing to drop.
            try {
                mongoTemplate.indexOps(requests).dropIndex("uk_mentorship_requests_pending_pair");
                log.info("Dropped the superseded index uk_mentorship_requests_pending_pair");
            } catch (RuntimeException ignored) {
                // Not present on a fresh database, or already dropped.
            }
            mongoTemplate.indexOps(requests).ensureIndex(new Index()
                    .on("pairKey", Sort.Direction.ASC)
                    .unique()
                    .partial(PartialIndexFilter.of(Criteria.where("status").in(
                            MentorshipRequestStatus.PENDING.name(),
                            MentorshipRequestStatus.ACCEPTING.name())))
                    .named("uk_mentorship_requests_open_pair"));
            mongoTemplate.indexOps(requests).ensureIndex(new Index()
                    .on("requesterUserId", Sort.Direction.ASC)
                    .on("createdAt", Sort.Direction.DESC)
                    .named("ix_mentorship_requests_requester_created"));
            mongoTemplate.indexOps(requests).ensureIndex(new Index()
                    .on("recipientUserId", Sort.Direction.ASC)
                    .on("createdAt", Sort.Direction.DESC)
                    .named("ix_mentorship_requests_recipient_created"));

            mongoTemplate.indexOps(mentorships).ensureIndex(new Index()
                    .on("pairKey", Sort.Direction.ASC)
                    .unique()
                    .partial(PartialIndexFilter.of(Criteria.where("status")
                            .is(MentorshipStatus.ACTIVE.name())))
                    .named("uk_mentorships_active_pair"));
            mongoTemplate.indexOps(mentorships).ensureIndex(new Index()
                    .on("mentorUserId", Sort.Direction.ASC)
                    .on("status", Sort.Direction.ASC)
                    .named("ix_mentorships_mentor_status"));
            mongoTemplate.indexOps(mentorships).ensureIndex(new Index()
                    .on("menteeUserId", Sort.Direction.ASC)
                    .on("status", Sort.Direction.ASC)
                    .named("ix_mentorships_mentee_status"));
            log.info("Ensured indexes on mentorship_requests and mentorships");
        } catch (RuntimeException ex) {
            log.error("Could not create the mentorship indexes. Without the unique partial indexes "
                    + "on pairKey, a pair could hold two open requests or two active mentorships.", ex);
        }
    }
}
