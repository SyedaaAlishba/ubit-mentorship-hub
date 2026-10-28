package com.ubit.mentorshiphub.profile.dto;

import com.ubit.mentorshiphub.profile.model.PreferredContact;
import com.ubit.mentorshiphub.profile.model.Visibility;
import com.ubit.mentorshiphub.user.model.AppRole;
import com.ubit.mentorshiphub.user.model.VerificationStatus;

import java.util.List;

public record ProfileContext(
        String name,
        String email,
        AppRole role,
        VerificationStatus verificationStatus,
        boolean enabled,
        String bio,
        List<String> skills,
        Boolean mentorshipAvailable,
        String jobTitle,
        String company,
        String workArea,
        List<String> technologies,
        String interests,
        String linkedinUrl,
        String githubUrl,
        String websiteUrl,
        String location,
        Visibility visibility,
        PreferredContact preferredContact,
        String avatarGridFsId,
        String program,
        Integer currentSemester,
        Integer passoutYear
) {
}
