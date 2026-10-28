package com.ubit.mentorshiphub.verification.model;

/**
 * Undergraduate programmes offered at UBIT that a student may claim.
 *
 * <p>Restricted on purpose: the value is compared against the course named on
 * the uploaded student card, so accepting free text would make every comparison
 * a string guess.
 */
public enum Program {
    CS,
    SE,
    AI
}
