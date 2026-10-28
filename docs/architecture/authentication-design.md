# Authentication & User Architecture Design

**Status:** Implemented — see [Implementation notes](#implementation-notes) at the end
**Scope:** `auth`, `user`, and the onboarding/verification workflow that depend on them
**Related:** [Root README](../../README.md)

**Revision 2** — updated after product requirements were finalized. Changes from
revision 1: the sign-in policy is now open to any Google account (the university
email-domain restriction and all domain-based auto-granting are **removed**);
onboarding with an explicit Current Student / Alumni choice replaces the automatic
`STUDENT` default; the first-admin bootstrap is now `BOOTSTRAP_ADMIN_EMAIL`; and
Spring Session with MongoDB is approved. See §11 for the full list of what changed
and what is still open.

---

## 0. Verdict on the architecture

**The proposed direction is correct and is now approved:**

Google OAuth 2.0 + Spring Security + **Spring Session backed by MongoDB** +
MongoDB for persistent users.

Session versus JWT, restated:

| Requirement | Session | JWT |
| --- | --- | --- |
| Revoke an admin-disabled account immediately | Yes — delete the session | No — token valid until expiry |
| Role change takes effect immediately | Yes — re-read or invalidate | No — role is baked into the token |
| Fresh `verificationStatus` per request | Yes | Needs a DB read, defeating "stateless" |
| One deployed backend, browser SPA | Natural | Unnecessary complexity |
| CSRF protection required either way | Yes | Yes (cookie JWT) or XSS risk (localStorage JWT) |
| New infrastructure | None — MongoDB is already required | Refresh-token store or revocation list |

JWT only earns its keep with **multiple independent backend services, native
mobile clients, or third-party API consumers**. None of those are in scope. A JWT
would actively work against the "account disabling" and "role change"
requirements, because those become eventual rather than immediate.

**Spring Session with MongoDB is approved** (previously an open question). It is
required for one concrete reason: in-memory Tomcat sessions cannot be addressed
per user, so an admin disabling an account or demoting a role could not revoke
that user's live session — the change would only take effect at expiry. Spring
Session fixes this using infrastructure we already need. Cost: one dependency,
one annotation, and a `sessions` collection that is not domain data and must be
excluded from user-facing collection documentation.

**No Redis, no separate identity service, no microservices, no password
authentication, no JWT.** Confirmed as out of scope.

### 0.1 Identity scope: any Google account

**Any Google account may authenticate. There is no university email-domain
restriction.**

Google authentication establishes exactly one thing: *which Google account is
this person?* It does not establish, imply, or help prove that the person is a
UBIT student or a UBIT alumnus.

Consequences, all deliberate:

- A `gmail.com` account is a first-class user of the platform.
- A university-looking email address is **not** treated as evidence of anything.
  Revision 1 proposed an `@ubit.edu.pk` / `@uok.edu.pk` allowlist and a
  domain-based auto-grant; both are removed.
- `email_verified` from Google is still enforced at login (§4.2-D). That is a
  check on *Google's* assertion about the account, not a claim about UBIT
  affiliation.
- Because sign-in is open, the admin verification workflow (§3) is the only
  thing standing between "has a Google account" and "is a verified member of the
  UBIT community". It therefore carries more weight than in revision 1, and its
  review queue is a genuine moderation surface (§11.C).

---

## 1. User Entity

### 1.1 Decision: separate `users` and `profiles` collections

**Keep them separate.** Unchanged from revision 1.

`users` holds identity and authorization. `profiles` holds everything a human
sees. This is not just tidiness — there are two concrete engineering reasons:

1. **Write-frequency conflict.** `lastLoginAt` changes on every login. If the
   profile (bio, skills, interests, links, preferences) were embedded in the
   same document, every login would rewrite the whole document, and MongoDB
   would have to relocate the sub-document once the 16 MB limit approached.
2. **Public/private boundary.** Profile data is served to anonymous users during
   mentor and alumni discovery. Auth data must never be. A separate collection
   makes accidental leakage through a DTO or query much harder, and lets
   `profiles` be indexed for discovery without bloating the `users` index.

This matches the `user` / `profile` package split already in the repository, so
no existing structure changes.

**Cost to accept:** admin/moderation list views that show "who" did something
need one extra indexed lookup by `userId`. A normal application-side join in
MongoDB, and acceptable.

### 1.2 `users` document

Collection: `users`

```jsonc
{
  "_id":                     "ObjectId",   // also the session principal id
  "googleId":                "string",     // Google's `sub` claim; null until linked
  "email":                   "string",     // lowercased, Google-asserted
  "role":                    null,         // null until onboarding is submitted
  "verificationStatus":      "UNVERIFIED",
  "verificationReviewedAt":  null,         // ISODate
  "enabled":                 true,
  "createdAt":               "ISODate",
  "updatedAt":               "ISODate",
  "lastLoginAt":             null          // ISODate
}
```

| Field | Type | Purpose |
| --- | --- | --- |
| `_id` | ObjectId | Primary key. Referenced by `profiles.userId`, `session` principal, verification applications, and all content documents. |
| `googleId` | String, **unique sparse** | Google's stable `sub` claim. The authoritative provider identity — survives the user changing their Google display name or email. |
| `email` | String, **unique**, lowercased | Google-asserted email. The human-readable handle, the key used to link an account on first login, and the key compared against `BOOTSTRAP_ADMIN_EMAIL`. |
| `role` | Enum, **nullable** | The single input to backend authorization. `null` means *onboarding not yet completed* (§1.2.1). See §2. |
| `verificationStatus` | Enum | How far the platform has got in confirming the claimed status. Defaults to `UNVERIFIED` at first sign-in. See §3. |
| `verificationReviewedAt` | ISODate, nullable | When an admin last made a verification decision. Basic audit trail; reset to `null` when a new application is submitted. |
| `enabled` | boolean | The account-disabling switch. `false` blocks login and invalidates live sessions. |
| `createdAt` | ISODate | Set once at first login. |
| `updatedAt` | ISODate | Maintained on every write. |
| `lastLoginAt` | ISODate, nullable | Updated on every successful login. Useful for "active alumni" filtering later. |

#### 1.2.1 Why `role` is nullable

Onboarding happens *after* authentication, so there is a real window in which a
user is signed in but has chosen nothing. That window needs a representation, and
there are only three options:

| Option | Verdict |
| --- | --- |
| Default `role = STUDENT` | **Rejected.** It grants a role the user never chose and silently pre-commits a claim; a future alumni applicant would have to be *migrated* from a role they never held. |
| A fifth pseudo-role such as `PENDING_ONBOARDING` or `REGISTERING` | **Rejected.** A fake role pollutes the role enum, needs special-casing in the authorities mapper, and tempts code into treating it as a real capability. |
| **`role = null` until onboarding is submitted** | **Adopted.** Fails closed with no new vocabulary. |

`role = null` means:

- The authorities mapper grants **no** `ROLE_*` authority, so method security
  denies every role-gated endpoint. There is no window in which an un-onboarded
  user can act as a student.
- `GET /api/auth/me` reports `role: null`, and the frontend routes to onboarding.
- Only endpoints explicitly marked *authenticated but not role-gated* are
  reachable — which is exactly the read-only browsing and the verification
  application itself (§3.4).

The practical effect is that a user who abandons onboarding holds an account
that can read and can start the verification process, and can do nothing else.
That is the intended behaviour, not an accident.

> This is a design inference from the finalized requirements, not something
> stated explicitly. It is listed for approval in §11.B.1.

### 1.3 Fields deliberately NOT added

| Rejected field | Reason |
| --- | --- |
| `password` / `passwordHash` | Google is the only identity provider. |
| `roles: []` | A single role enum is sufficient for the four defined roles. A collection would be speculative. |
| `authorities` / `permissions: []` | Derived from `role` at runtime by a mapper. Persisting them creates a second source of truth that can drift. |
| `provider` / `providerName` | Only one provider exists. Adding the field now is speculation. |
| `accessToken` / `refreshToken` | Scopes are limited to `openid email profile` and we never call Google APIs on the user's behalf. |
| `emailVerified` | Google returns fresh `email_verified` on every login, so a snapshot is pointless. Enforced at login (§4.2-D) but not stored — and deliberately not stored under a name that could be confused with our own `verificationStatus`. |
| `tokenVersion` / `passwordChangedAt` | Only needed for stateless-token invalidation. Irrelevant with sessions. |
| `universityDomain` / `isUniversityEmail` | **Removed in revision 2.** A university-looking email is not evidence of UBIT affiliation (§0.1). Storing the flag would only invite treating it as proof. |
| `program`, `currentSemester`, `passoutYear`, `linkedInUrl` | These belong to the **verification application**, not the account — they are the *evidence* of a claim, submitted once and reviewed by an admin (§2.5). They are not account attributes. |
| `displayName`, `avatarUrl`, `headline`, `bio`, skills | Belong to `profiles`. Seeded from Google on first login, then user-editable. |
| `deletedAt` (soft delete) | `enabled = false` covers disabling. True deletion is a separate policy — see §11.C. |

### 1.4 `profiles` document (sketch only — out of scope here)

Shown to confirm the boundary, **not** to be implemented in the auth task.

```jsonc
{
  "_id":        "ObjectId",
  "userId":     "ObjectId",   // unique index -> users._id
  "fullName":   "string",     // seeded from Google `name` on first login, user-editable
  "avatarUrl":  "string",     // seeded from Google `picture`, user-overridable
  "headline":   "string",
  "bio":        "string",
  "department": "string",
  "skills":     [],
  "interests":  [],
  "careerGoals":"string",
  "linkedInUrl":"string",
  "githubUrl":  "string",
  "mentorshipPreferences": {},
  "openToMentoring": false,
  "createdAt":  "ISODate",
  "updatedAt":  "ISODate"
}
```

Indexes: unique on `userId`; later, a text index over
`fullName, headline, skills, interests, department` for discovery search.

**Note on overlap with onboarding data.** The student's program and semester, and
the alumnus's passout year, arrive during verification (§2.5) and are stored on
the verification application. The `profile` feature will also want to display
them. Two options: keep them only on the verification application and join at
read time, or copy them onto the profile when verification is approved. Joining
is the purer choice and avoids drift; copying is cheaper to read. This is
recorded in §11.B.7 and must be settled when the `profile` feature is designed —
it is not blocking authentication.

### 1.5 Collections created by this design

| Collection | Owner module | Domain data? |
| --- | --- | --- |
| `users` | `user` | Yes |
| `profiles` | `profile` | Yes |
| `verification_applications` | `verification` | Yes |
| `sessions` | Spring Session | **No** — infrastructure, exclude from data documentation |

---

## 2. Role Model

### 2.1 The four roles

| Role | How it is obtained | High-level responsibility |
| --- | --- | --- |
| `STUDENT` | Self-selected at onboarding, then **admin-approved** | Currently enrolled. Once verified, can ask and answer questions, search for mentors, send mentorship requests, and manage their own profile. **Not discoverable as a mentor.** |
| `ALUMNI` | Self-selected at onboarding, then **admin-approved** | Graduated. Once verified, can participate as a mentor **and** appears in the alumni directory with professional information for networking. |
| `SENIOR` | **Admin only.** Never self-selected, never inferred from semester | Upperclassman. Participates as a mentor and receives mentorship requests. Being a mentor is the defining capability. |
| `ADMIN` | `BOOTSTRAP_ADMIN_EMAIL` bootstrap, then admin-managed | Platform operator. Reviews verification applications, grants and changes roles, disables accounts, moderates content. Not a mentor. |

Two points worth stating explicitly, because revision 1 got them wrong:

- **`SENIOR` is not a signup choice and is not auto-assigned.** In particular, it
  is **not** derived from the current semester. Semester is collected only as
  evidence for the *student* claim; it confers nothing. `SENIOR` is granted by an
  admin, and only after the user already exists.
- **`ADMIN` is never self-selected**, and cannot be requested through any
  application form.

Mentor-capability (`SENIOR`, `ALUMNI`) is **derived from the role**, not stored
as a boolean. Whether a given senior currently wants to be contacted is a
separate profile flag (`openToMentoring`) — availability is personal, capability
is not.

### 2.2 The core mental model

> **Authentication** answers *which Google account is this?*
> **`role`** answers *what role does this account hold?*
> **`verificationStatus`** answers *has the platform approved the student/alumni claim behind that role?*

These three are independent and must never be collapsed. In particular:

- A role is **never** derived from a verification status. There is no
  `VERIFIED_ALUMNI`.
- A verification status is **never** derived from a role.
- A verification decision **never** blocks authentication. A user with
  `PENDING` or `REJECTED` can still sign in, read, and manage their application.

The `role = ALUMNI, verificationStatus = PENDING` combination is the normal state
of every applicant in the queue and must be comfortably representable.

### 2.3 How role is assigned

| Step | Actor | Effect |
| --- | --- | --- |
| 1. First Google sign-in | system | Create `users` with `role = null`, `verificationStatus = UNVERIFIED`, `enabled = true`. |
| 2. Onboarding choice | user | User selects **Current Student** or **Alumni**, submits the required evidence (§2.5). |
| 3. Application submitted | system | Set `role` to the selected role, `verificationStatus = PENDING`, `verificationReviewedAt = null`. |
| 4. Admin review | admin | Approve → `verificationStatus = VERIFIED`, `verificationReviewedAt = now`. Reject → `verificationStatus = REJECTED`, `verificationReviewedAt = now`, **role unchanged**. |
| 5. Later promotion | admin | Admin assigns `SENIOR` (or changes any role). §11.B.6 covers whether this resets verification. |

Two invariants:

- **The user can only ever select `STUDENT` or `ALUMNI`.** `SENIOR` and `ADMIN`
  are unreachable from any user-facing form, endpoint, or request body. This is
  the privilege-escalation boundary, and it must be enforced in the DTOs
  themselves — the accepted type simply has no field for it (§6.1).
- **Step 3 sets the role, not the verification.** The user is *claiming* a role,
  not being *granted* a capability. That is the whole point of keeping the two
  separate.

### 2.4 Post-login onboarding

After the first successful sign-in, a user with `role = null` is routed to
onboarding. Onboarding is one irreversible choice between two paths.

**Path A — Current Student**

| Input | Notes |
| --- | --- |
| Program | `CS` / `SE` / `AI` — a closed enum, not free text |
| Current semester | Stored as claim evidence only; **confers no role and no capability** |
| Verification document | University Student Card **or** Enrollment Slip |

On submission: `role = STUDENT`, `verificationStatus = PENDING`.

**Path B — Alumni**

| Input | Notes |
| --- | --- |
| Passout year | Bounded range, not any integer |
| LinkedIn profile URL | **Supporting evidence, not automatic proof.** Admin judgment still applies |

On submission: `role = ALUMNI`, `verificationStatus = PENDING`.

**Onboarding does not appear in the signup list:**

- `SENIOR` — excluded by requirement; admin-controlled.
- `ADMIN` — excluded by requirement; bootstrap-only.

**Once a choice is submitted, is it changeable?** A user who claims `ALUMNI` and
is rejected may legitimately discover they are still enrolled, or vice versa.
This is a real case and it is not settled by the finalized requirements —
recorded in §11.B.4. The recommendation is to permit a change of claim only while
`REJECTED`, never while `PENDING` (which would let a user swap applications to
game the queue), and never for a `VERIFIED` user without an admin.

### 2.5 The first-admin bootstrap

An admin cannot be created by an admin, and there is no first user to promote.

**Decision: `BOOTSTRAP_ADMIN_EMAIL`.**

| Property | Value |
| --- | --- |
| Configured via | Environment variable `BOOTSTRAP_ADMIN_EMAIL` |
| Default | **empty → bootstrap disabled** |
| Stored in source | **Never.** No default value, no hardcoded address in Java or YAML |
| Comparison | Case-insensitive against the lowercased Google-asserted email |
| Purpose | Establish the initial administrator, and only that |

**Mechanics.** When a `users` record is *created* and the incoming email matches
`BOOTSTRAP_ADMIN_EMAIL`, the new account is created with `role = ADMIN`,
`verificationStatus = VERIFIED`, and onboarding is skipped.

**The match is applied at account creation only, never on every login.** This
matters: if the comparison ran on every login, then leaving the variable set in
the environment would silently re-promote an admin who had been demoted. Applying
it once, at creation, makes the variable inert afterwards.

**Clearing the variable is a required deployment step**, documented in the
runbook. Once the admin account exists, `BOOTSTRAP_ADMIN_EMAIL` should be unset
and normal admin-controlled role management (§2.3 step 5) takes over.

**If the administrator already has a normal account**, the bootstrap will not
fire, because it only applies at creation. Two options: promote via a documented
one-time `mongosh` update, or extend the bootstrap to promote an existing account
once. The safer default is the manual update — it cannot accidentally re-grant
ADMIN. Recorded in §11.B.5.

The bootstrap admin is set to `VERIFIED` so that the §3.4 participation gate
does not constrain the platform operator. An admin is not claiming student or
alumni status. This is a small judgement call, listed in §11.B.5.

---

## 3. Verification Status

### 3.1 State model

| State | Meaning | Entered when |
| --- | --- | --- |
| `UNVERIFIED` | No application has ever been submitted. The account is new and unclaimed. | Default at first Google sign-in. |
| `PENDING` | An application has been submitted and no admin has decided yet. | Onboarding submission (§2.3 step 3). |
| `VERIFIED` | An admin approved the claim. The user's student/alumni status is confirmed by the platform. | Admin approves. |
| `REJECTED` | An admin declined the claim. **The role is not changed.** | Admin declines. |

Transitions: `UNVERIFIED → PENDING → VERIFIED | REJECTED`, and
`REJECTED → PENDING` on resubmission.

The `UNVERIFIED → PENDING` edge is the only user-initiated transition.
`PENDING → VERIFIED | REJECTED` and any change of `role` are admin-only. There is
no user-initiated path out of `PENDING`, and no path from `VERIFIED` back to
`UNVERIFIED`.

### 3.2 Why these four, and not others

- **`UNVERIFIED` is required** because "never applied" is genuinely different
  from "applied and under review". Without it, every brand-new account would
  enter the admin review queue, and the queue would stop being a review queue.
- **`REJECTED` is required** because an admin must be able to say no. Leaving a
  refused claim as `PENDING` forever is wrong, and a boolean `verified` flag
  cannot express it. It also has to be non-terminal, or a legitimate applicant
  who submitted the wrong document could never try again.
- **`NOT_REQUIRED` was considered and rejected.** It would only mean "no
  verification needed", but every account either has a role claim or has no role
  at all — and "no role" is already expressed by `role = null`. It would overlap
  confusingly with `UNVERIFIED`.
- `EXPIRED` / `SUSPENDED` / `UNDER_REVIEW` were rejected as speculative.
  Nothing in scope needs them.

### 3.3 How verification differs from role

| | Role | Verification status |
| --- | --- | --- |
| Answers | "What role does this account hold?" | "Has the platform approved the student/alumni claim?" |
| Set by | The user, at onboarding — then an admin | An admin only |
| When | Once, at onboarding | Changes through the review lifecycle |
| Effect | Which capabilities apply, once verified | Whether *any* participatory capability applies |
| Never | Never encodes verification | Never encodes or implies a role |
| Example | `ALUMNI` + `PENDING` is the normal applicant state | `VERIFIED_ALUMNI` as a single role is forbidden |

### 3.4 What verification actually gates

**Enforced on the backend. Never merely hidden in React.**

**May, before verification approval** (`UNVERIFIED`, `PENDING`, `REJECTED`):

- Browse and read existing questions and answers.
- See ongoing community content.
- Discover and view available seniors, where appropriate.
- Complete or resubmit the verification process.
- View their own verification status and application.
- (Anonymously) browse the public landing surface.

**May not, before verification approval:**

- Ask new questions.
- Reply to questions.
- Send mentorship or community messages — *see the scope question in §11.B.3,
  because "internal real-time messaging" was excluded from the original brief
  and this line may be read two ways.*
- Any other interactive community action.

**Modelling this as two gates** rather than one permission table:

```
  Gate 1 — participation:   verificationStatus == VERIFIED
            │                (false for UNVERIFIED / PENDING / REJECTED)
            ▼
  Gate 2 — capability:      role
            │                STUDENT → ask, answer, request mentorship
            │                SENIOR  → mentor, accept/decline requests
            │                ALUMNI  → mentor + alumni directory
            │                ADMIN   → review, moderate, manage roles
            ▼
        endpoint allowed
```

Gate 1 is uniform: it does not care what the user claims. Gate 2 varies by role.
Because Gate 1 is a single boolean check, the read-only behaviour for
non-verified users is enforced in **one** place rather than being re-stated on
every endpoint — which is what keeps the restriction from being forgotten when a
new feature is added. Detailed per-endpoint permissions remain out of scope by
agreement; this two-gate shape is the framework they will be written into.

Note that this makes `role` largely inert until `VERIFIED`. That is intentional
and consistent with §2.2: a role is a claim, and an unconfirmed claim confers
nothing.

---

## 4. Authentication Flow

### 4.1 Sign-in

```
React                Backend (Spring Security)              Google          MongoDB
  │
  ├─ GET /api/auth/me ──────────────────────────────────────────────────────────────────▶
  │                        no session → 401
  │
  ├─ GET /api/auth/login ─────▶ build AuthorizationRequest
  │                            (state + nonce in the HTTP session)
  │  ◀── 302 ──────────────────────────────────────────────────────────────────────────┤
  │
  ├─ browser follows redirect ─────────────────────────────────────▶ /o/oauth2/v2/auth
  │                                                                  user consents
  │  ◀── 302 ?code=...&state=... ───────────────────────────────────────────────────────┘
  │                            validate state (CSRF on the OAuth hop)
  │                            exchange code for tokens ───────────────────▶
  │  ◀── id_token + userinfo (sub, email, email_verified, name, picture) ──────────────┘
  │
  │                            AuthUserService
  │                              1. reject if email_verified != true
  │                              2. reject if enabled == false
  │                              3. find user by googleId, else by email
  │                              4. create user + empty profile if absent
  │                                 role = null, verificationStatus = UNVERIFIED
  │                                 (role = ADMIN if BOOTSTRAP_ADMIN_EMAIL matched)
  │                              5. update lastLoginAt ───────────────────────▶
  │
  │                            SecurityContext written to the Mongo session store
  │                            authorities = ROLE_<role>   (none when role is null)
  │
  │  ◀── 302 → frontend
  │           ├─ role == null            → /onboarding
  │           └─ role != null            → /
  │
  └─ GET /api/auth/me ──────────────────────────────────────────────────────────────────▶
                           200 { userId, email, role, verificationStatus }
```

### 4.2 Scenarios

**A. First-time user**
1. No `users` document matches `googleId` or `email`.
2. Create `users`: `role = null`, `verificationStatus = UNVERIFIED`, `enabled = true`,
   `createdAt = updatedAt = lastLoginAt = now`.
3. If the email matches `BOOTSTRAP_ADMIN_EMAIL`, set `role = ADMIN`,
   `verificationStatus = VERIFIED` and skip onboarding (§2.5).
4. Create an empty `profiles` document, seeding `fullName` and `avatarUrl` from
   the Google claims so the UI is not blank on first render.
5. Redirect to onboarding. The user selects Current Student or Alumni and
   submits evidence (§2.4). Until they do, Gate 1 and Gate 2 both hold them to
   read-only.

**B. Existing user**
1. Match on `googleId` → use that record. Otherwise match on `email` (§4.2-D).
2. Update `lastLoginAt` and `updatedAt`. **No role and no verification change.**
   A fresh login must never promote, demote, or re-verify anyone — the only
   exception is a bootstrap match, which applies at creation only (§2.5).
3. Rebuild authorities from the stored role. If `role` is `null`, grant none.
4. Route to `/onboarding` if `role` is `null`, otherwise to the app.

**B2. Returning applicant (`PENDING` or `REJECTED`)**
Signs in normally, keeps read-only access, and sees their status. A `PENDING`
user can view but not edit their application; a `REJECTED` user can resubmit.
A `VERIFIED` user's decision is not re-litigated on login.

**C. Logout**
`POST /api/auth/logout` (CSRF-protected) → `SecurityContextLogoutHandler`
invalidates the session, clears the `SecurityContext`, and expires the session
cookie. On the frontend, clear `AuthContext` and the cached CSRF cookie.

This only clears *our* session; the user's Google login stays active, so the
next sign-in may complete without a consent prompt. Fully signing out of Google
as well would mean redirecting to Google's logout endpoint — a deliberate product
decision, not a default. Recorded in §11.B.8.

**D. Google account email already exists in `users`**

Two sub-cases:

- **Existing record has `googleId = null`** (a bootstrap admin, or a legacy
  record). Google has asserted a *verified* email matching the record, so
  linking is safe: set `googleId` and continue. Log the link.
- **Existing record has a different, non-null `googleId`.** Do **not** silently
  overwrite. Two different Google accounts are presenting the same verified
  address — most likely a stale `googleId` after the user deleted and recreated
  their Google account, but possibly something else. Refuse the sign-in, return a
  clear error, and log it for admin review. An admin resolves it explicitly.

Note this is now a **more likely** path than in revision 1, because with sign-in
open to any Google account a person may hold a personal account and a work
account with the same address at different times.

**E. User has been disabled (`enabled = false`)**
Rejected at sign-in inside `AuthUserService`, before any session is created, with
a message that does not leak whether the account exists. Additionally, an admin
disabling an account must invalidate that user's live sessions — possible
because Spring Session is approved, so the session is addressable and
deletable. Two layers, because a stale session is the realistic leak path.

**F. User's role changes**
Because authorities are derived from the database rather than from the client,
`GET /api/auth/me` reflects the change immediately. With Spring Session, the
admin action also **deletes the user's sessions**, so the change is *enforced*
immediately — the user is asked to sign in again and picks up the new role.

This is the concrete payoff of the approved Spring Session decision, and it
matters most for demotion out of `ADMIN` and out of a mentor role — precisely
the cases where staleness is not acceptable. It also means the frontend must
handle a `401` arriving at any time, not just at boot (§9.2).

---

## 5. Session Strategy

### 5.1 How the browser stores it

The cookie holds **only an opaque session id** — no user data, no role, no
claims. The session contents live server-side in the `sessions` collection
(Spring Session, MongoDB), so sessions survive an application restart and are
consistent across more than one backend instance if the deployment later needs
it. Editing the cookie value resolves to no session and is treated as anonymous.

The cookie is renamed from Tomcat's default `JSESSIONID` to `UBITSESSION` for
clarity, since the name is visible to anyone using a browser's dev tools.

### 5.2 Cookie configuration

| Attribute | Value | Reason |
| --- | --- | --- |
| `HttpOnly` | `true` | JavaScript cannot read it, so an XSS bug cannot exfiltrate the session. |
| `Secure` | `true` in production, configurable | Sent over HTTPS only. Must be `false` for plain-HTTP local development. |
| `SameSite` | `Lax` | **Not `Strict`.** The OAuth callback is a top-level cross-site `GET` navigation from Google; a `Strict` cookie would not be sent on it and sign-in would break. `Lax` permits exactly that top-level navigation while still blocking cross-site `POST`, which is the CSRF vector. |
| `Path` | `/` | Sent to the whole app. |
| `Name` | `UBITSESSION` | Cosmetic clarity. |

### 5.3 Session expiration

- **Idle timeout: 60 minutes**, configurable via `server.servlet.session.timeout`.
- **Sliding** — each authenticated request refreshes the window, so an active
  user is not logged out mid-task. Spring Session's MongoDB repository updates
  `lastAccessedTime`, so this comes for free.
- Absolute lifetime is not needed for this scope.

### 5.4 CSRF — mandatory, not optional

Any cookie the browser attaches automatically is a CSRF vector. Because the SPA
and the API share an origin (Vite dev proxy in development, same-origin serving
in production), a malicious page could otherwise issue `POST /api/auth/logout` or
any authenticated write using the user's session cookie.

**Standard double-submit cookie for SPAs:**

- `CookieCsrfTokenRepository.withHttpOnlyFalse()` issues an `XSRF-TOKEN` cookie.
- The frontend reads that cookie and echoes it in the `X-XSRF-TOKEN` header on
  every non-safe method. JavaScript on another origin cannot read the cookie, so
  it cannot forge the header.
- **Known Spring Security 6 pitfall:** with BREACH protection enabled the token
  is rendered lazily, so the first `POST` after a fresh page load can arrive
  without a token. Fix: use the documented SPA `CsrfTokenRequestHandler` (or call
  `setCsrfRequestAttributeName(null)`) and make one `GET` on app boot so the
  cookie exists before any write. Budget time for this — it is the single most
  common source of "why is my SPA getting 403s".
- `/login/oauth2/code/google` is a `GET` and needs no token; Spring handles
  `state` separately as OAuth's own CSRF defence.

### 5.5 CORS

- **Development:** the Vite dev proxy forwards `/api` to the backend, so the
  browser only ever talks to `localhost:5173`. No CORS configuration needed.
- **Production (recommended):** serve the built frontend and the API from the
  **same origin** (Spring serves the static bundle, or a CDN on the same site).
  Cookies stay first-party and CORS disappears entirely.
- **Production (fallback):** if the frontend and API are on genuinely different
  origins, a `CorsConfigurationSource` is required with
  `allowCredentials(true)` and an explicit origin allowlist. Never `*` — it is
  illegal with credentials and defeats the point. It also forces
  `SameSite=None; Secure`, which browsers increasingly restrict, so it is worth
  avoiding.

### 5.6 Why JWT is not needed here

Covered in §0. A single first-party browser client talking to one backend, with
role and account status that must be revocable at will, is the textbook case
*against* JWT. Adding it would mean solving token refresh, rotation, and
revocation — all of which the session model gives for free — in exchange for a
statelessness benefit this application cannot use.

---

## 6. Security Boundaries

Practical rules, ordered by how much they matter here.

1. **Never trust role or verification from the frontend.** The browser may send
   `role` in a request body; the backend must ignore it. Any DTO used for
   self-service updates must not contain `role`, `enabled`, or
   `verificationStatus` — the safest way to prevent mass assignment is for the
   field not to exist on the incoming type. This applies with extra force to the
   verification application DTOs, which set `role` server-side (§2.3 step 3) and
   must therefore not accept a `role` field from the client.
2. **`SENIOR` and `ADMIN` must be unreachable from any user-facing request.** Not
   hidden in the UI — absent from the accepted DTO entirely.
3. **Authorities are derived, never posted.** `AppRoleGrantedAuthoritiesMapper`
   maps `users.role` → `ROLE_STUDENT` etc. at authentication time, server-side
   only. A `null` role grants nothing.
4. **The participation gate is a single backend check.** §3.4's Gate 1 must be
   enforced in one shared place, not restated per endpoint, so a newly added
   feature cannot forget it.
5. **Keep the Google client secret in the backend, always.** `GOOGLE_CLIENT_ID`
   and `GOOGLE_CLIENT_SECRET` come from environment variables and are never sent
   to the browser. The *client ID* is not actually secret — it appears in the
   redirect URL — but there is no reason to expose it to the frontend either.
6. **`BOOTSTRAP_ADMIN_EMAIL` is configuration, not a secret, and never lives in
   source.** Empty by default; applied at account creation only; cleared after
   use (§2.5).
7. **Understand Vite's `VITE_*` rule:** any variable prefixed `VITE_` is inlined
   into the client bundle and readable by anyone. Never name a secret with that
   prefix.
8. **Cookies:** `HttpOnly` always; `Secure` in production; `SameSite=Lax`;
   HTTPS-only in production.
9. **Authorization is enforced on the backend, per endpoint**, via Spring method
   security, defaulting to deny. UI-level hiding of buttons is cosmetic and is
   never a control.
10. **Validate all input** at the API boundary, and scope every query by the
    authenticated user's id to prevent IDOR (`/questions/{id}` must check
    ownership and visibility, not merely that the caller is signed in).
11. **Authentication ≠ authorization ≠ verification.** Being signed in says
    nothing about what an account may do, and being verified says nothing about
    which of the three questions has been answered.
12. **Disabling an account must have teeth:** reject at sign-in *and* delete
    existing sessions.
13. **Least-privilege OAuth scopes:** `openid email profile` only. Never store
    Google access or refresh tokens.
14. **Audit the privileged actions:** sign-in, sign-out, verification decisions,
    role changes including promotion to `SENIOR`, account disabling, and failed
    privileged access — enough to answer "who did this and when".
15. **Don't leak identity through error messages.** Handle "account disabled"
    separately from "credentials invalid" so responses do not become an
    account-enumeration oracle. With open sign-in this matters more, not less.
16. **Rate-limit** `/api/auth/login`, verification submission, and anonymous
    read endpoints before launch.
17. **Verification documents are sensitive.** They are identity evidence and must
    be access-controlled to admins only, never served from a public URL, and
    never logged. See §11.B.2 — the storage decision is still open.

---

## 7. Backend Package Design

Nothing here is created yet. This is the target layout and the reasoning.

```
auth/
├── config/
│   ├── SecurityConfig.java          SecurityFilterChain, oauth2Login(), CSRF,
│   │                                method security, Spring Session wiring
│   ├── AuthProperties.java          @ConfigurationProperties("app.auth")
│   │                                → bootstrapAdminEmail (default empty),
│   │                                  cookie flags
│   ├── SessionConfig.java           @EnableMongoHttpSession
│   └── (CorsConfig.java only if a separate-origin deployment is chosen)
├── security/
│   ├── AuthenticatedUser.java       principal record: (userId, email, role)
│   ├── AppRole.java                 the four-role enum
│   ├── UserRoleGrantedAuthoritiesMapper.java   role → ROLE_*; null → no authority
│   ├── AuthUserService.java         implements OAuth2UserService: the §4
│   │                                upsert/guard logic + bootstrap check
│   └── CurrentUserArgumentResolver.java  injects @AuthenticatedUser into
│                                        controllers without SecurityContextHolder
├── controller/
│   └── AuthController.java          GET /api/auth/login, GET /api/auth/me,
│                                    POST /api/auth/logout — HTTP concerns only
└── dto/
    ├── CurrentUserResponse.java     id, email, role (nullable), verificationStatus
    └── LogoutResponse.java          (or 204 No Content)

user/
├── model/
│   └── User.java                    the @Document from §1.2
├── repository/
│   └── UserRepository.java          Spring Data MongoDB
├── service/
│   └── UserService.java             create/lookup/update; no HTTP types
└── dto/
    ├── AdminUserResponse.java       admin list/detail views
    └── UpdateUserRoleRequest.java   admin-only; may set SENIOR

verification/                        owns the onboarding/verification workflow
├── model/
│   ├── VerificationApplication.java the submitted claim + evidence (§2.4)
│   └── VerificationStatus.java      the §3.1 enum lives in user, not here —
│                                    this package owns applications only
├── repository/
│   └── VerificationApplicationRepository.java
├── service/
│   ├── VerificationApplicationService.java  submit / list / decide
│   └── VerificationDecisionService.java    admin approve/reject, writes back
├── controller/
│   └── VerificationController.java  application submission + own-status
└── dto/
    ├── StudentVerificationRequest.java   program, currentSemester, document
    ├── AlumniVerificationRequest.java    passoutYear, linkedInUrl
    └── MyVerificationResponse.java        status, submittedAt, decision fields

common/exception/
├── GlobalExceptionHandler.java      exists — extend, do not replace
├── AccountDisabledException.java    → 403
├── EmailNotVerifiedException.java   → 403
├── OnboardingRequiredException.java → 403, role == null
└── VerificationRequiredException.java → 403, Gate 1 failed
```

Design notes:

- **`auth` owns no repository and no Mongo document.** It depends on
  `user.service.UserService`, keeping a single owner for the `users` collection
  and honouring Single Responsibility: *auth* decides who is signed in, *user*
  owns the user record.
- **`AppRole` lives in `auth.security`; `VerificationStatus` lives in `user.model`.**
  The role is an authorization input and belongs beside the authorities mapper;
  the status is a persisted field on the user document. The
  `verification` package owns *applications*, not the status enum — otherwise
  reading `users.verificationStatus` would require a cross-package dependency.
- **Two new exceptions make the gates explicit and testable.**
  `OnboardingRequiredException` covers `role == null`;
  `VerificationRequiredException` covers Gate 1. Mapping them to `403` keeps the
  distinction from becoming an implicit, forgotten annotation somewhere.
- **`AuthenticatedUser` is a tiny immutable principal**, not the raw
  `OAuth2User`. The session need not retain Google's tokens, and
  `Authentication.getPrincipal()` stays cheap.
- **`/api/auth/me` re-reads the user from the database** rather than serving
  session-cached values, so role and verification changes are reflected
  immediately and the session is never the source of truth for mutable data.
- `CurrentUserArgumentResolver` keeps controllers clean — they ask for a
  parameter, not for `SecurityContextHolder` plumbing.
- Neither `auth.dto` nor `verification.dto` may contain entity types, per the
  existing layering rule.

---

## 8. API Design

Minimal by design. Nothing implemented yet.

### 8.1 Authentication

| Method | Path | Auth | Purpose | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/api/auth/login` | Public | Start the Google flow | `302` to Google |
| `GET` | `/login/oauth2/code/google` | Public | OAuth callback — Spring's default path, registered in Google Cloud Console | `302` to the frontend |
| `GET` | `/api/auth/me` | Authenticated | Who am I? | `200` `CurrentUserResponse` / `401` |
| `POST` | `/api/auth/logout` | Authenticated + CSRF | End the session | `204 No Content` |

<details>
<summary>Example <code>CurrentUserResponse</code></summary>

```json
{
  "userId": "66f1c2a4e1b2c3d4e5f60718",
  "email": "someone@gmail.com",
  "role": "STUDENT",
  "verificationStatus": "PENDING"
}
```

For an account that has not completed onboarding:

```json
{
  "userId": "66f1c2a4e1b2c3d4e5f60719",
  "email": "someone@gmail.com",
  "role": null,
  "verificationStatus": "UNVERIFIED"
}
```

`role: null` is the frontend's signal to route to onboarding.

Deliberately excludes `googleId`, `enabled`, and the timestamps — the frontend
does not need them, and a smaller surface leaks less. `fullName` and `avatarUrl`
are **not** included; they live on the profile, preserving the §1.1 boundary
(§11.B.7).
</details>

### 8.2 Verification application

Needed to make onboarding work. Proposed, not implemented.

| Method | Path | Auth | Purpose | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/api/verification/me` | Authenticated | Own application and status | `200` `MyVerificationResponse` / `404` if never applied |
| `POST` | `/api/verification/applications/student` | Authenticated, `role == null` or `REJECTED` | Submit a student claim | `201` + status; sets `role = STUDENT`, status `PENDING` |
| `POST` | `/api/verification/applications/alumni` | Authenticated, `role == null` or `REJECTED` | Submit an alumni claim | `201` + status; sets `role = ALUMNI`, status `PENDING` |

Two endpoints rather than one discriminated endpoint, so each request type can be
validated by its own DTO with its own required fields and no conditional
validation. Neither request DTO contains `role` — the role is derived server-side
from the endpoint that was called (§2.3 step 3, §6.1).

**Auth condition note.** Requiring `role == null` blocks a `PENDING` applicant
from swapping applications to game the queue. Whether a `VERIFIED` user may
submit a change of claim is §11.B.4.

### 8.3 Deliberately not in this slice

Register with password, password reset, profile CRUD, admin user management,
admin verification review, and mentor/community actions. Each is its own feature.

**Coming later:** `GET /api/admin/verifications`,
`POST /api/admin/verifications/{id}/decision`, `GET /api/admin/users`,
`PATCH /api/admin/users/{id}/role`, `PATCH /api/admin/users/{id}/status`.

---

## 9. Frontend Authentication State

### 9.1 `AuthContext` + `useAuth()`

One provider, mounted once in `main.tsx`, holding a small state machine:

```ts
type AuthStatus = 'loading' | 'anonymous' | 'onboarding' | 'authenticated'
type AuthState = { status: AuthStatus; user: CurrentUser | null }
```

| Member | Purpose |
| --- | --- |
| `status` | Drives route guards and avoids a "flash of logged-out UI" on boot. |
| `user` | `id`, `email`, `role`, `verificationStatus` — exactly what the API returns. |
| `login()` | `window.location.assign('/api/auth/login')` |
| `logout()` | `POST /api/auth/logout` with the CSRF header, then reset state |
| `refresh()` | Re-run `GET /api/auth/me` |

`'onboarding'` is derived from `user.role === null` rather than being a separate
fetch. It is a distinct status because the onboarding route must be reachable
while every other authenticated route is not.

A second context, `VerificationContext`, tracks the user's application status so
the UI can show "under review" or "rejected — resubmit" banners. It is fed by
`GET /api/verification/me` and kept separate from `AuthContext`, because
verification state is not authentication state (§2.2).

### 9.2 Boot sequence

1. State starts as `{ status: 'loading', user: null }`.
2. On mount, one `GET /api/auth/me` with `credentials: 'include'`.
3. `200` with `role: null` → `onboarding`. `200` with a role → `authenticated`.
   `401` → `anonymous`. Network error → `anonymous` plus a surfaced error, so a
   backend outage is not mistaken for a signed-out user.
4. **Do not store the user in `localStorage`.** The session cookie is the source
   of truth; cached client state goes stale and is forgeable. It would also mean
   re-hydrating a user whose account was disabled since the last visit.
5. **Handle `401` at any time, not only at boot.** Because Spring Session lets an
   admin revoke sessions (§4.2-F), any request can return `401` mid-session. The
   shared HTTP wrapper must drop the app to `anonymous` and route to sign-in.

### 9.3 Login and logout mechanics

- **Login = full-page navigation**, not a popup. `window.location.assign(...)`
  keeps the whole OAuth hop and the `Set-Cookie` on one top-level context.
  Popups are blocked or cookie-restricted in several browsers, which is exactly
  the wrong place to be fragile.
- **Logout = `fetch` POST** with `credentials: 'include'` and the CSRF header,
  then clear context state.
- **Every API call goes through one shared `services/http.ts` wrapper** that
  attaches `credentials: 'include'`, reads the `XSRF-TOKEN` cookie and sets
  `X-XSRF-TOKEN` on non-safe methods, unwraps the backend's `ApiError` shape,
  and emits the `401` → `anonymous` event. One place, so CSRF and error handling
  are never re-implemented per feature.

### 9.4 Route guards and role checks

- `RequireAuth` reads `status` and renders loading, sign-in, onboarding, or
  children as appropriate. A user in `onboarding` status must not reach the app
  shell, and an `authenticated` user must not be pushed back to onboarding.
- Role- and verification-based UI gating is **purely cosmetic**. The backend
  enforces the real rule (§3.4), and the frontend must never treat a hidden
  button as a control.
- Onboarding UI: two paths only. There is no control anywhere for `SENIOR` or
  `ADMIN` — not disabled, not hidden, **absent**. A visible-and-disabled control
  invites the question "why can't I pick that?" and makes the boundary look
  accidental rather than deliberate.

---

## 10. Architecture Diagram

```
┌───────────────────────────────────────────────────────────────────────────────┐
│  BROWSER — React SPA                                                          │
│                                                                               │
│  AuthContext          VerificationContext      services/http.ts              │
│   status: loading|     application status       • credentials: 'include'     │
│     anonymous|         • read-only banner       • X-XSRF-TOKEN on writes     │
│     onboarding|        • resubmit action        • 401 → drop to anonymous    │
│     authenticated                                                          │
│   user: { id, email, role|null, verificationStatus }                         │
│                                                                               │
│  Cookie jar:  UBITSESSION = <opaque id>   HttpOnly · Secure · SameSite=Lax  │
│               XSRF-TOKEN  = <token>        (readable by JS, by design)      │
└───────────────┬──────────────────────────────────┬──────────────────────────┘
                │                                  │
   GET  /api/auth/me                       GET  /api/auth/login
   POST /api/auth/logout                   (cookie attached automatically)
   POST /api/verification/applications/*    │
                │                                  │
                ▼                                  ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│  SPRING BOOT — SecurityFilterChain                                            │
│                                                                               │
│   CsrfFilter ── validate X-XSRF-TOKEN on writes                              │
│   LogoutFilter ── POST /api/auth/logout ──▶ invalidate session, clear cookie  │
│   oauth2Login()                                                                │
│     ├─ /api/auth/login  builds request, stores state+nonce in the session    │
│     └─ /login/oauth2/code/google  ◀── 302 code+state ──────────┐              │
│                                                               │              │
│   SecurityContextHolderFilter ── Mongo session store           │              │
│   AuthorizationFilter (method security)                        │              │
│                       │  GATE 1  verificationStatus == VERIFIED │              │
│                       │          else → 403 VerificationRequired│             │
│                       │  GATE 2  authorities = ROLE_<role>      │              │
│                       │          (null role → none)             │              │
│                       ▼                                       │              │
│   auth.AuthController      ◀── /api/auth/**                    │              │
│   verification.VerificationController ◀── /api/verification/**  │              │
│   <feature>.controller.*   ◀── /api/**                        │              │
│                       │                                       │              │
│                       ▼                                       │              │
│   auth.security.AuthUserService  (OAuth2UserService)           │              │
│     • reject email_verified != true                           │              │
│     • reject enabled == false                                 │              │
│     • link or create user + profile (role = null)             │              │
│     • BOOTSTRAP_ADMIN_EMAIL checked AT CREATION ONLY          │              │
│                       │                                       │              │
│                       ▼                                       │              │
│   user.service.UserService ──▶ user.repository.UserRepository │              │
│   verification.VerificationApplicationService                  │              │
└───────────────────────┬───────────────────────────────────────┼──────────────┘
                        │                                       │
                        ▼                                       ▼
              ┌─────────────────────┐              ┌──────────────────────────┐
              │  MongoDB            │              │  Google Accounts         │
              │                     │              │                          │
              │  users              │              │  /o/oauth2/v2/auth       │
              │   role (nullable)   │              │  /token  (code → tokens) │
              │   verificationStatus│◀─────────────┤  /userinfo              │
              │   enabled           │  sub, email, │                          │
              │   email, googleId   │  email_verif,│  AUTHORIZATION           │
              │                     │  name, pic   │  ~~~~~~                  │
              │  profiles           │              │  establishes IDENTITY    │
              │                     │              │  ONLY — never UBIT       │
              │  verification_apps  │              │  student/alumni status   │
              │  sessions  ◀──┐     │              └──────────────────────────┘
              │               │     │
              │               └─────┴── Spring Session
              └─────────────────────┘
                        ▲
                        │ admin actions
        ┌───────────────┴──────────────────┐
        │  admin  ──▶ role (incl. SENIOR),  │  ← delete sessions on disable/demote
        │           enabled                │
        │  verification ──▶ VERIFIED |      │  ← writes reviewedAt
        │                   REJECTED        │
        └───────────────────────────────────┘

THE THREE SEPARATE QUESTIONS
  Who is this Google account?  ── OAuth ──▶ session cookie ──▶ AuthenticatedUser
  What role does it hold?      ── users.role (set at onboarding) ──▶ Gate 2
  Is the claim approved?       ── users.verificationStatus ──▶ Gate 1 + badges
                                (never blocks sign-in, never creates a role)
```

---

## 11. Decisions & Open Questions

### A. Finalized decisions

Approved by the product requirements and reflected above.

| # | Decision | Rationale |
| --- | --- | --- |
| 1 | Google OAuth 2.0 authorization-code flow via `oauth2Login()` | Standard, minimal code, no custom token handling |
| 2 | Server-side HTTP session, **not** JWT | Revocable, always-fresh role, no extra infra (§0) |
| 3 | **Spring Session backed by MongoDB** | Required so an admin can revoke a specific user's session on disable or demotion (§4.2-E/F) |
| 4 | **Any Google account may sign in; no email-domain restriction** | §0.1. University email is not treated as evidence of anything |
| 5 | Separate `users` and `profiles` collections | Write-frequency and public/private separation (§1.1) |
| 6 | Single `role` enum, nullable, no roles/permissions collection | Four roles; authorities derived, never stored |
| 7 | **Post-login onboarding** choosing Current Student or Alumni | Replaces the automatic `STUDENT` default of revision 1 |
| 8 | `SENIOR` is admin-only — never a signup choice, never inferred from semester | Semester is evidence for a student claim, not a capability grant |
| 9 | `verificationStatus` ∈ {`UNVERIFIED`, `PENDING`, `VERIFIED`, `REJECTED`} | Expresses the real states without a composite role (§3) |
| 10 | Verification never blocks sign-in | Keeps the three concepts genuinely independent |
| 11 | Non-verified users are read-only, enforced on the backend | §3.4, as two gates rather than a per-endpoint permission table |
| 12 | `BOOTSTRAP_ADMIN_EMAIL`: env-driven, empty by default, applied at account creation only | Establishes the initial admin without a permanent backdoor (§2.5) |
| 13 | `auth` owns no repository; depends on `user` service | One owner for the `users` collection; SRP |
| 14 | Tiny `AuthenticatedUser` principal; `/me` re-reads from DB | Session holds no Google tokens; never stale |
| 15 | CSRF via `CookieCsrfTokenRepository` + `X-XSRF-TOKEN` | Mandatory with cookies; standard SPA pattern |
| 16 | `SameSite=Lax`, full-page-redirect sign-in | The only value that survives the Google callback and blocks CSRF |
| 17 | 60-minute sliding idle timeout | Reasonable for a low-stakes campus tool |
| 18 | Same-origin production deployment | Avoids CORS and third-party-cookie restrictions |
| 19 | No JWT, no Redis, no identity service, no microservices, no password auth | Out of scope |

### B. Decisions still requiring approval

Only items that materially change the code. Each is a genuine gap in the finalized
requirements, not a restatement of a settled point.

| # | Question | Recommendation | Why it matters |
| --- | --- | --- | --- |
| 1 | **What is `role` between sign-in and onboarding completion?** The requirements set `role` at submission, which leaves a window unaccounted for. | **`role = null`**, granting no authority, failing closed (§1.2.1) | Determines the `User` document's nullability, the mapper's null branch, and whether `/me` can return a null role. Changes the first thing written. |
| 2 | **How is the verification document stored?** "Student Card or Enrollment Slip" implies a file upload, and no storage decision was made. | **GridFS inside MongoDB** (image or PDF, size-capped), admin-only access, never served from a public URL | This is a whole sub-feature: multipart handling, size limits, content-type validation, retention/deletion policy, and admin review access. GridFS avoids new infrastructure. Storing only a text reference and having the admin verify out-of-band is a legitimate smaller alternative. **Blocks the verification module.** |
| 3 | **What does "send mentorship/community messages" mean?** Internal real-time messaging was excluded from the original scope, but this line reads as if messaging is in scope. | **Mentorship requests and existing interaction only** — no chat, no inbox | If it means chat, that is a new feature contradicting the stated exclusions and needs a scope decision. If it means mentorship requests, Gate 1 already covers it. |
| 4 | **Can a user change their claim (student ↔ alumni), and what is `role` after `REJECTED`?** | `role` is **kept** on rejection (§2.3 step 4). Resubmission allowed from `REJECTED` only — never from `PENDING` (queue gaming) and never by a `VERIFIED` user without an admin | Determines the allowed-state machine of the submission endpoint and the review queue's integrity. |
| 5 | **Bootstrap admin details.** If the administrator already has a normal account the bootstrap will not fire, and the bootstrap admin is set to `VERIFIED` so Gate 1 does not constrain them. | Promote the existing account via a documented one-time `mongosh` update; keep the bootstrap creation-only; bootstrap admin gets `VERIFIED` | The first is the only safe way to avoid an env var that re-promotes a demoted admin. The second avoids an operator locked out of participatory features. |
| 6 | **Does an admin promotion to `SENIOR` reset `verificationStatus` to `PENDING`?** | **No — leave it `VERIFIED`**, and record the promotion in the audit log | A verified student promoted to mentor is a higher trust level, not a new claim needing evidence. But if `SENIOR` should be independently evidenced, reset instead. Affects the role-update endpoint. |
| 7 | **Do program/semester/passout year live on the verification application, the profile, or both?** And should `/api/auth/me` include a profile summary to save a round trip? | Keep them on the **application only** and join at read time; keep `/me` **minimal** | Not blocking authentication, but the duplication decision is hard to reverse once both features exist. |
| 8 | **Should logout also end the Google session?** | **No** — our session only | Redirecting to Google's logout endpoint signs the user out of Google entirely, which on a shared or campus machine may be surprising. A deliberate product decision. |
| 9 | **OAuth callback path:** Spring's default `/login/oauth2/code/google`, or a custom `/api/auth/callback/google`? | **Keep Spring's default** | Zero custom code; the only cost is that the path sits outside `/api`. Must match Google Cloud Console either way. |
| 10 | **Where do the verification endpoints live** — `verification/` as proposed, or `auth/onboarding/`? | **`verification/`** | The onboarding choice creates an application and starts the review lifecycle, so the module that owns review should own submission. Affects the package layout only. |

### C. Risks and edge cases

| # | Risk | Mitigation |
| --- | --- | --- |
| 1 | **Open sign-in widens the attack surface.** Anyone with a Google account can now create an account and read all public content, then submit verification applications. | The verification review queue becomes the primary moderation surface: rate-limit submissions, require a real document, and treat the queue as a spam surface. The read-only Gate keeps the cost of a spam account close to zero. |
| 2 | **Verification fraud.** Supporting evidence is weak: a LinkedIn URL and a photo of a student card. `SENIOR` is the highest-value target because mentors are trusted. | Require human admin review for every claim; never auto-approve; log every decision with the reviewing admin; consider a second reviewer for `SENIOR`. |
| 3 | **The mentor pool depends entirely on admin action.** `SENIOR` is never self-selected and never inferred from semester, so no one can become a mentor on their own. | Decide up front how an admin identifies and promotes seniors, or mentor discovery may stay empty for a long time. Related to §11.B.6. |
| 4 | **Google's `sub` is unique per OAuth *client*, not per person.** If dev and prod share one database with different client IDs, provider-ID matching silently fails. | **A separate database per environment.** Match by `googleId` first, fall back to `email`. Document in the README. |
| 5 | A user deletes and recreates their Google account → new `sub`, same email → §4.2-D, second sub-case. Now more likely, since people hold both personal and work Google accounts. | Refuse to auto-overwrite a non-null `googleId`. Log for admin review. |
| 6 | **Unverified Google email.** Ordinary sign-ups can use a university-looking address without proving domain ownership, and `email_verified` is `false`. | Reject at sign-in when `email_verified != true`. Never infer UBIT status from the address (§0.1). |
| 7 | A user's password is shared across accounts / one person holds several Google accounts. | Not solvable with OAuth. Rely on verification review and admin action. Do not attempt to detect it. |
| 8 | Disabled account with a live session — the realistic leak path. | Two layers: reject at sign-in **and** delete sessions on disable (§4.2-E). |
| 9 | Role change, especially demotion from `ADMIN` or a mentor role. | Spring Session makes revocation possible and immediate; the admin action must actually call it (§4.2-F). |
| 10 | **CSRF 403s on the SPA's first write** after a fresh page load, due to Spring Security 6's lazily-rendered token. | Use the documented SPA `CsrfTokenRequestHandler` and bootstrap the cookie with one `GET` on mount. Budget for it in testing. |
| 11 | Cookies blocked entirely if the frontend and API are on genuinely cross-site origins. | Prefer same-origin deployment (§5.5). |
| 12 | MongoDB unavailable ⇒ the session store is unavailable ⇒ everyone is signed out. | Accepted: MongoDB is a hard dependency. Dev and test against a local `mongod`. |
| 13 | **Mass assignment of `role`/`enabled`/`verificationStatus`** through an update or submission endpoint. | Those fields must not exist on self-service DTOs at all (§6.1). Review every DTO when profiles and verification are built. |
| 14 | `BOOTSTRAP_ADMIN_EMAIL` left set in the environment after bootstrap. | Applied at creation only, so it cannot re-promote; still a required runbook step to unset it. |
| 15 | Verification documents are sensitive identity evidence. | Admin-only access, never a public URL, never logged, with a retention policy for rejected applications (§11.B.2). |
| 16 | IDOR — a signed-in user reading or editing another user's resources by id. | Scope every query by the authenticated user id; a standing review checklist item for every later feature. |
| 17 | Onboarding abandonment leaves `role = null` accounts indefinitely. | Intended and harmless (read-only), but consider an admin-visible filter for stalled signups. |
| 18 | No account-deletion policy. `enabled = false` is a disable, not a deletion, and submitted documents must also be deleted on request. | Out of scope now; settle before launch, since it affects the admin module. |

### D. Exact next implementation step

**Resolve §11.B.1 and §11.B.2 first.** They change the shape of the first files
written: B.1 fixes the `User` document, and B.2 determines whether the
verification module is a metadata form or a file-handling subsystem.

Once those are answered, the first slice is the **backend vertical slice for
sign-in and identity**, in this order:

1. `pom.xml`: add `spring-boot-starter-oauth2-client` and
   `spring-session-data-mongodb`.
2. `AuthProperties` for `BOOTSTRAP_ADMIN_EMAIL` (default empty) and cookie flags,
   plus `SessionConfig` with `@EnableMongoHttpSession`.
3. `AppRole`, `VerificationStatus`, then the `User` document and `UserRepository`
   with the indexes from §1.2.
4. `SecurityConfig`: `oauth2Login()`, CSRF, logout, method security enabled,
   form login and HTTP Basic disabled.
5. `AuthenticatedUser` principal, the role→authority mapper with its null branch,
   and `AuthUserService` implementing §4.2-A/B/D/E plus the creation-time
   bootstrap check.
6. `AuthController` with `/login`, `/me`, `/logout`.
7. Tests asserting: first-sign-in creation with `role = null`; the disabled
   rejection; the unverified-email rejection; the `googleId` conflict refusal;
   and the bootstrap match firing **only** at creation. These five are the rules
   most likely to regress silently.
8. Only then the verification module, then `AuthContext` plus
   `VerificationContext` in React, and a full browser round trip.

Defer to later slices: profiles, mentor and community actions, admin review UI,
and detailed per-endpoint permissions.

## Implementation notes

The design above is implemented. The decisions below record where the code
departs from the document, and why.

### Resolved decisions

- **A11.B.1 — `User` fields.** Implemented as the document specifies. A column
  view by the rest of the service layer.
- **A11.B.2 — verification is a file-handling subsystem**, not a metadata form.
  Student documents are stored in a GridFS bucket, so a student claim is a
  multipart request rather than a JSON one.
- **A11.B.1 / `User.name`**: included, per the explicit implementation
  requirement to carry a display name. It is presentation data read from the
  Google profile, not an identity claim, and nothing authorises on it.

### Deviations from the design

- **`AppRole` lives in `user.model`.** Placing it under `auth` would make the
  user module depend on the auth module, which inverts the dependency direction.
  The enum is account data, not an authentication concern.
- **Onboarding accepts `STUDENT` and `ALUMNI`**, matching the design's
  Current Student / Alumni choice. No `CURRENT_STUDENT` value exists in the enum.
- **The OAuth2 authorization endpoint keeps Spring Security's default
  `/oauth2/authorization/{registrationId}`.** Overriding it with a literal path
  removes the `{registrationId}` placeholder, the redirect filter stops
  matching, and no one can sign in at all. A test asserts the 302 redirect, which
  is what caught this.
- **Sign-out is handled by the `LogoutFilter`, not a controller.** A controller
  returning 204 would not invalidate the session or clear the authentication from
  the security context. The endpoint is `POST /api/auth/logout`, not `/logout`.
- **The session principal is not `transient`.** MongoDB-backed sessions serialize
  the authentication, and a transient principal is restored as `null`, leaving a
  signed-in session that fails every subsequent request.
- **Authorization gates are static methods** on `AuthorizationGates`, and the
  signed-in account is resolved per request by `AccountAccess`. There is no
  custom current-user argument resolver; `@AuthenticationPrincipal` and
  `AccountAccess` cover the same ground with less machinery.
- **Unique indexes are created by `MongoIndexInitializer`, not by annotations.**
  Auto-index creation is off so the application never mutates a production
  collection on startup, which makes `@Indexed` inert. The initializer creates
  `uk_users_google_id` and `uk_users_email` explicitly. Both matter: without the
  unique index on `googleId`, two concurrent first-time sign-ins for one identity
  can both pass the existence check and both insert.
- **A 401 body is the shared `ApiError` shape**, serialised through the shared
  `ObjectMapper`, rather than hand-written JSON, so a client parses one format.
- **Student multipart fields are bound to the validated record** via
  `@Valid @ModelAttribute`. Constructing the record inside the controller made the
  `@Min`/`@Max` constraints dead code, and a semester outside 1-8 reached the
  service.
- **The Spring Session store is selected by a `SessionRepository` bean.** With
  `spring-session-data-mongodb` on the classpath, Boot's Mongo session
  auto-configuration activates regardless of `spring.session.store-type=none`, so
  the test context supplies an in-memory repository to make it back off.

### Test coverage

128 backend tests. The five rules the design flags as most likely to regress
silently are covered directly:

1. First sign-in creates an account with `role = null` and `UNVERIFIED`.
2. A disabled account is refused.
3. An unverified Google email is refused.
4. A `googleId` conflict is refused rather than auto-merged.
5. The bootstrap-admin grant fires at account creation only, so it can never
   re-promote a demoted administrator.

`SecurityConfigTest` runs the real filter chain over HTTP and asserts the
configured behaviour: an anonymous API call is a JSON 401, an anonymous write
without a CSRF token is a 403, onboarding with a valid token is still a 401, the
Google endpoint redirects, and sign-out expires the session cookie.
