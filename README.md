# Student Manager

A React student directory connected to a Java Spring Boot REST microservice.
The service uses SQLite for persistent student records.

See [ARCHITECTURE.md](./ARCHITECTURE.md) for the system overview, modules,
data model, authorization boundaries, and runtime architecture.

## Run locally

Requirements: Node.js 20.19+ and Java 21. The backend includes the Maven
Wrapper, so Maven does not need to be installed separately.

```sh
npm install
npm run dev
```

This starts the React app at `http://localhost:5173` and the Spring Boot
student service at `http://127.0.0.1:8080`. Vite proxies `/api` requests to
the service. To run either project on its own:

```sh
npm run dev:web
npm run dev:api
```

The service reuses the existing SQLite database at
`server/data/students.sqlite`. Set `STUDENT_DB_PATH` to change the database
file.

## AI assistant

The signed-in AI Assistant section is available to administrators, teachers,
and guardians. It answers general questions only, does not access school
records, and keeps chat messages only in the current browser tab.

For a no-per-request-cost local setup, install [Ollama](https://ollama.com/)
and download a model such as `qwen2.5:3b`. Run the backend with
`AI_PROVIDER=ollama`, `OPENAI_BASE_URL=http://127.0.0.1:11434/v1`, and
`OPENAI_MODEL=qwen2.5:3b`. Ollama runs the model on the computer; no OpenAI API
key is needed, and prompts go to the local Ollama service. Model downloads use
disk space, and chat uses the computer's memory and CPU/GPU. A computer
serving the app through a temporary tunnel must stay on with both the app and
Ollama running.

The optional hosted OpenAI setup uses `AI_PROVIDER=openai` and
`OPENAI_API_KEY` in the backend process environment. API usage may incur
charges. Never add the key to React code, commit it, or paste it into chat.
Without a configured provider, the assistant page explains that setup is
needed. Requests are limited to 20 per signed-in account per hour.

## Preparing an Azure production deployment

The repository now includes a production Spring profile, PostgreSQL Flyway
migrations, and a multi-stage `Dockerfile` that builds the React UI into the
Spring Boot image. The production profile does not run the local SQLite
initializer, demo users, or fictional roster. It expects an empty managed
PostgreSQL database and creates the initial administrator only when the
database has no administrator.

Recommended hosting for an India-based school:

- Azure App Service for Containers, serving both the React application and
  `/api` from one HTTPS origin.
- Azure Database for PostgreSQL Flexible Server, in an India region selected
  to meet the school's contractual and data-residency requirements.
- Private networking between the app and database, Azure Key Vault references
  for database/admin secrets, HTTPS-only ingress, and a managed identity for
  pulling the private container image.

Before provisioning, confirm the Azure subscription, the region availability
and current pricing in that subscription, and the school's student-data
privacy requirements. No student database or secrets are included in the
container build context.

Build and test the container from the repository root:

```sh
docker build -t pandey-school:production .
```

Configure the App Service container to listen on port `8080` (`WEBSITES_PORT`
for a Linux custom container) and set these application settings. Store all
passwords in Key Vault; never commit them or send them in chat.

| Setting | Value |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `production` |
| `SERVER_PORT` | `8080` |
| `DATABASE_URL` | `jdbc:postgresql://<private-server>:5432/<database>?sslmode=require` |
| `DATABASE_USERNAME` | PostgreSQL application account (not the server owner) |
| `DATABASE_PASSWORD` | Key Vault reference to the application account password |
| `DATABASE_MIGRATION_USERNAME` | Optional separate schema-migration account |
| `DATABASE_MIGRATION_PASSWORD` | Key Vault reference for the migration account |
| `INITIAL_ADMIN_EMAIL` | First school administrator email |
| `INITIAL_ADMIN_PASSWORD` | Key Vault reference to a unique password (at least 14 characters) |
| `OPENAI_API_KEY` | Optional Key Vault reference to enable the AI assistant |
| `OPENAI_MODEL` | Optional OpenAI chat model (defaults to `gpt-4o-mini`) |
| `WEBSITES_PORT` | `8080` (App Service container setting) |

The first startup runs Flyway migrations. After the administrator has signed
in and confirmed account recovery is configured, remove
`INITIAL_ADMIN_PASSWORD` from the App Service settings; the bootstrapped
account remains in PostgreSQL. The public `/` route serves only the login UI;
API routes still require authentication and role authorization.

For least privilege, provide a dedicated migration account using the optional
`DATABASE_MIGRATION_*` settings and grant the runtime database account only
the DML and sequence privileges it needs. If those optional settings are
omitted, Flyway uses the runtime database account.

**This is deployment preparation, not approval to put live student data
online.** Before launch, complete a security/privacy review, verify HTTPS-only
and database-private networking in Azure, establish monitored backups and a
successful restore test, and decide how existing SQLite records will be
reviewed and migrated. The current application still needs a production
authentication review (including rate limiting, account recovery, and
administrator MFA) before it should be relied on for sensitive student
records. Do not copy the local SQLite database or demo credentials into
production.

## Local demo sign-in

The app is bound to loopback for local development and uses HTTP Basic
authentication with BCrypt password hashes. The included demo accounts are
for local testing only; do not expose this configuration or use the sample
passwords with real student data. Set `DEMO_ADMIN_PASSWORD`,
`DEMO_TEACHER_PASSWORD`, and `DEMO_GUARDIAN_PASSWORD` before the first service
start to choose local passwords. Existing demo accounts are not overwritten
on later starts.

| Role | Email | Local demo password |
| --- | --- | --- |
| Administrator | `admin@pandey.edu` | `AdminDemo2026!` |
| Teacher | `teacher@pandey.edu` | `TeacherDemo2026!` |
| Guardian | `guardian@pandey.edu` | `GuardianDemo2026!` |

The `guardian@pandey.edu` demo account is linked to one existing student.
Six additional guardian demo accounts are seeded for the sample roster:
`guardian01@pandey.edu` through `guardian06@pandey.edu`. They use the local
guardian demo password above and each can see only their linked student.
Administrators can create teacher and guardian accounts in **Accounts** and
link each guardian to one student.

## School modules

- **School structure:** administrator-managed academic years, grade sections,
  rooms, year-specific student enrollment, promotion planning, and enrollment
  status history. Activating a year updates each enrolled student's current
  grade while keeping previous-year placements.
- **Administrator:** manages school structure, student profiles, accounts,
  academic results, reports, and school audit history; can also mark
  attendance.
- **Teacher:** can view the attendance roster and mark or update attendance
  only. Student profile, academic, report, and account management are denied.
- **Guardian:** read-only access to their linked student's profile, full
  attendance history, academic results, and that student's activity only.
- **Attendance:** daily Present, Absent, Late, and Excused entries, with
  date and grade filters for administrators and teachers.
- **Academics:** administrator-managed subject and term scores from 0 to 100
  with remarks.
- **Reports:** administrator-only summary cards, styled printable HTML
  previews, and CSV exports for students, attendance, and academic results.
- **Accounts:** administrator-managed Administrator, Teacher, and Guardian
  accounts with BCrypt-hashed passwords and guardian-to-student links.
- **Audit history:** administrator-only school-wide log; guardians can see
  only activity associated with their linked student.

Existing student records are preserved. On the first upgraded service start,
missing admission numbers are generated as `PTS-00001`, `PTS-00002`, and so
on, enrollment dates are initialized, and optional profile details remain
blank unless already present.

The school-structure migration creates the `2026-27` academic year, adds
sections A for Grades 1–12, and places existing students in the section
matching their current grade. These initial records are inserted only when
missing. Create sections for a future year and enroll or promote students
there before activating that year.

The service also seeds 24 fictional Indian student profiles with
`PTS-DEMO-001` through `PTS-DEMO-024` admission numbers. Six have separate,
student-scoped demo guardian accounts. Seeding is safe to repeat: existing
demo students and accounts are not duplicated or overwritten.

The sample authentication setup is for local development only. A real school
deployment needs HTTPS, managed secrets, account recovery, and a security
review before it can safely handle student data.

## Student API

Requests and responses use JSON, except a successful delete, which returns
`204 No Content`.

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/health` | Service health |
| `GET` | `/api/school/academic-years` | List academic years (administrator and teacher) |
| `POST` | `/api/school/academic-years` | Create an academic year (administrator only) |
| `PUT` | `/api/school/academic-years/:id/activate` | Activate a year and sync current student grades |
| `GET` | `/api/school/sections?academicYearId=:id` | List class sections (administrator and teacher) |
| `POST` | `/api/school/sections` | Create a section (administrator only) |
| `PUT` | `/api/school/sections/:id` | Rename a section or change its room |
| `DELETE` | `/api/school/sections/:id` | Delete a section with no enrollment history |
| `GET` | `/api/school/enrollments?academicYearId=:id` | List year-specific student enrollments |
| `POST` | `/api/school/enrollments` | Enroll a student in a section |
| `PUT` | `/api/school/enrollments/:id` | Change a student's section or enrollment status |
| `GET` | `/api/students` | List students |
| `POST` | `/api/students` | Create a student |
| `PUT` | `/api/students/:id` | Update a student |
| `DELETE` | `/api/students/:id` | Delete a student |
| `GET` | `/api/attendance?date=YYYY-MM-DD` | Read daily attendance |
| `PUT` | `/api/attendance` | Mark or update one attendance entry |
| `GET` | `/api/academics` | Read academic results |
| `POST` | `/api/academics` | Record an academic result |
| `GET` | `/api/reports/*.csv` | Download CSV exports |
| `GET` | `/api/reports/summary` | Read report summary (administrator only) |
| `GET` | `/api/users` | List accounts (administrator only) |
| `POST` | `/api/users` | Create an account (administrator only) |
| `GET` | `/api/audit` | Read audit history (administrator only) |
| `GET` | `/api/audit/student-activity` | Read linked-student activity (guardian only) |

Student profiles require a unique `admissionNumber`, `name`, `grade` (`Grade 1`
through `Grade 12`), and a valid `email` address. Optional profile fields
include birth date, guardian contact details, address, enrollment date, and
status. Academic scores range from 0 to 100. Invalid requests return JSON
errors with appropriate `4xx` statuses.

Run `npm run build` to create the frontend production build. In production,
configure the web server or reverse proxy to route `/api` to the student API.
