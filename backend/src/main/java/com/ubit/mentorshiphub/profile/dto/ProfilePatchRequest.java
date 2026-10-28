package com.ubit.mentorshiphub.profile.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

public class ProfilePatchRequest {
    @Size(max = 500)
    private String bio;

    @Size(max = 20)
    private List<@Size(min = 1, max = 50) String> skills = new ArrayList<>();

    private Boolean mentorshipAvailable;

    @JsonProperty("mentorshipAvailable")
    public Boolean getMentorshipAvailable() {
        return mentorshipAvailable;
    }

    @Size(max = 100)
    private String jobTitle;
    @Size(max = 100)
    private String company;
    @Size(max = 100)
    private String workArea;
    @Size(max = 20)
    private List<@Size(min = 1, max = 50) String> technologies = new ArrayList<>();

    @Size(max = 250)
    private String interests;
    @Size(max = 2048)
    @jakarta.validation.constraints.Pattern(
        regexp = "^$|^https?://(www\\.)?linkedin\\.com/in/[A-Za-z0-9_\\-%.]+/?$",
        message = "Please enter a valid LinkedIn profile URL."
    )
    private String linkedinUrl;
    @Size(max = 2048)
    @jakarta.validation.constraints.Pattern(
        regexp = "^$|^https?://(www\\.)?github\\.com/[A-Za-z0-9_\\-%.]+/?$",
        message = "Please enter a valid GitHub profile URL."
    )
    private String githubUrl;
    @Size(max = 2048)
    private String websiteUrl;
    @Size(max = 100)
    private String location;
    private String visibility;
    private String preferredContact;
    @Size(max = 200)
    private String avatarGridFsId;

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills; }
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
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public String getPreferredContact() { return preferredContact; }
    public void setPreferredContact(String preferredContact) { this.preferredContact = preferredContact; }
    public String getAvatarGridFsId() { return avatarGridFsId; }
    public void setAvatarGridFsId(String avatarGridFsId) { this.avatarGridFsId = avatarGridFsId; }
}
