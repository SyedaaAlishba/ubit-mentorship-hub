package com.ubit.mentorshiphub.support;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.SessionRepository;

/**
 * Gives tests an in-memory session repository.
 *
 * <p>Necessary because Spring Boot's {@code MongoSessionConfiguration} is gated
 * only on {@code @ConditionalOnMissingBean(SessionRepository.class)} and on the
 * module being on the classpath - not on {@code spring.session.store-type}. So
 * with spring-session-data-mongodb present, the MongoDB session repository is
 * always the default, and it creates its indexes eagerly at startup, which
 * needs a live server. Declaring a repository here makes the MongoDB
 * configuration back off, so the test context starts with no MongoDB running.
 *
 * <p>Production deliberately keeps the MongoDB store: a server-side session is
 * what lets an administrator revoke a disabled user's sessions.
 */
@TestConfiguration(proxyBeanMethods = false)
public class InMemorySessionTestConfiguration {

    @Bean
    @SuppressWarnings("rawtypes")
    SessionRepository sessionRepository() {
        return new MapSessionRepository(new ConcurrentHashMap<>());
    }
}
