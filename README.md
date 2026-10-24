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

- **Administrator:** manages student profiles, accounts, academic results,
  reports, and school audit history; can also mark attendance.
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
