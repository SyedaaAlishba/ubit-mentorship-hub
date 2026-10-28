package com.ubit.mentorshiphub.verification.storage;

import com.mongodb.client.gridfs.GridFSBuckets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

/**
 * Configures the GridFS bucket that holds uploaded verification documents.
 *
 * <p>The bucket is deliberately separate from the collection names used by the
 * repositories, so that a permissive read on any application collection can never
 * surface document bytes by accident.
 *
 * <p>The bucket is resolved lazily through a supplier, so creating this bean does
 * not open a database connection - the application still starts, and tests can
 * substitute their own template, when MongoDB is not reachable.
 */
@Configuration
public class GridFsConfig {

    @Bean
    GridFsTemplate verificationGridFsTemplate(
            MongoDatabaseFactory databaseFactory,
            MongoConverter mongoConverter,
            @Value("${app.verification.gridfs-bucket:verification_documents}") String bucketName) {
        return new GridFsTemplate(mongoConverter,
                () -> GridFSBuckets.create(databaseFactory.getMongoDatabase(), bucketName));
    }
}
