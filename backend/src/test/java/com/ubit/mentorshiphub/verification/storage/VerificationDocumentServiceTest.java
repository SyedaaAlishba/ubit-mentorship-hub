package com.ubit.mentorshiphub.verification.storage;

import com.ubit.mentorshiphub.common.exception.DocumentStorageException;
import com.ubit.mentorshiphub.common.exception.InvalidDocumentException;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Upload validation, which runs before a single byte reaches GridFS.
 */
@ExtendWith(MockitoExtension.class)
class VerificationDocumentServiceTest {

    private static final ObjectId USER_ID = new ObjectId();

    @Mock
    private GridFsTemplate gridFsTemplate;

    @Mock
    private MultipartFile upload;

    private VerificationDocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = new VerificationDocumentService(gridFsTemplate);
    }

    @Test
    @DisplayName("a valid PNG is stored and returned with its metadata")
    void storesValidUpload() {
        givenValidUpload("card.png", "image/png");
        when(gridFsTemplate.store(any(InputStream.class), anyString(), anyString(), any()))
                .thenReturn(new ObjectId());

        VerificationDocumentService.StoredDocument stored = documentService.store(USER_ID, upload);

        assertThat(stored.id()).isNotNull();
        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.originalFilename()).isEqualTo("card.png");
    }

    @Test
    @DisplayName("an empty upload is refused")
    void refusesEmptyUpload() {
        when(upload.isEmpty()).thenReturn(true);

        assertThatThrownBy(() -> documentService.store(USER_ID, upload))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("Attach a student card");

        verify(gridFsTemplate, never()).store(any(), any(), any(), any());
    }

    @Test
    @DisplayName("an upload over 5 MB is refused")
    void refusesOversizedUpload() {
        // Only the size is stubbed: the size check is the one under test, and the
        // other accessors are never reached, so stubbing them would be noise.
        when(upload.isEmpty()).thenReturn(false);
        when(upload.getSize()).thenReturn(VerificationDocumentService.MAX_DOCUMENT_BYTES + 1L);

        assertThatThrownBy(() -> documentService.store(USER_ID, upload))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("5 MB or smaller");

        verify(gridFsTemplate, never()).store(any(), any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({
            "image/png,         card.png",
            "image/jpeg,        photo.jpg",
            "image/jpeg,        photo.jpeg",
            "image/webp,        scan.webp",
            "application/pdf,   transcript.pdf"
    })
    @DisplayName("each allowed type is accepted when the extension agrees with it")
    void allowsEverySupportedType(String contentType, String filename) {
        MockMultipartFile file = new MockMultipartFile(
                "document", filename, contentType, new byte[]{1, 2, 3});
        when(gridFsTemplate.store(any(InputStream.class), anyString(), anyString(), any()))
                .thenReturn(new ObjectId());

        assertThatCode(() -> documentService.store(USER_ID, file)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/x-msdownload", "application/zip", "image/svg+xml", "text/html"})
    @DisplayName("a disallowed type is refused, including svg and html")
    void refusesDisallowedTypes(String contentType) {
        MockMultipartFile file = new MockMultipartFile(
                "document", "evidence.bin", contentType, new byte[]{1});

        assertThatThrownBy(() -> documentService.store(USER_ID, file))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("JPG, PNG, WebP or PDF");
    }

    @Test
    @DisplayName("an executable disguise is refused on the extension even with an allowed content type")
    void refusesMislabelledExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "document", "payload.png.exe", "image/png", new byte[]{1});

        assertThatThrownBy(() -> documentService.store(USER_ID, file))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining(".jpg, .jpeg, .png, .webp or .pdf");
    }

    @Test
    @DisplayName("a content type carrying charset parameters is still accepted")
    void acceptsContentTypeWithParameters() {
        MockMultipartFile file = new MockMultipartFile(
                "document", "card.png", "image/png; charset=binary", new byte[]{1, 2, 3});
        when(gridFsTemplate.store(any(InputStream.class), anyString(), anyString(), any()))
                .thenReturn(new ObjectId());

        assertThatCode(() -> documentService.store(USER_ID, file)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a missing content type is refused rather than guessed")
    void refusesUnknownContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "document", "card", null, new byte[]{1, 2, 3});

        assertThatThrownBy(() -> documentService.store(USER_ID, file))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("no detectable type");
    }

    @Test
    @DisplayName("a path in the filename is stripped before storing")
    void sanitizesFilename() {
        MockMultipartFile file = new MockMultipartFile(
                "document", "..\\..\\etc\\passwd.png", "image/png", new byte[]{1, 2, 3});
        when(gridFsTemplate.store(any(InputStream.class), anyString(), anyString(), any()))
                .thenReturn(new ObjectId());

        VerificationDocumentService.StoredDocument stored = documentService.store(USER_ID, file);

        assertThat(stored.originalFilename()).isEqualTo("passwd.png");
    }

    @Test
    @DisplayName("a storage failure is reported as a server-side problem, not bad input")
    void reportsStorageFailureAsServerError() throws IOException {
        givenValidUpload("card.png", "image/png");
        when(gridFsTemplate.store(any(InputStream.class), anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("mongo down"));

        assertThatThrownBy(() -> documentService.store(USER_ID, upload))
                .isInstanceOf(DocumentStorageException.class)
                .hasMessageContaining("could not be stored");
    }

    @Test
    @DisplayName("a failed delete is swallowed so cleanup cannot mask the real error")
    void deleteFailureIsQuiet() {
        org.mockito.Mockito.doThrow(new IllegalStateException("mongo down"))
                .when(gridFsTemplate).delete(any(org.springframework.data.mongodb.core.query.Query.class));

        assertThatCode(() -> documentService.deleteQuietly(new ObjectId())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("deleting a null id is a no-op")
    void deleteNullIsNoOp() {
        assertThatCode(() -> documentService.deleteQuietly(null)).doesNotThrowAnyException();
        verify(gridFsTemplate, never()).delete(any(org.springframework.data.mongodb.core.query.Query.class));
    }

    @Test
    @DisplayName("queries GridFS by _id and deletes with _id query")
    void deletesUsingObjectId() {
        ObjectId docId = new ObjectId();
        documentService.deleteQuietly(docId);

        var queryCaptor = org.mockito.ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.Query.class);
        verify(gridFsTemplate).delete(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getQueryObject().get("_id")).isEqualTo(docId);
    }

    @Test
    @DisplayName("exists queries GridFS by _id")
    void existsQueriesByObjectId() {
        ObjectId docId = new ObjectId();
        when(gridFsTemplate.findOne(any(org.springframework.data.mongodb.core.query.Query.class))).thenReturn(null);

        boolean exists = documentService.exists(docId);
        assertThat(exists).isFalse();

        var queryCaptor = org.mockito.ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.Query.class);
        verify(gridFsTemplate).findOne(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getQueryObject().get("_id")).isEqualTo(docId);
    }

    @Test
    @DisplayName("openForReview queries GridFS by _id")
    void openForReviewQueriesByObjectId() {
        ObjectId docId = new ObjectId();
        when(gridFsTemplate.findOne(any(org.springframework.data.mongodb.core.query.Query.class))).thenReturn(null);

        var result = documentService.openForReview(docId);
        assertThat(result).isEmpty();

        var queryCaptor = org.mockito.ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.Query.class);
        verify(gridFsTemplate).findOne(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getQueryObject().get("_id")).isEqualTo(docId);
    }

    private void givenValidUpload(String filename, String contentType) {
        when(upload.isEmpty()).thenReturn(false);
        when(upload.getSize()).thenReturn(120_000L);
        when(upload.getContentType()).thenReturn(contentType);
        when(upload.getOriginalFilename()).thenReturn(filename);
        try {
            when(upload.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
