package com.ubit.mentorshiphub.alumni.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import com.ubit.mentorshiphub.alumni.dto.AlumniDiscoveryQuery;

/**
 * Executes the alumni discovery aggregation against MongoDB.
 *
 * <p>The query runs as a single {@code $facet} pipeline on the {@code users}
 * collection, so the page content and the matching count (used for
 * {@code totalElements}) are computed by the database in one round trip, after
 * every filter has been applied.
 *
 * <p>The pipeline is issued through the driver's {@code MongoCollection} rather
 * than {@code MongoTemplate.aggregate(...)}. The result of a {@code $facet} is
 * a synthetic document ({@code data}/{@code total}) that corresponds to no
 * entity; asking Spring Data to map it with the {@code users} collection as the
 * type hint makes it bind the nested {@code data} array back onto the
 * {@code User} entity, which then fails when the service reads the safe-field
 * projection. Reading raw {@link Document}s keeps every nested record a plain
 * map and leaves projection entirely to the service.
 */
@Repository
public class AlumniDiscoveryRepository {

    /** The data records on one directory page, plus the filtered total. */
    public record AlumniDiscoveryResult(List<Map<String, Object>> data, long total) {
    }

    private static final String USERS_COLLECTION = "users";

    private final MongoTemplate mongoTemplate;

    public AlumniDiscoveryRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public AlumniDiscoveryResult search(AlumniDiscoveryQuery query) {
        List<Document> pipeline = AlumniDiscoveryPipeline.build(query);
        Document facet = mongoTemplate.getCollection(USERS_COLLECTION).aggregate(pipeline).first();
        if (facet == null) {
            return new AlumniDiscoveryResult(List.of(), 0L);
        }
        return new AlumniDiscoveryResult(facetData(facet.get("data")), totalOf(facet.get("total")));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> facetData(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> data = new ArrayList<>(list.size());
        for (Object element : list) {
            if (element instanceof Map<?, ?> map) {
                data.add((Map<String, Object>) map);
            }
        }
        return data;
    }

    private long totalOf(Object raw) {
        if (!(raw instanceof List<?> counts) || counts.isEmpty()) {
            return 0L;
        }
        Object first = counts.get(0);
        if (!(first instanceof Map<?, ?> map)) {
            return 0L;
        }
        Object count = map.get("count");
        return count instanceof Number number ? number.longValue() : 0L;
    }
}