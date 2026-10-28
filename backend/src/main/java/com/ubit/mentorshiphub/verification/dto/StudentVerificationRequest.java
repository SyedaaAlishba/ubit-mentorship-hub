package com.ubit.mentorshiphub.verification.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import com.ubit.mentorshiphub.verification.model.Program;
import com.ubit.mentorshiphub.verification.model.StudentDocumentType;

/**
 * A submitted student claim, sent as multipart form fields alongside the upload.
 *
 * <p>Deliberately has no user id, no status and no role: ownership comes from the
 * session and both of those are decided server-side, so a crafted request cannot
 * apply on someone else's behalf or approve itself.
 *
 * @param program          the programme claimed, checked against the student card
 * @param currentSemester  semester currently enrolled in
 * @param documentType     whether the upload is a student card or an enrolment slip
 */
public record StudentVerificationRequest(

        @NotNull(message = "Select your programme")
        Program program,

        @NotNull(message = "Select your current semester")
        @Min(value = 1, message = "Semester must be between 1 and 8")
        @Max(value = 8, message = "Semester must be between 1 and 8")
        Integer currentSemester,

        @NotNull(message = "Select which document you are uploading")
        StudentDocumentType documentType
) {

    public static final String PROGRAM_FIELD = "program";
    public static final String SEMESTER_FIELD = "currentSemester";
    public static final String DOCUMENT_TYPE_FIELD = "documentType";
    public static final String FILE_FIELD = "document";
}
