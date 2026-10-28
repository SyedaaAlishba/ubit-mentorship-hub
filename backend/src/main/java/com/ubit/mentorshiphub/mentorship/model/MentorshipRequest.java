package com.ubit.mentorshiphub.mentorship.model;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A student's invitation to be mentored by an alumnus or senior.
 *
 * <p>Requests and the mentorships they can create live in separate collections:
 * a request is a proposal with its own lifecycle, and only an
 * {@link MentorshipRequestStatus#ACCEPTED} request has a corresponding
 * {@link Mentorship}. Keeping them apart is what lets the "one pending request
 * per pair" rule and the "one active mentorship per pair" rule be enforced by
 * two independent unique indexes rather than by a single shared document.
 *
 * <p>{@link #pairKey} is the canonical, order-independent pair identity built by
 * {@link PairKeys}. A unique partial index on {@code pairKey} for the
 * non-terminal statuses ({@code PENDING} and {@code ACCEPTING}) is the hard
 * guard behind the duplicate rules; the index is created in
 * {@code MongoIndexInitializer}, not by an annotation, since automatic index
 * creation is off.
 *
 * <p>No User or Profile data is embedded: display fields are resolved at read
 * time so the mentorship record cannot drift from the account it points at.
 */
@Document(collection = "mentorship_requests")
public class MentorshipRequest {

    @Id
    private ObjectId id;

    /** The verified student who sent the request. */
    private ObjectId requesterUserId;

    /** The verified alumni or senior the request is addressed to. */
    private ObjectId recipientUserId;

    private String message;

    private String goal;

    private MentorshipRequestStatus status;

    /** Canonical, order-independent pair identity; see {@link PairKeys}. */
    private String pairKey;

    private Instant createdAt;

    private Instant updatedAt;

    /** When the recipient accepted/rejected, or the requester cancelled. */
    private Instant respondedAt;

    /**
     * When the acceptance of this request began, i.e. when it moved to
     * {@link MentorshipRequestStatus#ACCEPTING}. Metadata for recovery; null on
     * every request that was never accepted.
     */
    private Instant acceptStartedAt;

    public ObjectId getId() { return id; }
    public void setId(ObjectId id) { this.id = id; }
    public ObjectId getRequesterUserId() { return requesterUserId; }
    public void setRequesterUserId(ObjectId requesterUserId) { this.requesterUserId = requesterUserId; }
    public ObjectId getRecipientUserId() { return recipientUserId; }
    public void setRecipientUserId(ObjectId recipientUserId) { this.recipientUserId = recipientUserId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getGoal() { return goal; }
    public void setGoal(String goal) { this.goal = goal; }
    public MentorshipRequestStatus getStatus() { return status; }
    public void setStatus(MentorshipRequestStatus status) { this.status = status; }
    public String getPairKey() { return pairKey; }
    public void setPairKey(String pairKey) { this.pairKey = pairKey; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getRespondedAt() { return respondedAt; }
    public void setRespondedAt(Instant respondedAt) { this.respondedAt = respondedAt; }
    public Instant getAcceptStartedAt() { return acceptStartedAt; }
    public void setAcceptStartedAt(Instant acceptStartedAt) { this.acceptStartedAt = acceptStartedAt; }
}
