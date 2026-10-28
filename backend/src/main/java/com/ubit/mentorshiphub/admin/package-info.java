/**
 * Administrative moderation and management operations.
 *
 * <p>The verification review queue lives here: the endpoints an administrator
 * uses to see pending applications, open a student's uploaded proof, and record
 * an approve or reject decision with a reason. It is deliberately a thin slice
 * over the existing {@code user} and {@code verification} packages - the account
 * status transition still happens through {@code UserService}, and the proof is
 * still stored by {@code VerificationDocumentService} - so the review path adds
 * no second implementation of either rule.
 */
package com.ubit.mentorshiphub.admin;
