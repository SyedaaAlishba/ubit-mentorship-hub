package com.ubit.mentorshiphub.alumni.repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bson.Document;

import com.ubit.mentorshiphub.alumni.dto.AlumniDiscoveryQuery;

/**
 * Builds the MongoDB aggregation pipeline that assembles the alumni directory
 * from three collections in a single pass over the database.
 *
 * <p>The stages, in order:
 * <ol>
 *   <li><b>Base match</b> on {@code users}: enabled, verified, and one of the
 *       discoverable roles (ALUMNI, SENIOR). A narrowed {@code role} filter is
 *       folded into this match so an index on
 *       {@code (enabled, verificationStatus, role)} can serve it.</li>
 *   <li><b>Profile lookup/unwind</b> with {@code preserveNullAndEmptyArrays}
 *       false: a user with no profile is not directory visible, mirroring the
 *       profile endpoint's not-found behaviour.</li>
 *   <li><b>Verification lookup/unwind</b>: the newest <b>verified</b>
 *       application for the user, i.e. the approved claim, ordered by
 *       {@code submittedAt} then {@code _id} so the choice is deterministic. It
 *       is unwound with {@code preserveNullAndEmptyArrays} true because a
 *       verified user can be directory visible even without an application.</li>
 *   <li><b>Directory match</b>: visibility (PRIVATE profiles are excluded) plus
 *       every field filter, all dimensions AND-ed together.</li>
 *   <li><b>Facet</b>: the data pipeline sorts by most recent pass-out year,
 *       name, then id and applies skip/limit; the total pipeline counts the
 *       distinct filtered users so the page metadata is computed in the same
 *       query as the page content.</li>
 * </ol>
 *
 * <p>The pipeline is public and pure (no database access) so its exact
 * semantics can be locked down in unit tests against the raw MongoDB
 * documents.
 */
public final class AlumniDiscoveryPipeline {

    /** The roles that may be returned by the directory. */
    public static final List<String> DISCOVERABLE_ROLES = List.of("ALUMNI", "SENIOR");

    private AlumniDiscoveryPipeline() {
    }

    public static List<Document> build(AlumniDiscoveryQuery query) {
        List<Document> stages = new ArrayList<>();
        stages.add(baseMatch(query));
        stages.add(profileLookup());
        stages.add(profileUnwind());
        stages.add(verificationLookup());
        stages.add(verificationUnwind());
        stages.add(directoryMatch(query));
        stages.add(facet(query));
        return stages;
    }

    private static Document baseMatch(AlumniDiscoveryQuery query) {
        Document criteria = new Document("enabled", true)
                .append("verificationStatus", "VERIFIED");
        if (query.role() != null) {
            criteria.append("role", query.role());
        } else {
            criteria.append("role", new Document("$in", DISCOVERABLE_ROLES));
        }
        return new Document("$match", criteria);
    }

    private static Document profileLookup() {
        return new Document("$lookup", new Document()
                .append("from", "profiles")
                .append("localField", "_id")
                .append("foreignField", "userId")
                .append("as", "profile"));
    }

    private static Document profileUnwind() {
        return new Document("$unwind", new Document("path", "$profile")
                .append("preserveNullAndEmptyArrays", false));
    }

    private static Document verificationLookup() {
        List<Document> pipeline = List.of(
                new Document("$match", new Document("$expr",
                        new Document("$eq", List.of("$userId", "$$uid")))
                        // Only the approved application is directory evidence. A
                        // resubmission can leave a newer PENDING or a REJECTED
                        // attempt above the verified one, and showing its claim
                        // would attribute unapproved academic details to the user.
                        .append("status", "VERIFIED")),
                // submittedAt alone is not a total order: two applications
                // submitted in the same instant would make the chosen row (and
                // the program/pass-out year shown) depend on storage order. The
                // _id tiebreaker makes the selection deterministic.
                new Document("$sort", new Document("submittedAt", -1).append("_id", -1)),
                new Document("$limit", 1));
        return new Document("$lookup", new Document()
                .append("from", "verification_applications")
                .append("let", new Document("uid", "$_id"))
                .append("pipeline", pipeline)
                .append("as", "verification"));
    }

    private static Document verificationUnwind() {
        return new Document("$unwind", new Document("path", "$verification")
                .append("preserveNullAndEmptyArrays", true));
    }

    private static Document directoryMatch(AlumniDiscoveryQuery query) {
        List<Document> criteria = new ArrayList<>();
        criteria.add(new Document("profile.visibility",
                new Document("$in", Arrays.asList("PUBLIC", "VERIFIED_ONLY", null))));
        if (query.program() != null) {
            criteria.add(new Document("verification.program", query.program()));
        }
        if (query.passoutYear() != null) {
            criteria.add(new Document("verification.passoutYear", query.passoutYear()));
        }
        if (!query.skills().isEmpty()) {
            criteria.add(anyTokenMatch("profile.skills", query.skills()));
        }
        if (!query.technologies().isEmpty()) {
            criteria.add(anyTokenMatch("profile.technologies", query.technologies()));
        }
        if (query.company() != null) {
            criteria.add(containsMatch("profile.company", query.company()));
        }
        if (query.workArea() != null) {
            criteria.add(containsMatch("profile.workArea", query.workArea()));
        }
        if (query.jobTitle() != null) {
            criteria.add(containsMatch("profile.jobTitle", query.jobTitle()));
        }
        if (query.name() != null) {
            criteria.add(containsMatch("name", query.name()));
        }
        if (query.mentorshipAvailable() != null) {
            if (query.mentorshipAvailable()) {
                criteria.add(new Document("profile.mentorshipAvailable", true));
            } else {
                criteria.add(new Document("profile.mentorshipAvailable",
                        new Document("$in", Arrays.asList(false, null))));
            }
        }
        return new Document("$match", new Document("$and", criteria));
    }

    /**
     * ANY semantics for the multi-value token filters: the requested tokens are
     * OR-ed together, each matched case-insensitively against a whole array
     * element.
     */
    private static Document anyTokenMatch(String field, List<String> tokens) {
        List<Document> alternatives = new ArrayList<>();
        for (String token : tokens) {
            alternatives.add(new Document(field, new Document("$elemMatch",
                    new Document("$regex", "^" + escapeLiteral(token) + "$")
                            .append("$options", "i"))));
        }
        return tokens.size() == 1 ? alternatives.get(0) : new Document("$or", alternatives);
    }

    /** Case-insensitive substring match on a single text field. */
    private static Document containsMatch(String field, String value) {
        return new Document(field, new Document("$regex", escapeLiteral(value))
                .append("$options", "i"));
    }

    private static Document facet(AlumniDiscoveryQuery query) {
        List<Document> data = List.of(
                new Document("$sort", new Document()
                        .append("verification.passoutYear", -1)
                        .append("name", 1)
                        .append("_id", 1)),
                new Document("$skip", (long) query.page() * query.size()),
                new Document("$limit", query.size()));
        List<Document> total = List.of(new Document("$count", "count"));
        return new Document("$facet", new Document()
                .append("data", data)
                .append("total", total));
    }

    /**
     * Escapes the regex metacharacters so a user-supplied filter value is
     * compared literally rather than interpreted as a pattern.
     */
    static String escapeLiteral(String value) {
        return value.replaceAll("([.\\\\^$*+?()\\[\\]{}|])", "\\\\$1");
    }
}