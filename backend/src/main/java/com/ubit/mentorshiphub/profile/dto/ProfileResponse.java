package com.ubit.mentorshiphub.profile.dto;

import java.time.Instant;
import java.util.List;

import com.ubit.mentorshiphub.profile.model.PreferredContact;
import com.ubit.mentorshiphub.profile.model.Visibility;
import com.ubit.mentorshiphub.user.model.AppRole;
import com.ubit.mentorshiphub.user.model.VerificationStatus;

public class ProfileResponse {
    private String id;
    private String userId;
    private String name;
    private String email;
    private AppRole role;
    private VerificationStatus verificationStatus;
    private boolean enabled;
    private boolean onboardingComplete;
    private String program;
    private Integer currentSemester;
    private Integer passoutYear;
    private String bio;
    private List<String> skills;
    private Boolean mentorshipAvailable;
    private String jobTitle;
    private String company;
    private String workArea;
    private List<String> technologies;
    private String interests;
    private String linkedinUrl;
    private String githubUrl;
    private String websiteUrl;
    private String location;
    private Visibility visibility;
    private PreferredContact preferredContact;
    private String avatarGridFsId;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String userId() { return userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String name() { return name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String email() { return email; }
    public AppRole getRole() { return role; }
    public void setRole(AppRole role) { this.role = role; }
    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(VerificationStatus verificationStatus) { this.verificationStatus = verificationStatus; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isOnboardingComplete() { return onboardingComplete; }
    public void setOnboardingComplete(boolean onboardingComplete) { this.onboardingComplete = onboardingComplete; }
    public String getProgram() { return program; }
    public void setProgram(String program) { this.program = program; }
    public Integer getCurrentSemester() { return currentSemester; }
    public void setCurrentSemester(Integer currentSemester) { this.currentSemester = currentSemester; }
    public Integer getPassoutYear() { return passoutYear; }
    public void setPassoutYear(Integer passoutYear) { this.passoutYear = passoutYear; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills; }
    public Boolean getMentorshipAvailable() { return mentorshipAvailable; }
    public void setMentorshipAvailable(Boolean mentorshipAvailable) { this.mentorshipAvailable = mentorshipAvailable; }
    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getWorkArea() { return workArea; }
    public void setWorkArea(String workArea) { this.workArea = workArea; }
    public List<String> getTechnologies() { return technologies; }
    public void setTechnologies(List<String> technologies) { this.technologies = technologies; }
    public String getInterests() { return interests; }
    public void setInterests(String interests) { this.interests = interests; }
    public String getLinkedinUrl() { return linkedinUrl; }
    public void setLinkedinUrl(String linkedinUrl) { this.linkedinUrl = linkedinUrl; }
    public String getGithubUrl() { return githubUrl; }
    public void setGithubUrl(String githubUrl) { this.githubUrl = githubUrl; }
    public String getWebsiteUrl() { return websiteUrl; }
    public void setWebsiteUrl(String websiteUrl) { this.websiteUrl = websiteUrl; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Visibility getVisibility() { return visibility; }
    public void setVisibility(Visibility visibility) { this.visibility = visibility; }
    public PreferredContact getPreferredContact() { return preferredContact; }
    public void setPreferredContact(PreferredContact preferredContact) { this.preferredContact = preferredContact; }
    public String getAvatarGridFsId() { return avatarGridFsId; }
    public void setAvatarGridFsId(String avatarGridFsId) { this.avatarGridFsId = avatarGridFsId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}