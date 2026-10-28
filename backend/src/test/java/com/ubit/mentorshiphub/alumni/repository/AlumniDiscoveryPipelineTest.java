package com.ubit.mentorshiphub.alumni.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ubit.mentorshiphub.alumni.dto.AlumniDiscoveryQuery;

class AlumniDiscoveryPipelineTest {

    private static final int BASE_MATCH = 0;
    private static final int PROFILE_LOOKUP = 1;
    private static final int PROFILE_UNWIND = 2;
    private static final int VERIFICATION_LOOKUP = 3;
    private static final int VERIFICATION_UNWIND = 4;
    private static final int DIRECTORY_MATCH = 5;
    private static final int FACET = 6;

    private List<Document> build(AlumniDiscoveryQuery query) {
        return AlumniDiscoveryPipeline.build(query);
    }

    /** The value of the single operator attached to a stage. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> operator(Document stage) {
        return (Map<String, Object>) stage.get(stage.keySet().iterator().next());
    }

    private Document baseCriteria(AlumniDiscoveryQuery query) {
        return (Document) operator(build(query).get(BASE_MATCH));
    }

    private Document facetDoc(AlumniDiscoveryQuery query) {
        return (Document) operator(build(query).get(FACET));
    }

    private List<Document> directoryCriteria(AlumniDiscoveryQuery query) {
        Document match = (Document) operator(build(query).get(DIRECTORY_MATCH));
        return (List<Document>) match.get("$and");
    }

    private static AlumniDiscoveryQuery query() {
        return new AlumniDiscoveryQuery(0, 20, null, null, null,
                List.of(), List.of(), null, null, null, null, null);
    }

    @Test
    @DisplayName("only enabled, verified ALUMNI and SENIOR users can appear in the directory")
    void baseMatchKeepsOnlyEligibleUsers() {
        Document criteria = baseCriteria(query());
        assertThat(criteria.get("enabled")).isEqualTo(true);
        assertThat(criteria.get("verificationStatus")).isEqualTo("VERIFIED");
        Document role = (Document) criteria.get("role");
        assertThat(role.get("$in")).isEqualTo(List.of("ALUMNI", "SENIOR"));
    }

    @Test
    @DisplayName("neither STUDENT nor ADMIN is ever a discoverable role")
    void discoverableRolesExcludeStudentsAndAdmins() {
        assertThat(AlumniDiscoveryPipeline.DISCOVERABLE_ROLES)
                .containsExactly("ALUMNI", "SENIOR");
    }

    @Test
    @DisplayName("a role filter narrows the base match instead of adding a second filter")
    void roleFilterIsFoldedIntoBaseMatch() {
        AlumniDiscoveryQuery query = new AlumniDiscoveryQuery(0, 20, "ALUMNI", null, null,
                List.of(), List.of(), null, null, null, null, null);
        Document criteria = baseCriteria(query);
        assertThat(criteria.get("role")).isEqualTo("ALUMNI");
    }

    @Test
    @DisplayName("the profile lookup joins on userId and drops users with no profile")
    void profileLookupExcludesUsersWithoutProfile() {
        List<Document> stages = build(query());
        Document lookup = (Document) operator(stages.get(PROFILE_LOOKUP));
        assertThat(lookup.get("from")).isEqualTo("profiles");
        assertThat(lookup.get("localField")).isEqualTo("_id");
        assertThat(lookup.get("foreignField")).isEqualTo("userId");
        assertThat(lookup.get("as")).isEqualTo("profile");

        Document unwind = (Document) operator(stages.get(PROFILE_UNWIND));
        assertThat(unwind.get("path")).isEqualTo("$profile");
        assertThat(unwind.get("preserveNullAndEmptyArrays")).isEqualTo(false);
    }

    @Test
    @DisplayName("the verification lookup takes the latest verified application only")
    void verificationLookupTakesTheLatestApplication() {
        List<Document> stages = build(query());
        Document lookup = (Document) operator(stages.get(VERIFICATION_LOOKUP));
        assertThat(lookup.get("from")).isEqualTo("verification_applications");
        assertThat(lookup.get("as")).isEqualTo("verification");
        Document let = (Document) lookup.get("let");
        assertThat(let.get("uid")).isEqualTo("$_id");

        @SuppressWarnings("unchecked")
        List<Document> pipeline = (List<Document>) lookup.get("pipeline");
        assertThat(pipeline).hasSize(3);

        Document match = (Document) operator(pipeline.get(0));
        Document expr = (Document) match.get("$expr");
        assertThat(expr.get("$eq")).isEqualTo(List.of("$userId", "$$uid"));
        // Only an approved application is eligible directory evidence.
        assertThat(match.get("status")).isEqualTo("VERIFIED");

        Document sort = (Document) operator(pipeline.get(1));
        assertThat(sort.get("submittedAt")).isEqualTo(-1);
        // Deterministic tiebreaker when two applications share a submittedAt.
        assertThat(sort.get("_id")).isEqualTo(-1);

        Document limit = pipeline.get(2);
        assertThat(limit.get("$limit")).isEqualTo(1);

        Document unwind = (Document) operator(stages.get(VERIFICATION_UNWIND));
        assertThat(unwind.get("path")).isEqualTo("$verification");
        assertThat(unwind.get("preserveNullAndEmptyArrays")).isEqualTo(true);
    }

    @Test
    @DisplayName("only PUBLIC and VERIFIED_ONLY profiles pass the visibility gate")
    void visibilityExcludesPrivateProfiles() {
        List<Document> criteria = directoryCriteria(query());
        assertThat(criteria).hasSize(1);
        Document visibility = (Document) criteria.get(0).get("profile.visibility");
        assertThat(visibility.get("$in")).isEqualTo(Arrays.asList("PUBLIC", "VERIFIED_ONLY", null));
    }

    @Test
    @DisplayName("a program filter checks the latest verification application")
    void programFilterMatchesLatestVerification() {
        AlumniDiscoveryQuery query = new AlumniDiscoveryQuery(0, 20, null, "CS", null,
                List.of(), List.of(), null, null, null, null, null);
        List<Document> criteria = directoryCriteria(query);
        assertThat(criteria).contains(new Document("verification.program", "CS"));
    }

    @Test
    @DisplayName("a pass-out year filter checks the latest verification application")
    void passoutYearFilterMatchesLatestVerification() {
        AlumniDiscoveryQuery query = new AlumniDiscoveryQuery(0, 20, null, null, 2024,
                List.of(), List.of(), null, null, null, null, null);
        List<Document> criteria = directoryCriteria(query);
        assertThat(criteria).contains(new Document("verification.passoutYear", 2024));
    }

    @Test
    @DisplayName("skill tokens are OR-ed, whole-word matched, case-insensitive")
    void skillsFilterIsAnyAcrossTokens() {
        AlumniDiscoveryQuery query = new AlumniDiscoveryQuery(0, 20, null, null, null,
                List.of("java", "spring"), List.of(), null, null, null, null, null);
        List<Document> criteria = directoryCriteria(query);
        Document or = criteria.stream()
                .filter(c -> c.keySet().contains("$or"))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        List<Document> alternatives = (List<Document>) or.get("$or");
        assertThat(alternatives).hasSize(2);
        assertThat(elemMatchRegex(alternatives.get(0), "profile.skills")).isEqualTo("^java$");
        assertThat(elemMatchRegex(alternatives.get(1), "profile.skills")).isEqualTo("^spring$");
    }

    @Test
    @DisplayName("a single skill token matches without a wrapping $or")
    void skillsFilterSingleTokenIsDirect() {
        AlumniDiscoveryQuery query = new AlumniDiscoveryQuery(0, 20, null, null, null,
                List.of("java"), List.of(), null, null, null, null, null);
        List<Document> criteria = directoryCriteria(query);
        assertThat(criteria).anyMatch(c -> c.keySet().contains("profile.skills"));
        assertThat(criteria).noneMatch(c -> c.keySet().contains("$or"));
    }

    @Test
    @DisplayName("skill matching is case-insensitive against a whole array element")
    void skillsFilterUsesAnchoredCaseInsensitiveRegex() {
        AlumniDiscoveryQuery query = new AlumniDiscoveryQuery(0, 20, null, null, null,
                List.of("java"), List.of(), null, null, null, null, null);
        List<Document> criteria = directoryCriteria(query);
        Document skill = criteria.stream()
                .filter(c -> c.keySet().contains("profile.skills"))
                .findFirst()
                .orElseThrow();
        Document elemMatch = (Document) skill.get("profile.skills");
        Document regex = (Document) elemMatch.get("$elemMatch");
        assertThat(regex.get("$regex")).isEqualTo("^java$");
        assertThat(regex.get("$options")).isEqualTo("i");
    }

    @Test
    @DisplayName("technologies filters mirror the skills semantics")
    void technologiesFilterIsAnyAcrossTokens() {
        AlumniDiscoveryQuery query = new AlumniDiscoveryQuery(0, 20, null, null, null,
                List.of(), List.of("mongo", "docker"), null, null, null, null, null);
        List<Document> criteria = directoryCriteria(query);
        Document or = criteria.stream()
                .filter(c -> c.keySet().contains("$or"))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        List<Document> alternatives = (List<Document>) or.get("$or");
        assertThat(alternatives).hasSize(2);
        assertThat(elemMatchRegex(alternatives.get(0), "profile.technologies")).isEqualTo("^mongo$");
        assertThat(elemMatchRegex(alternatives.get(1), "profile.technologies")).isEqualTo("^docker$");
    }

    @Test
    @DisplayName("company, work area and job title filters are case-insensitive substrings")
    void containsFiltersMatchSubstrings() {
        AlumniDiscoveryQuery query = new AlumniDiscoveryQuery(0, 20, null, null, null,
                List.of(), List.of(), "Acme", "Backend", "Engineer", null, null);
        List<Document> criteria = directoryCriteria(query);
        assertThat(containsRegex(criteria, "profile.company")).isEqualTo("Acme");
        assertThat(containsRegex(criteria, "profile.workArea")).isEqualTo("Backend");
        assertThat(containsRegex(criteria, "profile.jobTitle")).isEqualTo("Engineer");
    }

    @Test
    @DisplayName("the name filter matches the user field, case-insensitively")
    void nameFilterMatchesUserName() {
        AlumniDiscoveryQuery query = new AlumniDiscoveryQuery(0, 20, null, null, null,
                List.of(), List.of(), null, null, null, "Ali", null);
        List<Document> criteria = directoryCriteria(query);
        assertThat(containsRegex(criteria, "name")).isEqualTo("Ali");
    }

    @Test
    @DisplayName("user-supplied filter text is compared literally, not as a regex")
    void containsFiltersEscapeRegexMetaCharacters() {
        assertThat(AlumniDiscoveryPipeline.escapeLiteral("java")).isEqualTo("java");
        assertThat(AlumniDiscoveryPipeline.escapeLiteral("C++ & .NET"))
                .isEqualTo("C\\+\\+ & \\.NET");
    }

    @Test
    @DisplayName("mentorshipAvailable=true keeps only explicit flags")
    void mentorshipTrueMatchesExplicitFlag() {
        AlumniDiscoveryQuery query = new AlumniDiscoveryQuery(0, 20, null, null, null,
                List.of(), List.of(), null, null, null, null, true);
        List<Document> criteria = directoryCriteria(query);
        assertThat(criteria).contains(new Document("profile.mentorshipAvailable", true));
    }

    @Test
    @DisplayName("mentorshipAvailable=false keeps missing or false flags")
    void mentorshipFalseIncludesMissingFlags() {
        AlumniDiscoveryQuery query = new AlumniDiscoveryQuery(0, 20, null, null, null,
                List.of(), List.of(), null, null, null, null, false);
        List<Document> criteria = directoryCriteria(query);
        Document mentorship = criteria.stream()
                .filter(c -> c.keySet().contains("profile.mentorshipAvailable"))
                .findFirst()
                .orElseThrow();
        Document in = (Document) mentorship.get("profile.mentorshipAvailable");
        assertThat(in.get("$in")).isEqualTo(Arrays.asList(false, null));
    }

    @Test
    @DisplayName("no mentorship filter is emitted when the parameter is absent")
    void mentorshipNotFilteredWhenAbsent() {
        List<Document> criteria = directoryCriteria(query());
        assertThat(criteria).noneMatch(c -> c.keySet().contains("profile.mentorshipAvailable"));
    }

    @Test
    @DisplayName("the data facet sorts latest pass-out year first, then name, then id")
    void dataFacetSortsByPassoutYearThenNameThenId() {
        Document facet = facetDoc(query());
        @SuppressWarnings("unchecked")
        List<Document> data = (List<Document>) facet.get("data");
        Document sort = (Document) data.get(0).get("$sort");
        assertThat(sort.get("verification.passoutYear")).isEqualTo(-1);
        assertThat(sort.get("name")).isEqualTo(1);
        assertThat(sort.get("_id")).isEqualTo(1);
    }

    @Test
    @DisplayName("data pagination and the total count live in the same facet")
    void dataFacetPaginatesAndTotalFacetCounts() {
        Document facet = facetDoc(query());
        @SuppressWarnings("unchecked")
        List<Document> data = (List<Document>) facet.get("data");
        assertThat(data.get(1).get("$skip")).isEqualTo(0L);
        assertThat(data.get(2).get("$limit")).isEqualTo(20);

        @SuppressWarnings("unchecked")
        List<Document> total = (List<Document>) facet.get("total");
        assertThat(total.get(0).get("$count")).isEqualTo("count");
    }

    @Test
    @DisplayName("skip is derived from page and size before the limit is applied")
    void skipComesFromPageTimesSize() {
        AlumniDiscoveryQuery query = new AlumniDiscoveryQuery(2, 15, null, null, null,
                List.of(), List.of(), null, null, null, null, null);
        Document facet = facetDoc(query);
        @SuppressWarnings("unchecked")
        List<Document> data = (List<Document>) facet.get("data");
        assertThat(data.get(1).get("$skip")).isEqualTo(30L);
        assertThat(data.get(2).get("$limit")).isEqualTo(15);
    }

    @Test
    @DisplayName("skip is computed in long arithmetic so a very large page cannot overflow int")
    void skipUsesLongArithmeticForHugePages() {
        AlumniDiscoveryQuery query = new AlumniDiscoveryQuery(Integer.MAX_VALUE, 50, null, null, null,
                List.of(), List.of(), null, null, null, null, null);
        Document facet = facetDoc(query);
        @SuppressWarnings("unchecked")
        List<Document> data = (List<Document>) facet.get("data");
        assertThat(data.get(1).get("$skip")).isEqualTo(2_147_483_647L * 50L);
        assertThat(data.get(2).get("$limit")).isEqualTo(50);
    }

    @Test
    @DisplayName("the facet is the last stage and nothing is filtered after pagination")
    void facetIsTheFinalStage() {
        List<Document> stages = build(query());
        assertThat(stages).hasSize(7);
        assertThat(stages.get(FACET).keySet()).containsExactly("$facet");
    }

    private static String elemMatchRegex(Document alternative, String field) {
        Document elemMatch = (Document) alternative.get(field);
        Document regex = (Document) elemMatch.get("$elemMatch");
        return (String) regex.get("$regex");
    }

    private static String containsRegex(List<Document> criteria, String field) {
        Document entry = criteria.stream()
                .filter(c -> c.keySet().contains(field))
                .findFirst()
                .orElseThrow();
        Document regex = (Document) entry.get(field);
        assertThat(regex.get("$options")).isEqualTo("i");
        return (String) regex.get("$regex");
    }
}