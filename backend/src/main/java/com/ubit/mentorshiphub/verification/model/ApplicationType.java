package com.ubit.mentorshiphub.verification.model;

/**
 * Which kind of claim an application makes. Fixed at submission and never
 * changed, so a rejected student cannot resubmit as an alumnus of the same
 * account and skip the student document.
 */
public enum ApplicationType {
    STUDENT,
    ALUMNI
}
