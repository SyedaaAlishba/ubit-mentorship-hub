package com.ubit.mentorshiphub.verification.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * A submitted alumnus claim, sent as JSON.
 *
 * <p>The LinkedIn profile is required but is treated as supporting evidence,
 * never as proof: it helps a reviewer recognise the person, while the decision
 * still rests on the university register.
 *
 * @param passoutYear  year of graduation
 * @param linkedInUrl  public LinkedIn profile URL
 */
public record AlumniVerificationRequest(

        @NotNull(message = "Enter the year you passed out")
        @Min(value = 2000, message = "Enter a passout year from 2000 onwards")
        Integer passoutYear,

        @NotBlank(message = "Add your LinkedIn profile URL")
        @Pattern(regexp = "^https://(www\\.)?linkedin\\.com/in/[A-Za-z0-9_%\\-]{3,100}/?$",
                message = "Use your public LinkedIn profile URL, for example https://www.linkedin.com/in/yourname")
        String linkedInUrl
) {

    /**
     * Whether the passout year is one that could actually have happened.
     *
     * <p>Takes the current year as an argument so the rule is testable and does
     * not depend on the machine clock.
     */
    public boolean isPassoutYearPlausible(int currentYear) {
        return this.passoutYear != null && this.passoutYear <= currentYear + 1;
    }
}
