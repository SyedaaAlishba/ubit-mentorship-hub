package com.ubit.mentorshiphub.mentorship.model;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * An established mentorship created when a request is accepted.
 *
 * <p>The direction is meaningful here: the request's recipient becomes the
 * {@link #mentorUserId} and the requester becomes the {@link #menteeUserId}.
 * Because the pair is still a single relationship, {@link #pairKey} carries the
 * same canonical, order-independent key as the request so a unique partial index
 * on {@code pairKey} for {@code status = ACTIVE} can guarantee a pair never has
 * two active mentorships at once.
 *
 * <p>{@link #requestId} points back at the accepted request. No User or Profile
 * data is embedded; display fields are resolved at read time.
 */
@Document(collection = "mentorships")
public class Mentorship {

    @Id
    private ObjectId id;

    /** The recipient of the accepted request. */
    private ObjectId mentorUserId;

    /** The requester of the accepted request. */
    private ObjectId menteeUserId;

    /** The accepted request that produced this mentorship. */
    private ObjectId requestId;

    private MentorshipStatus status;

    /** Canonical, order-independent pair identity; see {@link PairKeys}. */
    private String pairKey;

    private Instant startedAt;

    private Instant endedAt;

    /** Who ended the mentorship; null while active. */
    private ObjectId endedByUserId;

    /** Optional free-text reason supplied when the mentorship was ended. */
    private String endReason;

    private Instant createdAt;

    private Instant updatedAt;

    public ObjectId getId() { return id; }
    public void setId(ObjectId id) { this.id = id; }
    public ObjectId getMentorUserId() { return mentorUserId; }
    public void setMentorUserId(ObjectId mentorUserId) { this.mentorUserId = mentorUserId; }
    public ObjectId getMenteeUserId() { return menteeUserId; }
    public void setMenteeUserId(ObjectId menteeUserId) { this.menteeUserId = menteeUserId; }
    public ObjectId getRequestId() { return requestId; }
    public void setRequestId(ObjectId requestId) { this.requestId = requestId; }
    public MentorshipStatus getStatus() { return status; }
    public void setStatus(MentorshipStatus status) { this.status = status; }
    public String getPairKey() { return pairKey; }
    public void setPairKey(String pairKey) { this.pairKey = pairKey; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
    public ObjectId getEndedByUserId() { return endedByUserId; }
    public void setEndedByUserId(ObjectId endedByUserId) { this.endedByUserId = endedByUserId; }
    public String getEndReason() { return endReason; }
    public void setEndReason(String endReason) { this.endReason = endReason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
