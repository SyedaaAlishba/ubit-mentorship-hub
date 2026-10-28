package com.ubit.mentorshiphub.answer.model;

import java.time.Instant;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "answers")
public class Answer {
    @Id
    private ObjectId id;
    private ObjectId questionId;
    private ObjectId authorUserId;
    private String body;
    private AnswerStatus status = AnswerStatus.ACTIVE;
    private Instant editedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant moderatedAt;
    private ObjectId moderatedByUserId;
    private String moderationNote;

    public ObjectId getId() { return id; }
    public void setId(ObjectId id) { this.id = id; }
    public ObjectId getQuestionId() { return questionId; }
    public void setQuestionId(ObjectId questionId) { this.questionId = questionId; }
    public ObjectId getAuthorUserId() { return authorUserId; }
    public void setAuthorUserId(ObjectId authorUserId) { this.authorUserId = authorUserId; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public AnswerStatus getStatus() { return status; }
    public void setStatus(AnswerStatus status) { this.status = status; }
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
