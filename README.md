# AI-Powered Student Mentorship & Alumni Hub — UBIT

A web platform for University of Karachi / UBIT students, senior students,
alumni, and administrators. It provides community Q&A, mentor discovery with
explainable AI-assisted matching, mentorship requests, alumni networking, user
verification, administrative moderation, and a retrieval-augmented assistant
scoped to curated UBIT documents.

> **Status: the authentication and verification slice is implemented.** Google
> sign-in, server-side sessions, onboarding, and the student/alumni verification
> workflow are working and covered by 128 backend tests plus a minimal React UI.
> No other feature has been built — see
> [Development status](#development-status).

## Technology stack

| Layer | Technology |
| --- | --- |
| Frontend | React 19, TypeScript 6, Vite 8, oxlint |
| Backend | Java 17, Spring Boot 3.5, Maven, REST APIs |
| Persistence | MongoDB via Spring Data MongoDB |
| Authentication | Google OAuth2/OIDC, MongoDB-backed sessions, CSRF |
| AI | Explainable mentor matching (planned) + UBIT-scoped RAG assistant (planned) |

## Repository structure

```
ubit-mentorship-hub/
├── backend/                      Spring Boot REST API (Maven)
│   ├── src/main/java/com/ubit/mentorshiphub/
│   │   ├── auth/                 Google sign-in, sessions, roles
│   │   ├── user/                 Account identity and roles
│   │   ├── profile/              Student / senior / alumni profiles
│   │   ├── question/             Community questions
│   │   ├── answer/               Answers to questions
│   │   ├── mentorship/           Mentorship requests and workflow
│   │   ├── recommendation/       Explainable mentor matching
│   │   ├── alumni/               Alumni directory
│   │   ├── verification/         Identity verification records
│   │   ├── rag/                  UBIT-scoped retrieval assistant
│   │   ├── admin/                Moderation and management
│   │   └── common/               Shared HTTP and error handling
│   └── src/main/resources/application.yml
├── frontend/                     React + TypeScript SPA (Vite)
│   └── src/
│       ├── components/           Shared presentational components
│       ├── layouts/              Page-level layout shells
│       ├── services/             REST API clients
│       ├── hooks/                Shared reusable hooks
│       ├── context/              React context providers
│       ├── types/                Shared TypeScript types
│       ├── utils/                Shared utilities
│       └── features/             auth, profile, questions, mentorship,
│                                 alumni, verification, rag, admin
├── docs/
│   ├── architecture/             Architecture decisions and diagrams
│   ├── srs/                      Software requirements specification
│   ├── uml/                      UML diagrams
│   ├── database/                 Collection and index design
│   └── testing/                  Test plans and results
├── README.md
└── .gitignore
```

Both `backend/` and `frontend/` are independent projects with their own
`.gitignore` and their own dependency manifests. Either one can be built and run
on its own.

## Architecture

```
Frontend (React/Vite)
        │  REST / JSON
        ▼
Spring Boot controllers        ← HTTP concerns only
        ▼
Service layer                  ← business logic
        ▼
Repository layer (Spring Data) ← database access
        ▼
MongoDB
```

- Controllers, services, and repositories are kept separate and organised by
  feature rather than by technical layer.
- DTOs are used at the API boundary; persistence documents are not exposed
  directly.
- Incoming API data is validated with Jakarta Bean Validation, and failures are
  normalised into a single `ApiError` payload by `GlobalExceptionHandler`.
- AI features will sit behind dedicated service abstractions inside
  `recommendation/` and `rag/` rather than being called from controllers.

## Development setup

### Prerequisites

| Tool | Version used |
| --- | --- |
| JDK | 17 |
| Maven | 3.9.x (a wrapper is included in `backend/`) |
| Node.js | 22+ (developed against Node 24) |
| MongoDB | 6+ (local instance or Atlas) |

### Backend

```bash
cd backend
./mvnw spring-boot:run      # mvnw.cmd on Windows
```

The API listens on <http://localhost:8080> and exposes `GET /actuator/health`.

**MongoDB is required to actually sign in.** Spring Session creates its indexes
eagerly at startup, so the application will not come up without a reachable
MongoDB. In production the URI must point at a replica set, because session
expiry uses transactions and a standalone `mongod` cannot support them.

**Google credentials are required for sign-in.** With an empty client id the
application still starts, but `/oauth2/authorization/google` cannot build a
redirect. The authorised redirect URI in the Google console must be exactly
`http://localhost:8080/login/oauth2/code/google`.

There is no `.env` loader in the JVM, so export the variables in your shell
rather than creating a `.env` file and expecting it to be read:

```bash
export GOOGLE_CLIENT_ID=...
export GOOGLE_CLIENT_SECRET=...
export MONGODB_URI=...
cd backend && ./mvnw spring-boot:run
```

Override the defaults with environment variables — see `backend/.env.example`:

| Variable | Default | Purpose |
| --- | --- | --- |
| `GOOGLE_CLIENT_ID` | *(empty)* | Google OAuth client id; required for sign-in |
| `GOOGLE_CLIENT_SECRET` | *(empty)* | Google OAuth client secret; required for sign-in |
| `MONGODB_URI` | `mongodb://localhost:27017/ubit_mentorship_hub` | MongoDB connection string; required |
| `SERVER_PORT` | `8080` | HTTP port |
| `SESSION_TIMEOUT` | `60m` | Browser session lifetime |
| `SESSION_COOKIE_SECURE` | `false` | Set `true` behind HTTPS |
| `BOOTSTRAP_ADMIN_EMAIL` | *(empty)* | First administrator, promoted at account creation only |
| `VERIFICATION_GRIDFS_BUCKET` | `verification_documents` | GridFS bucket for uploaded documents |
| `APP_CORS_ALLOWED_ORIGINS` | *(empty)* | Only needed if the frontend is on a different origin |
| `APP_LOG_LEVEL` | `INFO` | Log level for `com.ubit.mentorshiphub` |

```bash
cd backend
./mvnw clean verify         # compile, test, package
```

### Frontend

```bash
cd frontend
npm install
npm run dev                 # http://localhost:5173
```

In development, the Vite dev server proxies `/api` to `http://localhost:8080`
(configured in `frontend/vite.config.ts`), so the browser stays on a single
origin and no CORS configuration is required locally.

```bash
cd frontend
npm run build               # type-check, then bundle to dist/
npm run typecheck
npm run lint
```

### Secrets

No credentials, API keys, or connection strings with passwords are committed.
Configuration is read from environment variables; `backend/.env.example`
documents the variable names with placeholder values only. Add local secrets to
an untracked `.env` file or your IDE run configuration.

## Development status

| Area | Status |
| --- | --- |
| Repository structure | Done |
| Backend build, configuration, error handling foundation | Done |
| Frontend build, tooling, feature folder layout | Done |
| Google authentication, sessions, and roles | Done |
| Student and alumni verification submission | Done |
| Minimal frontend for sign-in, onboarding, and verification status | Done |
| Administrator review UI for verification decisions | Not started |
| User profiles, skills, interests, preferences | Not started |
| Community Q&A | Not started |
| Mentor discovery and AI-assisted recommendations | Not started |
| Mentorship requests and accept/reject workflow | Not started |
| Alumni networking | Not started |
| UBIT RAG assistant | Not started |
| Administrative moderation | Not started |
| Deployment | Not started |

### What the authentication slice does

Sign-in is Google-only; there is no password form and no HTTP Basic. Any Google
account is accepted, because signup must be open and a university domain proves
nothing. Verification is what establishes UBIT student or alumni status, and an
administrator decides it.

The account record holds two independent facts, and the UI reports them
separately for that reason:

- **Role** — `STUDENT` or `ALUMNI`, claimed by the user at onboarding. `SENIOR`
  and `ADMIN` are never client-selectable; they are granted by an administrator.
- **Verification status** — `UNVERIFIED` → `PENDING` → `VERIFIED` or `REJECTED`,
  with `REJECTED` → `PENDING` allowed for resubmission.

Taking part in the platform requires both a role and `VERIFIED`, so a rejected
account keeps its role and its history and can resubmit. See
`docs/architecture/authentication-design.md` for the full reasoning.

Identity is keyed on the Google OIDC `sub` claim, not on email. A re-used address
with a different `sub` is a different person and is refused rather than silently
merged.

### API surface

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/auth/me` | Session state; `NOT_AUTHENTICATED` when signed out |
| `POST` | `/api/auth/onboarding` | Claim `STUDENT` or `ALUMNI` |
| `POST` | `/api/auth/logout` | Sign out; invalidates the session, returns `204` |
| `GET` | `/oauth2/authorization/google` | Start Google sign-in |
| `POST` | `/api/verification/student` | Student claim plus a 5 MB document (multipart) |
| `POST` | `/api/verification/alumni` | Alumni claim: passout year and LinkedIn URL |
| `GET` | `/api/verification/me` | Most recent application; `404` if never applied |

All `/api/**` calls other than `GET /api/auth/me` and `POST /api/auth/logout`
require a session, and every write requires a CSRF token.

## Scope

**In scope:** Google authentication, role-based access, user profiles, academic
and professional information, skills and interests, mentorship preferences,
LinkedIn/GitHub links, community Q&A, search and filtering, AI-assisted mentor
recommendations, mentorship requests with an accept/reject workflow, alumni
discovery, a verification workflow, administrative moderation, a UBIT-scoped RAG
assistant, REST API integration, MongoDB persistence, and deployment.

**Out of scope:** internal real-time messaging, replacing LinkedIn/GitHub,
automated access to official alumni records, training a large custom ML model, a
general-purpose unrestricted chatbot, and unnecessary enterprise-scale
infrastructure.
