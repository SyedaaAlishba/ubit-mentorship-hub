package com.ubit.mentorshiphub.verification.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.mongodb.client.gridfs.model.GridFSFile;
import com.ubit.mentorshiphub.common.exception.DocumentStorageException;
import com.ubit.mentorshiphub.common.exception.InvalidDocumentException;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores uploaded verification documents in MongoDB GridFS, and opens them again
 * for an administrator review.
 *
 * <p>GridFS rather than disk or object storage: the proof a student submits is
 * private, moderately sized, and must share a backup and retention story with
 * the record it belongs to. Keeping it in the same database avoids inventing a
 * second service to secure, expire and audit.
 *
 * <p>Nothing here serves bytes to an ordinary user. Retrieval is an
 * administrator concern reached through the admin review endpoints, so a leaked
 * identifier cannot be turned into a public URL: every read goes through
 * {@link #openForReview}, which the admin service calls only after the
 * {@code ADMIN} gate has passed, and which re-validates the stored content type
 * before the bytes are handed over.
 *
 * <p>Validation happens before a single byte is written. A 5 MB ceiling and a
 * four-type allow-list bound both storage cost and the parser risk of handling
 * attacker-chosen file types, and they keep oversized requests out of GridFS in
 * the first place.
 */
@Component
public class VerificationDocumentService {

    private static final Logger log = LoggerFactory.getLogger(VerificationDocumentService.class);

    /** Hard ceiling on an upload. Mirrored by the multipart limits in application.yml. */
    public static final long MAX_DOCUMENT_BYTES = 5L * 1024 * 1024;

    /** Only formats an administrator can actually open and compare against a register. */
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");

    /** Extension allow-list, checked independently of the declared content type. */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "pdf");

    private final GridFsTemplate gridFsTemplate;

    public VerificationDocumentService(GridFsTemplate verificationGridFsTemplate) {
        this.gridFsTemplate = verificationGridFsTemplate;
    }

    /** The stored file's identity, kept on the owning application. */
    public record StoredDocument(ObjectId id, String originalFilename, String contentType, long sizeBytes) {
    }

    /**
     * A stored document opened for an administrator review.
     *
     * <p>{@code content} is a live GridFS stream: the holder must close it. The
     * other fields are the re-validated metadata the review endpoints put in the
     * response headers, so a controller never has to touch GridFS itself.
     *
     * @param content       open byte stream over the stored file; caller closes it
     * @param contentType   stored content type, re-validated against the allow-list
     * @param filename      sanitized stored filename, safe for a disposition header
     * @param sizeBytes     stored file length
     */
    public record ReviewableDocument(InputStream content, String contentType, String filename, long sizeBytes) {
    }

    /**
     * Validates and stores an upload.
     *
     * @throws InvalidDocumentException if the upload is empty, oversized, of a
     *                                  disallowed type, or mislabelled
     */
    public StoredDocument store(ObjectId userId, MultipartFile file) {
        validate(file);

        String contentType = normalizeContentType(file.getContentType());
        Document metadata = new Document()
                .append("userId", userId)
                .append("purpose", "STUDENT_VERIFICATION")
                .append("contentType", contentType);

        try (InputStream in = file.getInputStream()) {
            ObjectId id = gridFsTemplate.store(in, sanitizeFilename(file.getOriginalFilename()), contentType, metadata);
            return new StoredDocument(id, sanitizeFilename(file.getOriginalFilename()), contentType, file.getSize());
        } catch (IOException ex) {
            throw new DocumentStorageException("The uploaded document could not be read.", ex);
        } catch (RuntimeException ex) {
            throw new DocumentStorageException("The uploaded document could not be stored.", ex);
        }
    }

    /**
     * Best-effort cleanup of an orphaned upload.
     *
     * <p>Called when the application cannot be written after its file was stored.
     * Failures are logged rather than thrown: the caller's request is already
     * failing, and masking that with a cleanup error would hide the real cause.
     */
    public void deleteQuietly(ObjectId documentId) {
        if (documentId == null) {
            return;
        }
        try {
            gridFsTemplate.delete(Query.query(Criteria.where("_id").is(documentId)));
        } catch (RuntimeException ex) {
            log.warn("Could not remove orphaned verification document {}", documentId, ex);
        }
    }

    /** Whether the document exists, for verification before an administrator decision. */
    public boolean exists(ObjectId documentId) {
        if (documentId == null) {
            return false;
        }
        GridFSFile file = gridFsTemplate.findOne(Query.query(Criteria.where("_id").is(documentId)));
        return file != null;
    }

    /**
     * Opens a stored document for an administrator review.
     *
     * <p>The bytes are streamed rather than buffered, so reviewing a 5 MB scan
     * does not put 5 MB on the heap. The content type is re-validated against the
     * upload allow-list rather than trusted from storage: the stored value was
     * checked on the way in, but a document that somehow carries a disallowed
     * type is still never handed to a browser under that type.
     *
     * <p>An empty optional means no such file is stored. The caller decides what
     * that means for the endpoint - the application may simply have no document,
     * as every alumni application does.
     *
     * @return the open document, or empty when GridFS holds no such file
     */
    public Optional<ReviewableDocument> openForReview(ObjectId documentId) {
        if (documentId == null) {
            return Optional.empty();
        }
        GridFSFile file = gridFsTemplate.findOne(Query.query(Criteria.where("_id").is(documentId)));
        if (file == null) {
            return Optional.empty();
        }
        String contentType = safeContentType(file.getMetadata() == null
                ? null : file.getMetadata().getString("contentType"));
        try {
            InputStream content = gridFsTemplate.getResource(file).getInputStream();
            return Optional.of(new ReviewableDocument(content, contentType,
                    file.getFilename() == null ? "verification-document" : file.getFilename(),
                    file.getLength()));
        } catch (IOException ex) {
            throw new DocumentStorageException("The stored document could not be read.", ex);
        }
    }

    /**
     * Whether a content type may be shown inline in a browser tab.
     *
     * <p>Only the raster image types qualify. A PDF is served as an attachment
     * instead: inline rendering of an attacker-supplied PDF exercises the
     * browser's PDF parser in the application's own origin, and a reviewer gains
     * nothing from inline display that a download does not give.
     */
    public static boolean isInlineDisplayable(String contentType) {
        return contentType != null
                && (contentType.equals("image/jpeg")
                || contentType.equals("image/png")
                || contentType.equals("image/webp"));
    }

    /**
     * Re-checks a stored content type, falling back to a non-executable default.
     *
     * <p>Deliberately does not reuse {@link #normalizeContentType}, which rejects
     * a missing type as bad upload input: on the read path a legacy file with no
     * recorded type must still be servable, just never under a type that was not
     * allow-listed on the way in.
     */
    private static String safeContentType(String stored) {
        if (stored == null || stored.isBlank()) {
            return "application/octet-stream";
        }
        String normalized = stored.toLowerCase(Locale.ROOT);
        int parameters = normalized.indexOf(';');
        if (parameters > 0) {
            normalized = normalized.substring(0, parameters);
        }
        normalized = normalized.trim();
        return ALLOWED_CONTENT_TYPES.contains(normalized) ? normalized : "application/octet-stream";
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidDocumentException("Attach a student card or enrolment slip to continue.");
        }
        if (file.getSize() > MAX_DOCUMENT_BYTES) {
            throw new InvalidDocumentException(
                    "The document must be %d MB or smaller."
                            .formatted(MAX_DOCUMENT_BYTES / (1024 * 1024)));
        }
        if (!ALLOWED_CONTENT_TYPES.contains(normalizeContentType(file.getContentType()))) {
            throw new InvalidDocumentException(
                    "Upload a JPG, PNG, WebP or PDF file.");
        }
        if (!ALLOWED_EXTENSIONS.contains(extensionOf(file.getOriginalFilename()))) {
            throw new InvalidDocumentException(
                    "The file name must end in .jpg, .jpeg, .png, .webp or .pdf.");
        }
    }

    /**
     * The declared content type comes from the client, so it is only a hint. The
     * file extension is checked separately, and neither is trusted as proof of
     * what the bytes actually are - the allowed types are all formats that carry
     * no server-side execution risk.
     */
    private static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new InvalidDocumentException("The uploaded file has no detectable type. Upload a JPG, PNG, WebP or PDF.");
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        int parameters = normalized.indexOf(';');
        return (parameters > 0 ? normalized.substring(0, parameters) : normalized).trim();
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Strips path separators so an upload cannot write a misleading stored name. */
    private static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "verification-document";
        }
        String base = filename.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        return base.isEmpty() ? "verification-document" : base.substring(0, Math.min(base.length(), 120));
    }
}
