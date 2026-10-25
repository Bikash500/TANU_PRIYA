package com.brightwood.students.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

@Component
@Profile("!production")
public class DatabaseInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        migrateStudents();
        createModuleTables();
        seedDemoStudents();
        seedAcademicStructure();
        seedDemoUsers();
    }

    private void migrateStudents() {
        Set<String> columns = new HashSet<>(jdbcTemplate.query(
                "PRAGMA table_info(students)",
                (resultSet, row) -> resultSet.getString("name")
        ));
        addColumn(columns, "admission_number", "TEXT");
        addColumn(columns, "date_of_birth", "TEXT");
        addColumn(columns, "guardian_name", "TEXT");
        addColumn(columns, "guardian_email", "TEXT");
        addColumn(columns, "guardian_phone", "TEXT");
        addColumn(columns, "address", "TEXT");
        addColumn(columns, "enrollment_date", "TEXT");
        addColumn(columns, "status", "TEXT NOT NULL DEFAULT 'ACTIVE'");

        jdbcTemplate.update("""
                UPDATE students
                SET admission_number = 'PTS-' || printf('%05d', id)
                WHERE admission_number IS NULL OR trim(admission_number) = ''
                """);
        jdbcTemplate.update("""
                UPDATE students
                SET enrollment_date = date('now')
                WHERE enrollment_date IS NULL OR trim(enrollment_date) = ''
                """);
        jdbcTemplate.update("""
                UPDATE students
                SET status = 'ACTIVE'
                WHERE status IS NULL OR trim(status) = ''
                """);
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_students_admission_number
                ON students(admission_number COLLATE NOCASE)
                """);
    }

    private void addColumn(Set<String> existingColumns, String column, String definition) {
        if (!existingColumns.contains(column)) {
            jdbcTemplate.execute("ALTER TABLE students ADD COLUMN " + column + " " + definition);
        }
    }

    private void createModuleTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL UNIQUE COLLATE NOCASE,
                    password_hash TEXT NOT NULL,
                    role TEXT NOT NULL CHECK (role IN ('ADMIN', 'TEACHER', 'GUARDIAN')),
                    student_id INTEGER REFERENCES students(id) ON DELETE SET NULL,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS attendance (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    student_id INTEGER NOT NULL REFERENCES students(id) ON DELETE CASCADE,
                    attendance_date TEXT NOT NULL,
                    status TEXT NOT NULL CHECK (status IN ('PRESENT', 'ABSENT', 'LATE', 'EXCUSED')),
                    recorded_by INTEGER REFERENCES users(id) ON DELETE SET NULL,
                    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(student_id, attendance_date)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS academic_records (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    student_id INTEGER NOT NULL REFERENCES students(id) ON DELETE CASCADE,
                    subject TEXT NOT NULL,
                    term TEXT NOT NULL,
                    score REAL NOT NULL CHECK (score >= 0 AND score <= 100),
                    remarks TEXT,
                    recorded_by INTEGER REFERENCES users(id) ON DELETE SET NULL,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS audit_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER REFERENCES users(id) ON DELETE SET NULL,
                    username TEXT NOT NULL,
                    action TEXT NOT NULL,
                    entity_type TEXT NOT NULL,
                    entity_id TEXT,
                    details TEXT,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS academic_years (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE COLLATE NOCASE,
                    starts_on TEXT NOT NULL,
                    ends_on TEXT NOT NULL,
                    is_current INTEGER NOT NULL DEFAULT 0 CHECK (is_current IN (0, 1)),
                    CHECK (starts_on <= ends_on)
                )
                """);
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_academic_years_one_current
                ON academic_years(is_current) WHERE is_current = 1
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS class_sections (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    academic_year_id INTEGER NOT NULL REFERENCES academic_years(id) ON DELETE CASCADE,
                    grade TEXT NOT NULL,
                    name TEXT NOT NULL COLLATE NOCASE,
                    room TEXT,
                    UNIQUE(academic_year_id, grade, name)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS student_enrollments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    student_id INTEGER NOT NULL REFERENCES students(id) ON DELETE CASCADE,
                    academic_year_id INTEGER NOT NULL REFERENCES academic_years(id) ON DELETE CASCADE,
                    class_section_id INTEGER NOT NULL REFERENCES class_sections(id) ON DELETE RESTRICT,
                    status TEXT NOT NULL DEFAULT 'ENROLLED'
                        CHECK (status IN ('ENROLLED', 'COMPLETED', 'WITHDRAWN')),
                    UNIQUE(student_id, academic_year_id)
                )
                """);
    }

    private void seedAcademicStructure() {
        jdbcTemplate.update("""
                INSERT OR IGNORE INTO academic_years (name, starts_on, ends_on, is_current)
                VALUES ('2026-27', '2026-04-01', '2027-03-31', 1)
                """);
        Long academicYearId = jdbcTemplate.query(
                "SELECT id FROM academic_years WHERE name = '2026-27' COLLATE NOCASE",
                resultSet -> resultSet.next() ? resultSet.getLong(1) : null
        );
        if (academicYearId == null) {
            throw new IllegalStateException("Could not initialize the 2026-27 academic year.");
        }

        for (int gradeNumber = 1; gradeNumber <= 12; gradeNumber++) {
            String grade = "Grade " + gradeNumber;
            jdbcTemplate.update("""
                    INSERT OR IGNORE INTO class_sections (academic_year_id, grade, name)
                    VALUES (?, ?, 'A')
                    """, academicYearId, grade);
        }
        jdbcTemplate.update("""
                INSERT OR IGNORE INTO student_enrollments (
                    student_id, academic_year_id, class_section_id, status
                )
                SELECT s.id, ?, c.id, 'ENROLLED'
                FROM students s
                JOIN class_sections c
                  ON c.academic_year_id = ? AND c.grade = s.grade AND c.name = 'A'
                """, academicYearId, academicYearId);
    }

    private void seedDemoUsers() {
        addDemoUser("admin@pandey.edu", "ADMIN", "DEMO_ADMIN_PASSWORD");
        addDemoUser("teacher@pandey.edu", "TEACHER", "DEMO_TEACHER_PASSWORD");
        Long studentId = jdbcTemplate.query(
                "SELECT id FROM students ORDER BY id LIMIT 1",
                resultSet -> resultSet.next() ? resultSet.getLong(1) : null
        );
        if (studentId != null) {
            addDemoGuardian("guardian@pandey.edu", "DEMO_GUARDIAN_PASSWORD", studentId);
            jdbcTemplate.update("""
                    UPDATE students
                    SET guardian_name = COALESCE(NULLIF(guardian_name, ''), 'Demo Guardian'),
                        guardian_email = COALESCE(NULLIF(guardian_email, ''), 'guardian@pandey.edu')
                    WHERE id = ?
                    """, studentId);
        }
    }

    private void seedDemoStudents() {
        List<DemoStudent> demoStudents = List.of(
                new DemoStudent("Aarav Sharma", "Grade 1", "aarav.sharma@pandey.edu", "Anita Sharma", "9876500101", "2019-03-14"),
                new DemoStudent("Aadhya Verma", "Grade 2", "aadhya.verma@pandey.edu", "Rajesh Verma", "9876500102", "2018-07-22"),
                new DemoStudent("Vihaan Gupta", "Grade 3", "vihaan.gupta@pandey.edu", "Pooja Gupta", "9876500103", "2017-11-05"),
                new DemoStudent("Ananya Patel", "Grade 4", "ananya.patel@pandey.edu", "Kiran Patel", "9876500104", "2016-02-18"),
                new DemoStudent("Ishaan Reddy", "Grade 5", "ishaan.reddy@pandey.edu", "Lakshmi Reddy", "9876500105", "2015-09-09"),
                new DemoStudent("Saanvi Iyer", "Grade 6", "saanvi.iyer@pandey.edu", "Meera Iyer", "9876500106", "2014-05-27"),
                new DemoStudent("Reyansh Singh", "Grade 7", "reyansh.singh@pandey.edu", "Neha Singh", "9876500107", "2013-12-11"),
                new DemoStudent("Diya Nair", "Grade 8", "diya.nair@pandey.edu", "Arun Nair", "9876500108", "2012-04-03"),
                new DemoStudent("Kabir Joshi", "Grade 9", "kabir.joshi@pandey.edu", "Sunita Joshi", "9876500109", "2011-08-16"),
                new DemoStudent("Myra Kulkarni", "Grade 10", "myra.kulkarni@pandey.edu", "Sanjay Kulkarni", "9876500110", "2010-01-29"),
                new DemoStudent("Advik Das", "Grade 11", "advik.das@pandey.edu", "Ritu Das", "9876500111", "2009-06-07"),
                new DemoStudent("Pari Chatterjee", "Grade 12", "pari.chatterjee@pandey.edu", "Amit Chatterjee", "9876500112", "2008-10-20"),
                new DemoStudent("Arjun Mehta", "Grade 3", "arjun.mehta@pandey.edu", "Nisha Mehta", "9876500113", "2017-03-30"),
                new DemoStudent("Ira Krishnan", "Grade 4", "ira.krishnan@pandey.edu", "Deepa Krishnan", "9876500114", "2016-09-12"),
                new DemoStudent("Dhruv Malhotra", "Grade 5", "dhruv.malhotra@pandey.edu", "Rohan Malhotra", "9876500115", "2015-12-02"),
                new DemoStudent("Tara Banerjee", "Grade 6", "tara.banerjee@pandey.edu", "Shreya Banerjee", "9876500116", "2014-01-24"),
                new DemoStudent("Rohan Kapoor", "Grade 7", "rohan.kapoor@pandey.edu", "Asha Kapoor", "9876500117", "2013-07-15"),
                new DemoStudent("Avni Shah", "Grade 8", "avni.shah@pandey.edu", "Harish Shah", "9876500118", "2012-11-28"),
                new DemoStudent("Kian Fernandes", "Grade 9", "kian.fernandes@pandey.edu", "Maria Fernandes", "9876500119", "2011-05-19"),
                new DemoStudent("Navya Rao", "Grade 10", "navya.rao@pandey.edu", "Suresh Rao", "9876500120", "2010-08-31"),
                new DemoStudent("Atharv Mishra", "Grade 11", "atharv.mishra@pandey.edu", "Kavita Mishra", "9876500121", "2009-02-14"),
                new DemoStudent("Kiara Sethi", "Grade 12", "kiara.sethi@pandey.edu", "Manoj Sethi", "9876500122", "2008-06-26"),
                new DemoStudent("Yash Thakur", "Grade 2", "yash.thakur@pandey.edu", "Priya Thakur", "9876500123", "2018-10-08"),
                new DemoStudent("Zoya Khan", "Grade 5", "zoya.khan@pandey.edu", "Sameer Khan", "9876500124", "2015-04-21")
        );

        for (int index = 0; index < demoStudents.size(); index++) {
            DemoStudent student = demoStudents.get(index);
            String admissionNumber = "PTS-DEMO-%03d".formatted(index + 1);
            String guardianEmail = "guardian%02d@pandey.edu".formatted(index + 1);
            jdbcTemplate.update("""
                    INSERT OR IGNORE INTO students (
                        admission_number, name, grade, email, date_of_birth,
                        guardian_name, guardian_email, guardian_phone, address,
                        enrollment_date, status
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, date('now'), 'ACTIVE')
                    """, admissionNumber, student.name(), student.grade(), student.email(),
                    student.dateOfBirth(), student.guardianName(),
                    index < 6 ? guardianEmail : student.email().replace("@pandey.edu", "@family.example"),
                    student.guardianPhone(), "Pandey Nagar, India");

            if (index < 6) {
                Long studentId = jdbcTemplate.query(
                        "SELECT id FROM students WHERE admission_number = ?",
                        resultSet -> resultSet.next() ? resultSet.getLong(1) : null,
                        admissionNumber
                );
                if (studentId == null) {
                    throw new IllegalStateException("Could not find seeded demo student " + admissionNumber);
                }
                addDemoGuardian(guardianEmail, "DEMO_GUARDIAN_PASSWORD", studentId);
            }
        }
    }

    private void addDemoUser(String username, String role, String passwordVariable) {
        String password = System.getenv().getOrDefault(passwordVariable, defaultDemoPassword(role));
        jdbcTemplate.update("""
                INSERT OR IGNORE INTO users (username, password_hash, role)
                VALUES (?, ?, ?)
                """, username, passwordEncoder.encode(password), role);
    }

    private void addDemoGuardian(String username, String passwordVariable, long studentId) {
        String password = System.getenv().getOrDefault(passwordVariable, "GuardianDemo2026!");
        jdbcTemplate.update("""
                INSERT OR IGNORE INTO users (username, password_hash, role, student_id)
                VALUES (?, ?, 'GUARDIAN', ?)
                """, username, passwordEncoder.encode(password), studentId);
    }

    private static String defaultDemoPassword(String role) {
        return switch (role) {
            case "ADMIN" -> "AdminDemo2026!";
            case "TEACHER" -> "TeacherDemo2026!";
            default -> throw new IllegalArgumentException("Unsupported demo role: " + role);
        };
    }

    private record DemoStudent(
            String name,
            String grade,
            String email,
            String guardianName,
            String guardianPhone,
            String dateOfBirth
    ) {
    }
}
