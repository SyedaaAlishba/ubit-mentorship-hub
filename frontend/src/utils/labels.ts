/**
 * Display labels for account facts and domain enumerations.
 *
 * Kept apart from component files so those files export only components, which
 * is what keeps React Fast Refresh working. A module that mixes components and
 * helpers breaks Fast Refresh.
 *
 * Role and status are labelled separately on purpose: they are independent —
 * one is claimed, the other is decided — and a single combined word would hide
 * the distinction the user needs to see.
 */

import type {
  AppRole,
  MentorshipRequestStatus,
  MentorshipStatus,
  PreferredContact,
  QuestionCategory,
  VerificationStatus,
  Visibility,
} from '@/types/api'

const ROLE_LABELS: Record<AppRole, string> = {
  STUDENT: 'Student',
  ALUMNI: 'Alumnus',
  SENIOR: 'Senior',
  ADMIN: 'Administrator',
}

const STATUS_LABELS: Record<VerificationStatus, string> = {
  UNVERIFIED: 'Not yet applied',
  PENDING: 'Awaiting review',
  VERIFIED: 'Verified',
  REJECTED: 'Rejected',
}

export function roleLabel(role: AppRole | null): string {
  return role ? ROLE_LABELS[role] : 'Not chosen'
}

export function statusLabel(status: VerificationStatus | null): string {
  return status ? STATUS_LABELS[status] : 'Unknown'
}

/* ─── Program labels ──────────────────────────────────────────────────────── */

export const PROGRAM_LABELS: Record<string, string> = {
  CS: 'Computer Science',
  SE: 'Software Engineering',
  AI: 'Artificial Intelligence',
  CE: 'Computer Engineering',
  ME: 'Mechanical Engineering',
  BM: 'Biomedical Engineering',
  BBA: 'Business Administration',
  BS: 'Basic Sciences',
  MS: 'Master of Science',
  PHD: 'PhD',
}

export function programLabel(program: string | null | undefined): string {
  if (!program) return ''
  return PROGRAM_LABELS[program] ?? program
}

/* ─── Question category labels ────────────────────────────────────────────── */

export const CATEGORY_LABELS: Record<QuestionCategory, string> = {
  ACADEMIC: 'Academic',
  CAREER: 'Career',
  TECHNICAL: 'Technical',
  CAMPUS: 'Campus',
  GENERAL: 'General',
}

export function categoryLabel(category: QuestionCategory): string {
  return CATEGORY_LABELS[category] ?? category
}

/* ─── Mentorship status labels ────────────────────────────────────────────── */

export const REQUEST_STATUS_LABELS: Record<MentorshipRequestStatus, string> = {
  PENDING: 'Pending',
  ACCEPTED: 'Accepted',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
  ACCEPTING: 'Processing',
}

export function requestStatusLabel(status: MentorshipRequestStatus): string {
  return REQUEST_STATUS_LABELS[status] ?? status
}

export const MENTORSHIP_STATUS_LABELS: Record<MentorshipStatus, string> = {
  ACTIVE: 'Active',
  ENDED: 'Ended',
}

export function mentorshipStatusLabel(status: MentorshipStatus): string {
  return MENTORSHIP_STATUS_LABELS[status] ?? status
}

/* ─── Visibility labels ───────────────────────────────────────────────────── */

export const VISIBILITY_LABELS: Record<Visibility, string> = {
  PRIVATE: 'Private',
  VERIFIED_ONLY: 'Verified members only',
  PUBLIC: 'Public',
}

export function visibilityLabel(v: Visibility | null | undefined): string {
  if (!v) return 'Not set'
  return VISIBILITY_LABELS[v]
}

/* ─── Preferred contact labels ────────────────────────────────────────────── */

export const CONTACT_LABELS: Record<PreferredContact, string> = {
  EMAIL: 'Email',
  LINKEDIN: 'LinkedIn',
  GITHUB: 'GitHub',
  WEBSITE: 'Website',
}

export function contactLabel(c: PreferredContact | null | undefined): string {
  if (!c) return 'Not specified'
  return CONTACT_LABELS[c]
}
