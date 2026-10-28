/**
 * Wire types mirroring the backend DTOs.
 *
 * Only what the UI actually reads is declared. The backend is the authority on
 * these values; `AuthState` in particular is the one field the whole app routes
 * on, so its union must match `AuthState` in the backend exactly.
 *
 * New domain types (Profile, Question, Answer, Alumni, Mentorship, Admin) are
 * appended below the original auth/verification types so that existing imports
 * are not disturbed.
 */

/** Mirrors `com.ubit.mentorshiphub.auth.dto.AuthState`. */
export type AuthState =
  | 'NOT_AUTHENTICATED'
  | 'ONBOARDING_REQUIRED'
  | 'VERIFICATION_REQUIRED'
  | 'PENDING_VERIFICATION'
  | 'REJECTED'
  | 'VERIFIED'

/** Mirrors `com.ubit.mentorshiphub.user.model.AppRole`. */
export type AppRole = 'STUDENT' | 'ALUMNI' | 'SENIOR' | 'ADMIN'

/** Mirrors `com.ubit.mentorshiphub.user.model.VerificationStatus`. */
export type VerificationStatus = 'UNVERIFIED' | 'PENDING' | 'VERIFIED' | 'REJECTED'

/**
 * The only roles a client may claim at onboarding. `SENIOR` and `ADMIN` are
 * absent on purpose: they are granted by an administrator, never requested.
 */
export type ClaimableRole = Extract<AppRole, 'STUDENT' | 'ALUMNI'>

/** Mirrors `CurrentUserResponse`. */
export interface CurrentUser {
  state: AuthState
  userId: string | null
  email: string | null
  name: string | null
  role: AppRole | null
  verificationStatus: VerificationStatus | null
  enabled: boolean
  onboardingComplete: boolean
  canSubmitVerification: boolean
  loginUrl: string
  logoutUrl: string
}

/** Mirrors `com.ubit.mentorshiphub.verification.model.Program`. */
export type Program = 'CS' | 'SE' | 'AI'

/** Mirrors `com.ubit.mentorshiphub.verification.model.StudentDocumentType`. */
export type StudentDocumentType = 'STUDENT_CARD' | 'ENROLLMENT_SLIP'

/** Mirrors `com.ubit.mentorshiphub.verification.model.ApplicationStatus`. */
export type ApplicationStatus = 'PENDING' | 'VERIFIED' | 'REJECTED'

/**
 * Mirrors `VerificationApplicationResponse`. `documentId` is intentionally not
 * present: the backend never exposes the GridFS id, and a client that expects
 * one would push someone toward building a document-download endpoint that
 * skips the ownership check.
 */
export interface VerificationApplication {
  applicationId: string
  type: 'STUDENT' | 'ALUMNI'
  status: ApplicationStatus
  claimedRole: string | null
  program: Program | null
  currentSemester: number | null
  documentType: StudentDocumentType | null
  passoutYear: number | null
  linkedInUrl: string | null
  documentFilename: string | null
  attemptNumber: number | null
  submittedAt: string | null
}

/** Mirrors `com.ubit.mentorshiphub.common.api.ApiError`. */
export interface ApiError {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  violations: { field: string; message: string }[]
}

/* ─── Profile ─────────────────────────────────────────────────────────────── */

/** Mirrors `com.ubit.mentorshiphub.profile.model.Visibility`. */
export type Visibility = 'PRIVATE' | 'VERIFIED_ONLY' | 'PUBLIC'

/** Mirrors `com.ubit.mentorshiphub.profile.model.PreferredContact`. */
export type PreferredContact = 'EMAIL' | 'LINKEDIN' | 'GITHUB' | 'WEBSITE'

/** Mirrors `ProfileResponse` — the owner's own profile view (includes email, role etc.). */
export interface ProfileResponse {
  id: string
  userId: string
  name: string
  email: string
  role: AppRole
  verificationStatus: VerificationStatus
  enabled: boolean
  onboardingComplete: boolean
  program: string | null
  currentSemester: number | null
  passoutYear: number | null
  bio: string | null
  skills: string[]
  mentorshipAvailable: boolean | null
  jobTitle: string | null
  company: string | null
  workArea: string | null
  technologies: string[]
  interests: string | null
  linkedinUrl: string | null
  githubUrl: string | null
  websiteUrl: string | null
  location: string | null
  visibility: Visibility | null
  preferredContact: PreferredContact | null
  avatarGridFsId: string | null
  createdAt: string
  updatedAt: string
}

/** Mirrors `ProfilePublicResponse` — the public view of another user's profile. */
export interface ProfilePublicResponse {
  id: string
  userId: string
  name: string
  role: string
  bio: string | null
  skills: string[]
  mentorshipAvailable: boolean | null
  jobTitle: string | null
  company: string | null
  workArea: string | null
  technologies: string[]
  interests: string | null
  linkedinUrl: string | null
  githubUrl: string | null
  websiteUrl: string | null
  location: string | null
  visibility: Visibility | null
  preferredContact: PreferredContact | null
  avatarGridFsId: string | null
}

/** Body sent to PATCH /api/profile/me */
export interface ProfilePatchRequest {
  bio?: string
  skills?: string[]
  mentorshipAvailable?: boolean
  jobTitle?: string
  company?: string
  workArea?: string
  technologies?: string[]
  interests?: string
  linkedinUrl?: string
  githubUrl?: string
  websiteUrl?: string
  location?: string
  visibility?: Visibility
  preferredContact?: PreferredContact
}

/* ─── Questions ───────────────────────────────────────────────────────────── */

/** Mirrors `com.ubit.mentorshiphub.question.model.QuestionCategory`. */
export type QuestionCategory = 'ACADEMIC' | 'CAREER' | 'TECHNICAL' | 'CAMPUS' | 'GENERAL'

/** Mirrors `com.ubit.mentorshiphub.question.model.QuestionStatus`. */
export type QuestionStatus = 'ACTIVE' | 'CLOSED' | 'DELETED'

/** Mirrors `AuthorResponse`. */
export interface AuthorResponse {
  userId: string
  name: string
  role: AppRole
  avatarGridFsId: string | null
}

/** Mirrors `QuestionResponse` and `QuestionDetailResponse` (identical shape). */
export interface QuestionResponse {
  questionId: string
  author: AuthorResponse
  title: string
  body: string
  category: QuestionCategory
  tags: string[]
  status: QuestionStatus
  acceptedAnswerId: string | null
  editedAt: string | null
  createdAt: string
  updatedAt: string
}

/** Spring `Page<T>` wrapper returned by paginated endpoints. */
export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  last: boolean
  first: boolean
}

/* ─── Answers ─────────────────────────────────────────────────────────────── */

/** Mirrors `com.ubit.mentorshiphub.answer.model.AnswerStatus`. */
export type AnswerStatus = 'ACTIVE' | 'DELETED'

/** Mirrors `AnswerResponse`. */
export interface AnswerResponse {
  answerId: string
  questionId: string
  author: AuthorResponse
  body: string
  status: AnswerStatus
  isAccepted: boolean
  editedAt: string | null
  createdAt: string
  updatedAt: string
}

/* ─── Alumni ──────────────────────────────────────────────────────────────── */

/** Mirrors `AlumniProfileResponse`. */
export interface AlumniProfileResponse {
  userId: string
  name: string
  role: AppRole
  program: string | null
  currentSemester: number | null
  passoutYear: number | null
  bio: string | null
  skills: string[]
  interests: string | null
  technologies: string[]
  jobTitle: string | null
  company: string | null
  workArea: string | null
  githubUrl: string | null
  linkedinUrl: string | null
  websiteUrl: string | null
  location: string | null
  preferredContact: string | null
  mentorshipAvailable: boolean
  avatarGridFsId: string | null
}

/* ─── Mentorship ──────────────────────────────────────────────────────────── */

/** Mirrors `MentorshipRequestStatus`. */
export type MentorshipRequestStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED' | 'ACCEPTING'

/** Mirrors `MentorshipStatus`. */
export type MentorshipStatus = 'ACTIVE' | 'ENDED'

/** Mirrors `MentorshipParticipantResponse`. */
export interface MentorshipParticipant {
  userId: string
  name: string
  role: AppRole
  avatarGridFsId: string | null
}

/** Mirrors `MentorshipRequestResponse`. */
export interface MentorshipRequestResponse {
  id: string
  requester: MentorshipParticipant
  recipient: MentorshipParticipant
  message: string
  goal: string | null
  status: MentorshipRequestStatus
  createdAt: string
  updatedAt: string
  respondedAt: string | null
}

/** Mirrors `MentorshipResponse`. */
export interface MentorshipResponse {
  id: string
  mentor: MentorshipParticipant
  mentee: MentorshipParticipant
  requestId: string
  status: MentorshipStatus
  startedAt: string
  endedAt: string | null
  endedByUserId: string | null
  endReason: string | null
  createdAt: string
  updatedAt: string
}

/* ─── Admin ───────────────────────────────────────────────────────────────── */

/** Mirrors `AdminVerificationApplicationResponse`. */
export interface AdminVerificationApplication {
  applicationId: string
  userId: string
  applicantName: string
  applicantEmail: string
  type: 'STUDENT' | 'ALUMNI'
  claimedRole: string
  status: ApplicationStatus
  attemptNumber: number
  submittedAt: string
  program: Program | null
  currentSemester: number | null
  documentType: StudentDocumentType | null
  documentFilename: string | null
  passoutYear: number | null
  linkedInUrl: string | null
  decidedAt: string | null
  reviewerNote: string | null
  documentUrl: string | null
}

/** Decision values the admin may send. */
export type AdminDecision = 'VERIFIED' | 'REJECTED'
