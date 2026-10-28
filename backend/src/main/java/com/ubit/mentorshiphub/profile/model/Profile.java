package com.ubit.mentorshiphub.profile.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "profiles")
public class Profile {
    @Id
    private ObjectId id;

    @Indexed(unique = true)
    private ObjectId userId;

    private String bio;
    private List<String> skills = new ArrayList<>();

    private Boolean mentorshipAvailable = Boolean.FALSE;
    private String jobTitle;
    private String company;
    private String workArea;
    private List<String> technologies = new ArrayList<>();

    private String interests;
    private String linkedinUrl;
    private String githubUrl;
    private String websiteUrl;
    private String location;
    private Visibility visibility = Visibility.VERIFIED_ONLY;
    private PreferredContact preferredContact = PreferredContact.NONE;

    private String avatarGridFsId;
    private Instant createdAt;
    private Instant updatedAt;

    public ObjectId getId() { return id; }
    public void setId(ObjectId id) { this.id = id; }
    public ObjectId getUserId() { return userId; }
    public void setUserId(ObjectId userId) { this.userId = userId; }
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
