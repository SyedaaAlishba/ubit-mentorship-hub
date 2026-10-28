package com.ubit.mentorshiphub.verification.model;

/**
 * The proof a student application must carry.
 *
 * <p>Both are common at UBIT and either one is enough, so this is a choice rather
 * than a hierarchy.
 */
public enum StudentDocumentType {

    /** University-issued identity card. */
    STUDENT_CARD,

    /** Class or enrolment slip from the department. */
    ENROLLMENT_SLIP
}
