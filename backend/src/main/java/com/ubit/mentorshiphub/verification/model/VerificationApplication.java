package com.ubit.mentorshiphub.verification.model;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One submitted claim that the account holder is a UBIT student or alumnus.
 *
 * <p>Everything that varies with a resubmission lives here rather than on
 * {@code User}: which programme and semester were claimed, which document was
 * uploaded, what the administrator decided and why. {@code User} therefore needs
 * no programme, semester, passout year, LinkedIn URL or document reference, and
 * no knowledge of GridFS.
 *
 * <p>The application stores only the GridFS id of the uploaded file. The bytes
 * live in the {@code verification_documents} bucket and are never inlined here,
 * so a 5 MB upload does not push this document towards the 16 MB BSON limit and
 * reads of applications stay cheap.
 *
 * <p>Attempts are append-only: a resubmission writes a new document and leaves
 * the previous one untouched, so the decision history is preserved.
 */
@Document(collection = "verification_applications")
public class VerificationApplication {

    @Id
    private ObjectId id;

    /** Owning account. Taken from the session, never from the request body. */
    @Indexed
    private ObjectId userId;

    private ApplicationType type;

    /** The role claimed at onboarding. Preserved so a rejection never rewrites history. */
    private String claimedRole;

    private ApplicationStatus status;

    /** 1 for the first application, incremented on each resubmission. */
    private int attemptNumber;

    /* ---- student claims ---- */

    private Program program;

    private Integer currentSemester;

    private StudentDocumentType documentType;

    /* ---- alumni claims ---- */

    private Integer passoutYear;

    private String linkedInUrl;

    /* ---- upload metadata; the file itself is in GridFS ---- */

    /** GridFS id of the stored document. {@code null} for alumni applications. */
    private ObjectId documentId;

    private String documentOriginalFilename;

    private String documentContentType;

    private long documentSizeBytes;

    /* ---- lifecycle ---- */

    private Instant submittedAt;

    private Instant decidedAt;

    /** Administrator explanation, shown to the applicant. */
    private String reviewerNote;

    /**
     * Account that decided this application. Kept as an id so the review record
     * never carries another account's profile fields, and absent (null) on
     * documents written before this field existed.
     */
    private ObjectId reviewerUserId;

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public ObjectId getUserId() {
        return userId;
    }

    public void setUserId(ObjectId userId) {
        this.userId = userId;
    }

    public ApplicationType getType() {
        return type;
    }

    public void setType(ApplicationType type) {
        this.type = type;
    }

    public String getClaimedRole() {
        return claimedRole;
    }

    public void setClaimedRole(String claimedRole) {
        this.claimedRole = claimedRole;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public Program getProgram() {
        return program;
    }

    public void setProgram(Program program) {
        this.program = program;
    }

    public Integer getCurrentSemester() {
        return currentSemester;
    }

    public void setCurrentSemester(Integer currentSemester) {
        this.currentSemester = currentSemester;
    }

    public StudentDocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(StudentDocumentType documentType) {
        this.documentType = documentType;
    }

    public Integer getPassoutYear() {
        return passoutYear;
    }

    public void setPassoutYear(Integer passoutYear) {
        this.passoutYear = passoutYear;
    }

    public String getLinkedInUrl() {
        return linkedInUrl;
    }

    public void setLinkedInUrl(String linkedInUrl) {
        this.linkedInUrl = linkedInUrl;
    }

    public ObjectId getDocumentId() {
        return documentId;
    }

    public void setDocumentId(ObjectId documentId) {
        this.documentId = documentId;
    }

    public String getDocumentOriginalFilename() {
        return documentOriginalFilename;
    }

    public void setDocumentOriginalFilename(String documentOriginalFilename) {
        this.documentOriginalFilename = documentOriginalFilename;
    }

    public String getDocumentContentType() {
        return documentContentType;
    }

    public void setDocumentContentType(String documentContentType) {
        this.documentContentType = documentContentType;
    }

    public long getDocumentSizeBytes() {
        return documentSizeBytes;
    }

    public void setDocumentSizeBytes(long documentSizeBytes) {
        this.documentSizeBytes = documentSizeBytes;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getReviewerNote() {
        return reviewerNote;
    }

    public void setReviewerNote(String reviewerNote) {
        this.reviewerNote = reviewerNote;
    }

    public ObjectId getReviewerUserId() {
        return reviewerUserId;
    }

    public void setReviewerUserId(ObjectId reviewerUserId) {
        this.reviewerUserId = reviewerUserId;
    }

    /** Whether this application is awaiting a decision. */
    public boolean isPending() {
        return this.status == ApplicationStatus.PENDING;
    }
}
