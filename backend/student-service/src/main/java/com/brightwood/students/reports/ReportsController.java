package com.brightwood.students.reports;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.brightwood.students.security.SchoolUserPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportsController {

    private final JdbcTemplate jdbcTemplate;

    public ReportsController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public Summary summary() {
        return new Summary(
                count("SELECT COUNT(*) FROM students WHERE status = 'ACTIVE'"),
                count("SELECT COUNT(*) FROM students"),
                count("SELECT COUNT(DISTINCT grade) FROM students"),
                count("SELECT COUNT(*) FROM attendance WHERE attendance_date = date('now') AND status = 'PRESENT'"),
                count("SELECT COUNT(*) FROM academic_records")
        );
    }

    @GetMapping("/students.csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'GUARDIAN')")
    public ResponseEntity<byte[]> studentsCsv(@AuthenticationPrincipal SchoolUserPrincipal user) {
        StringBuilder csv = new StringBuilder("Admission number,Name,Grade,Email,Date of birth,Guardian,Guardian email,Guardian phone,Address,Enrollment date,Status\r\n");
        List<SchoolStudentRow> students;
        if (user.role().equals("GUARDIAN")) {
            students = jdbcTemplate.query("""
                    SELECT admission_number,name,grade,email,date_of_birth,guardian_name,
                           guardian_email,guardian_phone,address,enrollment_date,status
                    FROM students WHERE id = ?
                    """, this::mapStudent, user.studentId());
        } else {
            students = jdbcTemplate.query("""
                    SELECT admission_number,name,grade,email,date_of_birth,guardian_name,
                           guardian_email,guardian_phone,address,enrollment_date,status
                    FROM students ORDER BY grade,name
                    """, this::mapStudent);
        }
        students.forEach(student -> csv.append(row(
                student.admissionNumber(), student.name(), student.grade(), student.email(),
                student.dateOfBirth(), student.guardianName(), student.guardianEmail(),
                student.guardianPhone(), student.address(), student.enrollmentDate(), student.status()
        )));
        return csvResponse(csv, "students.csv");
    }

    @GetMapping("/attendance.csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'GUARDIAN')")
    public ResponseEntity<byte[]> attendanceCsv(
            @RequestParam(required = false) String date,
            @AuthenticationPrincipal SchoolUserPrincipal user
    ) {
        String selectedDate = date == null || date.isBlank() ? java.time.LocalDate.now().toString() : date;
        StringBuilder csv = new StringBuilder("Date,Admission number,Name,Grade,Attendance status\r\n");
        String sql = """
                SELECT a.attendance_date,s.admission_number,s.name,s.grade,a.status
                FROM attendance a JOIN students s ON s.id=a.student_id
                WHERE a.attendance_date = ? AND (? = 0 OR s.id = ?)
                ORDER BY s.grade,s.name
                """;
        Long studentId = user.role().equals("GUARDIAN") ? user.studentId() : 0L;
        jdbcTemplate.query(sql, (ResultSetExtractor<Void>) resultSet -> {
            while (resultSet.next()) {
                csv.append(row(resultSet.getString("attendance_date"),
                        resultSet.getString("admission_number"), resultSet.getString("name"),
                        resultSet.getString("grade"), resultSet.getString("status")));
            }
            return null;
        }, selectedDate, studentId, studentId);
        return csvResponse(csv, "attendance-" + selectedDate + ".csv");
    }

    @GetMapping("/academics.csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'GUARDIAN')")
    public ResponseEntity<byte[]> academicsCsv(@AuthenticationPrincipal SchoolUserPrincipal user) {
        StringBuilder csv = new StringBuilder("Admission number,Student,Grade,Subject,Term,Score,Remarks\r\n");
        String sql = """
                SELECT s.admission_number,s.name,s.grade,a.subject,a.term,a.score,a.remarks
                FROM academic_records a JOIN students s ON s.id=a.student_id
                WHERE (? = 0 OR s.id = ?)
                ORDER BY s.grade,s.name,a.term,a.subject
                """;
        Long studentId = user.role().equals("GUARDIAN") ? user.studentId() : 0L;
        jdbcTemplate.query(sql, (ResultSetExtractor<Void>) resultSet -> {
            while (resultSet.next()) {
                csv.append(row(resultSet.getString("admission_number"), resultSet.getString("name"),
                        resultSet.getString("grade"), resultSet.getString("subject"),
                        resultSet.getString("term"), resultSet.getString("score"),
                        resultSet.getString("remarks")));
            }
            return null;
        }, studentId, studentId);
        return csvResponse(csv, "academic-records.csv");
    }

    private long count(String sql) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class);
        return result == null ? 0 : result;
    }

    private SchoolStudentRow mapStudent(java.sql.ResultSet resultSet, int row) throws java.sql.SQLException {
        return new SchoolStudentRow(resultSet.getString("admission_number"), resultSet.getString("name"),
                resultSet.getString("grade"), resultSet.getString("email"), resultSet.getString("date_of_birth"),
                resultSet.getString("guardian_name"), resultSet.getString("guardian_email"),
                resultSet.getString("guardian_phone"), resultSet.getString("address"),
                resultSet.getString("enrollment_date"), resultSet.getString("status"));
    }

    private static String row(String... values) {
        StringBuilder line = new StringBuilder();
        for (int index = 0; index < values.length; index++) {
            if (index > 0) line.append(',');
            String value = values[index] == null ? "" : values[index];
            if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
                value = "'" + value;
            }
            line.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        return line.append("\r\n").toString();
    }

    private static ResponseEntity<byte[]> csvResponse(StringBuilder csv, String filename) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    public record Summary(
            long activeStudents,
            long totalStudents,
            long gradeCount,
            long presentToday,
            long academicRecords
    ) {
    }

    private record SchoolStudentRow(
            String admissionNumber,
            String name,
            String grade,
            String email,
            String dateOfBirth,
            String guardianName,
            String guardianEmail,
            String guardianPhone,
            String address,
            String enrollmentDate,
            String status
    ) {
    }
}
