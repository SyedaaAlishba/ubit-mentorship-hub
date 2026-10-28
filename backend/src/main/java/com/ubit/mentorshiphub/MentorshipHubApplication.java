package com.ubit.mentorshiphub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MentorshipHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(MentorshipHubApplication.class, args);
    }

}
