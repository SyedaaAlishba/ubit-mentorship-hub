package com.ubit.mentorshiphub.question.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "questions")
public class Question {
    @Id
    private ObjectId id;
    private ObjectId authorUserId;
    private String title;
    private String body;
    private QuestionCategory category;
    private List<String> tags = new ArrayList<>();
    private QuestionStatus status = QuestionStatus.ACTIVE;
    private ObjectId acceptedAnswerId;
    private Instant editedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant moderatedAt;
    private ObjectId moderatedByUserId;
    private String moderationNote;

    public ObjectId getId() { return id; }
    public void setId(ObjectId id) { this.id = id; }
    public ObjectId getAuthorUserId() { return authorUserId; }
    public void setAuthorUserId(ObjectId authorUserId) { this.authorUserId = authorUserId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public QuestionCategory getCategory() { return category; }
    public void setCategory(QuestionCategory category) { this.category = category; }
    public List<String> getTags() { if (tags == null) tags = new ArrayList<>(); return tags; }
    public void setTags(List<String> tags) { this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags); }
    public QuestionStatus getStatus() { return status; }
    public void setStatus(QuestionStatus status) { this.status = status; }
    public ObjectId getAcceptedAnswerId() { return acceptedAnswerId; }
    public void setAcceptedAnswerId(ObjectId acceptedAnswerId) { this.acceptedAnswerId = acceptedAnswerId; }
    public Instant getEditedAt() { return editedAt; }
    public void setEditedAt(Instant editedAt) { this.editedAt = editedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getModeratedAt() { return moderatedAt; }
    public void setModeratedAt(Instant moderatedAt) { this.moderatedAt = moderatedAt; }
    public ObjectId getModeratedByUserId() { return moderatedByUserId; }
    public void setModeratedByUserId(ObjectId moderatedByUserId) { this.moderatedByUserId = moderatedByUserId; }
    public String getModerationNote() { return moderationNote; }
    public void setModerationNote(String moderationNote) { this.moderationNote = moderationNote; }
}
