# Pandey Technical School Architecture

## Overview

Pandey Technical School is a locally runnable school information system with a
React single-page application and a modular Spring Boot REST service. The
backend is one deployable service organized by school domain; it is not a set
of independently deployed microservices. SQLite provides persistent storage
for the current single-school development setup.

## System context

```text
┌───────────────────────────────┐
│ Browser                       │
│ React 18 + Vite               │
│ Login, role navigation, pages │
└──────────────┬────────────────┘
               │ /api/*, HTTP Basic
               ▼
┌───────────────────────────────┐
│ Spring Boot 3.5 REST service  │
│ Spring Security + JDBC        │
│ Domain controllers/services  │
└──────────────┬────────────────┘
               │ JDBC
               ▼
┌───────────────────────────────┐
│ SQLite                        │
│ students.sqlite               │
│ students, academic_years,     │
│ class_sections, enrollments,  │
│ users, attendance, results,   │
│ audit_logs                    │
└───────────────────────────────┘
```

During local development, Vite serves the UI on port `5173` and proxies `/api`
requests to the Spring Boot service on `127.0.0.1:8080`. The database path can
be overridden with `STUDENT_DB_PATH`; otherwise it defaults to
`server/data/students.sqlite`.

The production Docker image serves the built React assets and Spring Boot API
from one origin. The `production` Spring profile connects to managed
PostgreSQL and runs versioned Flyway migrations; local SQLite initialization
and sample data are disabled for that profile.

## Repository structure

```text
src/
  App.jsx                       authenticated app shell and role navigation
  api.js                        shared authenticated fetch and error handling
  components/LoginPage.jsx      sign-in page
  features/
    structure/                  academic years, classes, sections, and enrollment
    assistant/                  general AI chat for all signed-in roles
    students/                   student profiles and administration
    attendance/                 attendance marking
    academics/                  academic result management
    reports/                    report summaries, HTML previews, CSV downloads
    accounts/                   administrator account management
    audit/                      administrator audit view
    guardian/                   linked-student family portal
backend/student-service/
  src/main/java/.../
    academics/                  academic record API
    assistant/                  authenticated chat proxy; no chat persistence
    attendance/                 daily attendance API
    audit/                       audit logging and scoped activity API
    config/                      SQLite schema migration and demo seed data
    reports/                     summary and CSV endpoints
    school/                      academic years, class sections, and enrollments
    security/                    authentication, roles, account API
    student/                     student profile domain and API
  src/main/resources/
    application.properties      service and SQLite configuration
    application-production.properties
                               PostgreSQL and cloud runtime configuration
    db/migration/                production PostgreSQL Flyway migrations
    schema.sql                  base student table schema
  src/test/                      Spring integration tests
server/data/                     local SQLite database (ignored by Git)
scripts/run-spring.js            starts the backend from the npm dev command
```

## Backend modules and data ownership

The Spring Boot service owns validation, authorization, persistence, and audit
events. Controllers expose domain APIs; `StudentService` and
`StudentRepository` encapsulate student operations, while the domain-specific
controllers use Spring JDBC for attendance, academics, reports, users, and
audit data.

| Domain | Main tables | Responsibilities |
| --- | --- | --- |
| School structure | `academic_years`, `class_sections`, `student_enrollments` | Academic calendar, grade sections, year-specific placements, and promotion history |
| Students | `students` | Profile CRUD, admission number, current grade, family contacts, admission date, and status |
| Accounts | `users` | BCrypt password hashes, role, optional linked student |
| Attendance | `attendance` | One status per student and date; records who made the change |
| Academics | `academic_records` | Subject, term, score, remarks, and recorder |
| Audit | `audit_logs` | Actor, action, entity, details, and event timestamp |

Relationships are enforced by SQLite foreign keys. Attendance and academic
records are deleted with their student. Deleting a user clears references to
that user. A student can have one enrollment per academic year, with the
enrollment retaining the section and status for that year. Sections with
enrollment history cannot be deleted. Activating a year synchronizes the
legacy `students.grade` field from that year's active enrollments so existing
attendance and academic screens continue to show the current grade.

The startup initializer adds missing student profile columns, backfills
admission numbers and enrollment dates, creates module tables, and adds the
initial 2026-27 year with sections A for Grades 1–12. Existing students are
enrolled in the section matching their stored grade. These backfills and
default records are repeat-safe and preserve existing rows.

## Request and authentication flow

1. A user enters an email and password in the React login page.
2. React requests `GET /api/auth/me` with HTTP Basic credentials.
3. Spring Security loads the user and BCrypt hash from `users` and returns the
   user identity and role when authentication succeeds.
4. The UI keeps credentials in the in-memory session state and attaches the
   Basic authorization header to subsequent API requests.
5. Spring Security enforces endpoint and method-level permissions. Controllers
   scope guardian queries using the authenticated account's linked student ID.
6. Mutating domain operations record audit events alongside their changes.

The UI hides modules that do not apply to a role, but the API is the actual
security boundary; direct API requests are independently authorized.

## Authorization model

| Capability | Administrator | Teacher | Guardian |
| --- | :---: | :---: | :---: |
| Manage academic years, sections, and student enrollments | Yes | No | No |
| Read/manage student profiles | Yes | No | Linked student only |
| Read attendance | Yes | Yes | Linked student only |
| Mark attendance | Yes | Yes | No |
| Read academic results | Yes | No | Linked student only |
| Create academic results | Yes | No | No |
| Manage accounts | Yes | No | No |
| School reports and summary | Yes | No | No |
| School-wide audit history | Yes | No | No |
| Linked-student activity | No | No | Linked student only |
| General AI assistant | Yes | Yes | Yes |

Guardians cannot choose another student's ID to expand their scope. The API
derives the linked student from the authenticated principal. Guardian activity
is restricted to student and attendance events for that student and academic
events whose record belongs to that student.

## Frontend

The React app is a single-page application with feature-oriented screens.
`App.jsx` hosts the authenticated shell, banner, and role-specific navigation.
Each feature owns its UI state and calls the shared `apiRequest` helper.
The Reports page builds an HTML report preview in the browser, supports
printing that report, and downloads CSV exports from the backend.

The AI Assistant keeps conversation state only in the current browser tab and
does not query or persist school records. Authenticated requests are bounded
and rate-limited before the backend sends them to the configured provider.
For local no-per-request-cost inference, configure Ollama at
`http://127.0.0.1:11434/v1` with `AI_PROVIDER=ollama`; prompts stay on the
machine running Ollama. Alternatively, `AI_PROVIDER=openai` uses the hosted
OpenAI API and requires the backend `OPENAI_API_KEY` environment variable;
provider retention terms and possible usage charges apply.

## Local runtime and startup

Requirements: Node.js 20.19+ and Java 21.

```sh
npm install
npm run dev
```

The development script starts Vite and the Maven Wrapper-backed Spring service
together. To run one process independently:

```sh
npm run dev:web
npm run dev:api
```

Vite provides the browser UI and `/api` proxy. Spring Boot binds to loopback
by default; `SERVER_PORT` can change its port. Set `STUDENT_DB_PATH` to use a
different SQLite database.

For deployment, see the Azure production preparation section in
[README.md](./README.md). Production startup requires `DATABASE_URL`,
`DATABASE_USERNAME`, and `DATABASE_PASSWORD`; Flyway creates the PostgreSQL
schema. On an empty production database, `INITIAL_ADMIN_EMAIL` and
`INITIAL_ADMIN_PASSWORD` are required to bootstrap one administrator. No
demo accounts or sample students are created in the production profile.
Never copy the local SQLite file or local demo passwords into the hosted
environment.

The container configuration is a deployment starting point, not a statement
that the application is production-ready for real student data. Complete a
security/privacy review, private database networking, HTTPS-only ingress,
secret management, administrator MFA/account recovery, rate limiting,
monitoring, and backup/restore validation before live use. Existing SQLite
records require an independently reviewed, secure migration plan.

## Demo and migration behavior

The first upgraded service start migrates the existing `students` table in
place, preserving student rows and populating missing required profile
defaults. The initializer then seeds 24 fictional Indian demo students and
six individual guardian demo accounts. `INSERT OR IGNORE` and unique
admission numbers make demo seeding repeat-safe and do not overwrite existing
records.

Demo accounts and passwords are documented in [README.md](./README.md).
They are development conveniences only. Do not use them with real student
data or expose the demo service publicly.

## API groups

| Prefix | Purpose |
| --- | --- |
| `/api/auth` | Current authenticated account |
| `/api/school` | Administrator school structure; read-only academic year and section lists are also available to teachers |
| `/api/students` | Student profile CRUD and filtering |
| `/api/attendance` | Daily attendance read and mark/update |
| `/api/academics` | Academic result read and administration |
| `/api/reports` | Administrator summary and CSV exports |
| `/api/users` | Administrator account list/create/delete |
| `/api/audit` | Administrator audit history; guardian-linked activity |
| `/api/assistant/status` | Authenticated check for AI provider configuration |
| `/api/assistant/chat` | Authenticated general AI chat for all roles |
| `/api/health` | Service health |

See [README.md](./README.md) for the endpoint table and local setup details.

## Testing and build

The Spring integration tests use an isolated temporary SQLite database and
exercise student profiles, seeded demo users, attendance, role restrictions,
guardian scoping, CSV output, and report endpoints.

```sh
cd backend/student-service
.\mvnw.cmd test
cd ..\..
npm run build
```

## Security and production considerations

The current arrangement is intended for local development and demonstration.
Before using real school data or deploying beyond loopback, the service needs
HTTPS, managed secrets, production credentials, account lifecycle and recovery
processes, backups, monitoring, and an independent security review. HTTP Basic
credentials must not be sent over an unencrypted network. SQLite is appropriate
for the current single-service local setup; a multi-instance production
deployment should evaluate a managed database and migration tooling.
