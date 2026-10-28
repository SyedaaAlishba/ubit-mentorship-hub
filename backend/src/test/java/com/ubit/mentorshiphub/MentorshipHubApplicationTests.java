package com.ubit.mentorshiphub;

import com.ubit.mentorshiphub.support.InMemorySessionTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Proves the whole application context wires together: the security chain, the
 * session store, MongoDB mapping and the GridFS bucket all resolve.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(InMemorySessionTestConfiguration.class)
class MentorshipHubApplicationTests {

    @Test
    void contextLoads() {
    }
}
