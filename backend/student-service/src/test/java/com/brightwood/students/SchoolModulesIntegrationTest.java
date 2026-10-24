package com.brightwood.students;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.Objects;

import com.brightwood.students.student.Student;
import org.springframework.lang.NonNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SchoolModulesIntegrationTest {

    private static final String DATABASE_PATH = System.getProperty("java.io.tmpdir")
            + "/pandey-school-test-" + UUID.randomUUID() + ".sqlite";

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE_PATH);
    }

    @Test
    void adminCanCreateAndReadCompleteStudentProfile() {
        String admissionNumber = "TEST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        HttpEntity<String> request = jsonRequest("""
                {
                  "admissionNumber": "%s",
                  "name": "Integration Student",
                  "grade": "Grade 7",
                  "email": "student@example.edu",
                  "dateOfBirth": "2012-05-04",
                  "guardianName": "Demo Parent",
                  "guardianEmail": "parent@example.edu",
                  "guardianPhone": "555-0100",
                  "address": "12 School Road",
                  "enrollmentDate": "2026-01-15",
                  "status": "ACTIVE"
                }
                """.formatted(admissionNumber), "admin@pandey.edu", "AdminDemo2026!");
        ResponseEntity<Student> created = restTemplate.withBasicAuth("admin@pandey.edu", "AdminDemo2026!")
                .postForEntity("/api/students", request, Student.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Student createdStudent = Objects.requireNonNull(created.getBody());
        assertThat(createdStudent.admissionNumber()).isEqualTo(admissionNumber);
        assertThat(createdStudent.guardianName()).isEqualTo("Demo Parent");

        try {
            Student[] fetched = restTemplate.withBasicAuth("admin@pandey.edu", "AdminDemo2026!")
                    .getForObject("/api/students", Student[].class);
            assertThat(fetched).anySatisfy(student -> {
                assertThat(student.admissionNumber()).isEqualTo(admissionNumber);
                assertThat(student.dateOfBirth()).hasToString("2012-05-04");
            });
        } finally {
            restTemplate.withBasicAuth("admin@pandey.edu", "AdminDemo2026!")
                    .delete("/api/students/{id}", createdStudent.id());
        }
    }

    @Test
    void teacherCanRecordAttendanceButCannotManageStudentProfilesOrAcademics() {
        Student student = guardianLinkedStudent();
        String teacher = "teacher@pandey.edu";
        HttpEntity<String> attendance = jsonRequest("""
                {"studentId": %d, "date": "2026-10-24", "status": "PRESENT"}
                """.formatted(student.id()), teacher, "TeacherDemo2026!");
        ResponseEntity<String> attendanceResult = restTemplate.withBasicAuth(teacher, "TeacherDemo2026!")
                .exchange("/api/attendance", HttpMethod.PUT, attendance, String.class);
        assertThat(attendanceResult.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(attendanceResult.getBody()).contains("PRESENT");

        HttpEntity<String> academic = jsonRequest("""
                {"studentId": %d, "subject": "Mathematics", "term": "Term 1",
                 "score": 88.5, "remarks": "Good progress"}
                """.formatted(student.id()), teacher, "TeacherDemo2026!");
        ResponseEntity<String> academicResult = restTemplate.withBasicAuth(teacher, "TeacherDemo2026!")
                .postForEntity("/api/academics", academic, String.class);
        assertThat(academicResult.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> studentList = restTemplate.withBasicAuth(teacher, "TeacherDemo2026!")
                .getForEntity("/api/students", String.class);
        assertThat(studentList.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        HttpEntity<String> studentProfile = jsonRequest("""
                {"admissionNumber":"NO-TEACHER-001","name":"Unauthorized Student",
                 "grade":"Grade 7","email":"unauthorized@example.edu","status":"ACTIVE"}
                """, teacher, "TeacherDemo2026!");
        ResponseEntity<String> studentCreate = restTemplate.withBasicAuth(teacher, "TeacherDemo2026!")
                .postForEntity("/api/students", studentProfile, String.class);
        assertThat(studentCreate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        HttpEntity<String> studentUpdate = jsonRequest("""
                {"admissionNumber":"%s","name":"Unauthorized Update",
                 "grade":"Grade 7","email":"updated@example.edu","status":"ACTIVE"}
                """.formatted(student.admissionNumber()), teacher, "TeacherDemo2026!");
        ResponseEntity<String> studentEdit = restTemplate.withBasicAuth(teacher, "TeacherDemo2026!")
                .exchange("/api/students/{id}", HttpMethod.PUT, studentUpdate, String.class, student.id());
        assertThat(studentEdit.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> studentDelete = restTemplate.withBasicAuth(teacher, "TeacherDemo2026!")
                .exchange("/api/students/{id}", HttpMethod.DELETE, HttpEntity.EMPTY, String.class, student.id());
        assertThat(studentDelete.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> academicList = restTemplate.withBasicAuth(teacher, "TeacherDemo2026!")
                .getForEntity("/api/academics", String.class);
        assertThat(academicList.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> reports = restTemplate.withBasicAuth(teacher, "TeacherDemo2026!")
                .getForEntity("/api/reports/summary", String.class);
        assertThat(reports.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void guardianCanOnlyReadLinkedStudentAndCannotReadAdminModules() {
        ResponseEntity<Student[]> linkedStudents = restTemplate.withBasicAuth(
                        "guardian@pandey.edu", "GuardianDemo2026!")
                .getForEntity("/api/students", Student[].class);
        assertThat(linkedStudents.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(linkedStudents.getBody()).hasSize(1);

        ResponseEntity<String> forbiddenAccounts = restTemplate.withBasicAuth(
                        "teacher@pandey.edu", "TeacherDemo2026!")
                .getForEntity("/api/users", String.class);
        assertThat(forbiddenAccounts.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> guardianSummary = restTemplate.withBasicAuth(
                        "guardian@pandey.edu", "GuardianDemo2026!")
                .getForEntity("/api/reports/summary", String.class);
        assertThat(guardianSummary.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> guardianAudit = restTemplate.withBasicAuth(
                        "guardian@pandey.edu", "GuardianDemo2026!")
                .getForEntity("/api/audit", String.class);
        assertThat(guardianAudit.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void guardianSeesOnlyLinkedStudentsAttendanceAcademicResultsAndActivity() {
        Student linkedStudent = guardianLinkedStudent();
        Student[] allStudents = restTemplate.withBasicAuth("admin@pandey.edu", "AdminDemo2026!")
                .getForObject("/api/students", Student[].class);
        Student otherStudent = java.util.Arrays.stream(allStudents)
                .filter(student -> student.id() != linkedStudent.id())
                .findFirst()
                .orElseThrow();

        HttpEntity<String> linkedAttendance = jsonRequest("""
                {"studentId": %d, "date": "2026-10-20", "status": "PRESENT"}
                """.formatted(linkedStudent.id()), "teacher@pandey.edu", "TeacherDemo2026!");
        restTemplate.withBasicAuth("teacher@pandey.edu", "TeacherDemo2026!")
                .exchange("/api/attendance", HttpMethod.PUT, linkedAttendance, String.class);

        HttpEntity<String> otherAttendance = jsonRequest("""
                {"studentId": %d, "date": "2026-10-20", "status": "ABSENT"}
                """.formatted(otherStudent.id()), "teacher@pandey.edu", "TeacherDemo2026!");
        restTemplate.withBasicAuth("teacher@pandey.edu", "TeacherDemo2026!")
                .exchange("/api/attendance", HttpMethod.PUT, otherAttendance, String.class);

        HttpEntity<String> linkedAcademic = jsonRequest("""
                {"studentId": %d, "subject": "Science", "term": "Term 1",
                 "score": 92, "remarks": "Excellent progress"}
                """.formatted(linkedStudent.id()), "admin@pandey.edu", "AdminDemo2026!");
        restTemplate.withBasicAuth("admin@pandey.edu", "AdminDemo2026!")
                .postForEntity("/api/academics", linkedAcademic, String.class);

        ResponseEntity<String> guardianAttendance = restTemplate.withBasicAuth(
                        "guardian@pandey.edu", "GuardianDemo2026!")
                .getForEntity("/api/attendance", String.class);
        assertThat(guardianAttendance.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(guardianAttendance.getBody()).contains(linkedStudent.name()).contains("PRESENT")
                .doesNotContain(otherStudent.name());

        ResponseEntity<String> guardianAcademics = restTemplate.withBasicAuth(
                        "guardian@pandey.edu", "GuardianDemo2026!")
                .getForEntity("/api/academics", String.class);
        assertThat(guardianAcademics.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(guardianAcademics.getBody()).contains("Excellent progress")
                .doesNotContain(otherStudent.name());

        ResponseEntity<String> guardianActivity = restTemplate.withBasicAuth(
                        "guardian@pandey.edu", "GuardianDemo2026!")
                .getForEntity("/api/audit/student-activity", String.class);
        assertThat(guardianActivity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(guardianActivity.getBody()).contains("marked PRESENT", "Science / Term 1")
                .doesNotContain(otherStudent.name())
                .doesNotContain("ABSENT");
    }

    @Test
    void demoRosterAndGuardianAccountsAreSeededAndScoped() {
        Student[] students = restTemplate.withBasicAuth("admin@pandey.edu", "AdminDemo2026!")
                .getForObject("/api/students", Student[].class);
        assertThat(students).filteredOn(student -> student.admissionNumber().startsWith("PTS-DEMO-"))
                .hasSize(24);

        Student[] guardianStudents = restTemplate.withBasicAuth("guardian01@pandey.edu", "GuardianDemo2026!")
                .getForObject("/api/students", Student[].class);
        assertThat(guardianStudents).hasSize(1);
        assertThat(guardianStudents[0].admissionNumber()).isEqualTo("PTS-DEMO-001");
        assertThat(guardianStudents[0].name()).isEqualTo("Aarav Sharma");
    }

    @Test
    void invalidAcademicScoreIsRejected() {
        Student student = guardianLinkedStudent();
        HttpEntity<String> request = jsonRequest("""
                {"studentId": %d, "subject": "Science", "term": "Term 1",
                 "score": 140, "remarks": ""}
                """.formatted(student.id()), "teacher@pandey.edu", "TeacherDemo2026!");
        ResponseEntity<String> response = restTemplate.withBasicAuth(
                        "teacher@pandey.edu", "TeacherDemo2026!")
                .postForEntity("/api/academics", request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void reportCsvEndpointsReturnValidCsvAndScopeGuardianRows() {
        String guardian = "guardian@pandey.edu";
        String admin = "admin@pandey.edu";
        Student linkedStudent = guardianLinkedStudent();

        ResponseEntity<String> studentCsv = restTemplate.withBasicAuth(admin, "AdminDemo2026!")
                .getForEntity("/api/reports/students.csv", String.class);
        assertThat(studentCsv.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(studentCsv.getHeaders().getContentType()).toString())
                .startsWith("text/csv");
        assertThat(studentCsv.getBody()).contains("Admission number,Name").contains(linkedStudent.name());

        ResponseEntity<String> attendanceCsv = restTemplate.withBasicAuth(admin, "AdminDemo2026!")
                .getForEntity("/api/reports/attendance.csv", String.class);
        assertThat(attendanceCsv.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(attendanceCsv.getBody()).contains("Date,Admission number,Name");

        ResponseEntity<String> academicCsv = restTemplate.withBasicAuth(admin, "AdminDemo2026!")
                .getForEntity("/api/reports/academics.csv", String.class);
        assertThat(academicCsv.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(academicCsv.getBody()).contains("Admission number,Student,Grade");

        ResponseEntity<String> guardianStudentCsv = restTemplate.withBasicAuth(guardian, "GuardianDemo2026!")
                .getForEntity("/api/reports/students.csv", String.class);
        assertThat(guardianStudentCsv.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(guardianStudentCsv.getBody()).contains(linkedStudent.name());
        assertThat(guardianStudentCsv.getBody()).doesNotContain("Integration Student");
    }

    private Student guardianLinkedStudent() {
        return restTemplate.withBasicAuth("guardian@pandey.edu", "GuardianDemo2026!")
                .getForObject("/api/students", Student[].class)[0];
    }

    private static HttpEntity<String> jsonRequest(
            String body,
            @NonNull String username,
            @NonNull String password
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(username, password);
        return new HttpEntity<>(Objects.requireNonNull(body), headers);
    }
}
