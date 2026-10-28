package com.ubit.mentorshiphub.alumni.dto;

import java.util.List;

/**
 * The discoverable view of an alumni or senior mentor: the fields another
 * verified participant may see when browsing the directory.
 *
 * <p>This is deliberately a projection, not the accounts profile: it never
 * carries email, google id, verification status, moderation notes or any
 * other database-backed field. Where Profile and VerificationApplication
 * describe the same perceived fact (for example pass-out year), the value in
 * this response follows the latest relevant VerificationApplication, exactly
 * as the profile context does, so the directory and the profile cannot drift.
 */
public record AlumniProfileResponse(
        String userId,
        String name,
        String role,
        String program,
        Integer currentSemester,
        Integer passoutYear,
        String bio,
        List<String> skills,
        String interests,
        List<String> technologies,
        String jobTitle,
        String company,
        String workArea,
        String githubUrl,
        String linkedinUrl,
        String websiteUrl,
        String location,
        String preferredContact,
        boolean mentorshipAvailable,
        String avatarGridFsId
) {
}