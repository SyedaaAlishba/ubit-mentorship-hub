# Administrator Verification Review API

**Status:** Implemented
**Scope:** `admin.controller.AdminVerificationController`, `admin.service.AdminVerificationService`
**Depends on:** the verification submission flow (`GET /api/verification/me`) and the
`ADMIN` authorization gate
**Related:** [Authentication & User Architecture Design](../architecture/authentication-design.md)

---

## 1. Why these endpoints exist

The submission flow records an application and moves the account to `PENDING`, but
until now nothing could move it out again. The verification queue closes that
dead-end: an administrator can see what is waiting, open the proof a student
uploaded, and record an approve or reject decision with a reason the applicant can
read back.

Every endpoint is under `/api/admin/**` and requires a signed-in account whose role
is `ADMIN` **and** whose own status is `VERIFIED`. Authorization is enforced in the
service through the project's existing gates — `AccountAccess.requireCurrentUser()`
followed by `AuthorizationGates.requireRoles(user, AppRole.ADMIN)` — not with Spring
method security, matching every other feature area. An anonymous caller is refused
by the filter chain with `401`; a signed-in non-administrator is refused by the gate
with `403`.

---

## 2. Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/admin/verification/applications` | Page through applications, defaulting to the pending queue |
| `GET` | `/api/admin/verification/applications/{applicationId}/document` | Stream the proof belonging to one application |
| `POST` | `/api/admin/verification/applications/{applicationId}/decide` | Record an approve or reject decision |

### 2.1 List applications

```
GET /api/admin/verification/applications?status=PENDING&page=0&size=20
```

| Query parameter | Rules |
| --- | --- |
| `status` | Optional. One of `PENDING`, `VERIFIED`, `REJECTED`; defaults to `PENDING`. An unknown value is `400`. |
| `page` | Zero-based, `>= 0`. Defaults to `0`. |
| `size` | `1..50`, default `20`. |

The queue is ordered **oldest first** (`submittedAt` ascending, then `_id`), so the
oldest claim is reviewed first. The response body is a Spring Data `Page`:

```json
{
  "content": [
    {
      "applicationId": "651f2a...",
      "userId": "64b0000000000000000000a1",
      "applicantName": "Ali Raza",
      "applicantEmail": "ali@example.com",
      "type": "STUDENT",
      "claimedRole": "STUDENT",
      "status": "PENDING",
      "attemptNumber": 1,
      "submittedAt": "2026-10-15T12:00:00Z",
      "program": "SE",
      "currentSemester": 6,
      "documentType": "STUDENT_CARD",
      "documentFilename": "card.png",
      "passoutYear": null,
      "linkedInUrl": null,
      "decidedAt": null,
      "reviewerNote": null,
      "documentUrl": "/api/admin/verification/applications/…/document"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

Notes on the shape:

- **The applicant's name and email are included on purpose.** Reviewing a claim
  against the university register is impossible without identifying the person. They
  are shown to administrators only; no other response exposes them.
- `documentUrl` is present only when the application actually has a stored document
  — every alumni application omits it. The GridFS id is never returned; a reviewer
  reaches the file only through this URL, whose authorization is re-checked on each
  request.
- `googleId` and all account internals are never part of this projection.

---

## 3. Retrieving a document

`GET /api/admin/verification/applications/{applicationId}/document` streams the
bytes straight from GridFS — nothing is buffered in memory, so a 5 MB scan costs no
heap. The response carries:

- `Content-Type` — the stored type, re-validated against the upload allow-list
  (`image/png`, `image/jpeg`, `image/webp`, `application/pdf`); anything else is
  downgraded to `application/octet-stream`.
- `Content-Disposition` — `inline` for the three raster image types, so a reviewer
  can see a student card in a browser tab; `attachment` for a PDF, so the browser's
  PDF parser does not run on attacker-supplied input inside this application's own
  origin.
- `Content-Length` — the stored file length.

The application id in the path is the **only** identifier involved. The GridFS id is
read from the stored application on the server, so a caller cannot substitute
another application's file, and an application that holds no document is a plain
`404`.

---

## 4. Recording a decision

```
POST /api/admin/verification/applications/{applicationId}/decide
Content-Type: application/json

{ "decision": "REJECTED", "reviewerNote": "The student card is not legible." }
```

| Field | Rule |
| --- | --- |
| `decision` | Required; exactly `VERIFIED` or `REJECTED`. `PENDING` is not a valid value — it is not in the enum, so it is rejected by validation. |
| `reviewerNote` | Optional for `VERIFIED`; **required and non-blank** for `REJECTED`. At most 1000 characters. |

The response reports both final states so a caller can see they agree:

```json
{
  "applicationId": "…",
  "userId": "…",
  "applicationStatus": "REJECTED",
  "userVerificationStatus": "REJECTED",
  "decidedAt": "2026-10-16T12:00:00Z",
  "reviewerNote": "The student card is not legible."
}
```

A rejection is not terminal for the applicant: their account moves to `REJECTED`,
which is startable again, so they can correct the problem and resubmit. The reason
is exposed to the applicant through the existing `GET /api/verification/me`
(`reviewerNote`); it is never shown to anyone else.

### Status codes

| Code | When |
| --- | --- |
| `200` | Decision recorded |
| `400` | Unknown decision value, over-long note, or a rejection with no reason |
| `401` | No session |
| `403` | Signed in but not a verified administrator |
| `404` | No such application, or no such document |
| `409` | The application is no longer pending — most often because another administrator just decided it |

---

## 5. Consistency and concurrency

A standalone `mongod` cannot run multi-document transactions, so "the application's
status and the applicant's account status always agree" is not a single atomic
write. The decision endpoint makes the unavoidable window safe by ordering:

1. **Claim the application** with one conditional update
   (`findAndModify` on `{ _id, status: PENDING }`, setting the final status,
   `decidedAt`, `reviewerUserId` and `reviewerNote`). The `status = PENDING`
   criterion is the concurrency guard: of two administrators deciding the same
   application at once, exactly one update matches; the other matches nothing and
   receives `409`.
2. **Then move the account**, through `UserService.applyVerificationDecision`,
   which re-checks that the account is still `PENDING`.

If step 2 refuses — which the guards make all but impossible, since nothing else
writes a pending account — the claim from step 1 is reverted, so the two records
never disagree. A crash between the steps leaves the application decided and the
account still pending: the **fail-closed** direction, where no privilege is ever
granted without the review record being committed first.

The `(status, submittedAt)` index on `verification_applications`, created by
`MongoIndexInitializer`, covers the queue query.

---

## 6. Tests

| Test | Covers |
| --- | --- |
| `AdminVerificationServiceTest` | The admin gate, status filter and paging validation, oldest-first ordering, the no-document `404`, inline vs attachment disposition, note requirements, both statuses moving together, and the claim/revert compensation |
| `AdminVerificationControllerTest` | HTTP shape, validation, and the mapping of each failure onto `400`/`403`/`404`/`409`; the document endpoint streamed through the async dispatch |
| `AdminVerificationIntegrationTest` | The real index, the real query ordering, end-to-end application↔account consistency, resubmission after rejection, repeated decisions, and two concurrent decisions resolving to exactly one winner (against a real MongoDB) |
| `SecurityConfigTest` | Anonymous access to all three endpoints is a JSON `401` through the real filter chain |

The integration test drives `MongoIndexInitializer`, the real repositories and the
real `UserService`; only the authorization lookup is mocked. It is skipped, not
failed, when no MongoDB is reachable.
