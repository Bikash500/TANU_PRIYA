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
│ students, users, attendance,  │
│ academic_records, audit_logs  │
└───────────────────────────────┘
```

During local development, Vite serves the UI on port `5173` and proxies `/api`
requests to the Spring Boot service on `127.0.0.1:8080`. The database path can
be overridden with `STUDENT_DB_PATH`; otherwise it defaults to
`server/data/students.sqlite`.

## Repository structure

```text
src/
  App.jsx                       authenticated app shell and role navigation
  api.js                        shared authenticated fetch and error handling
  components/LoginPage.jsx      sign-in page
  features/
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
    attendance/                 daily attendance API
    audit/                       audit logging and scoped activity API
    config/                      SQLite schema migration and demo seed data
    reports/                     summary and CSV endpoints
    security/                    authentication, roles, account API
    student/                     student profile domain and API
  src/main/resources/
    application.properties      service and SQLite configuration
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
| Students | `students` | Profile CRUD, admission number, grade, family contacts, enrollment and status |
| Accounts | `users` | BCrypt password hashes, role, optional linked student |
| Attendance | `attendance` | One status per student and date; records who made the change |
| Academics | `academic_records` | Subject, term, score, remarks, and recorder |
| Audit | `audit_logs` | Actor, action, entity, details, and event timestamp |

Relationships are enforced by SQLite foreign keys. Attendance and academic
records are deleted with their student. Deleting a user clears references to
that user. The startup initializer adds missing student profile columns,
backfills admission numbers and enrollment dates, creates module tables, then
inserts demo data using conflict-safe statements.

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
| Read/manage student profiles | Yes | No | Linked student only |
| Read attendance | Yes | Yes | Linked student only |
| Mark attendance | Yes | Yes | No |
| Read academic results | Yes | No | Linked student only |
| Create academic results | Yes | No | No |
| Manage accounts | Yes | No | No |
| School reports and summary | Yes | No | No |
| School-wide audit history | Yes | No | No |
| Linked-student activity | No | No | Linked student only |

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
| `/api/students` | Student profile CRUD and filtering |
| `/api/attendance` | Daily attendance read and mark/update |
| `/api/academics` | Academic result read and administration |
| `/api/reports` | Administrator summary and CSV exports |
| `/api/users` | Administrator account list/create/delete |
| `/api/audit` | Administrator audit history; guardian-linked activity |
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
