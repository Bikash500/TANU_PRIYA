package com.brightwood.students.attendance;

import java.time.LocalDate;
import java.util.List;

import com.brightwood.students.audit.AuditService;
import com.brightwood.students.security.SchoolUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;

    public AttendanceController(JdbcTemplate jdbcTemplate, AuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    @GetMapping
    public List<AttendanceEntry> list(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) Long studentId,
            @AuthenticationPrincipal SchoolUserPrincipal user
    ) {
        if (!user.role().equals("ADMIN") && !user.role().equals("TEACHER")
                && !user.role().equals("GUARDIAN")) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Your account cannot read attendance.");
        }
        if (user.role().equals("GUARDIAN") && user.studentId() == null) {
            return List.of();
        }
        if (user.role().equals("GUARDIAN") && date == null) {
            return jdbcTemplate.query("""
                    SELECT s.id AS student_id, s.name, s.admission_number, s.grade,
                           a.id AS attendance_id, a.status, a.attendance_date, a.updated_at
                    FROM attendance a
                    JOIN students s ON s.id = a.student_id
                    WHERE s.id = ?
                    ORDER BY a.attendance_date DESC
                    """, (resultSet, row) -> new AttendanceEntry(
                    resultSet.getLong("student_id"),
                    resultSet.getString("name"),
                    resultSet.getString("admission_number"),
                    resultSet.getString("grade"),
                    nullableLong(resultSet, "attendance_id"),
                    resultSet.getString("status"),
                    LocalDate.parse(resultSet.getString("attendance_date")),
                    resultSet.getString("updated_at")
            ), user.studentId());
        }
        LocalDate attendanceDate = date == null ? LocalDate.now() : date;
        boolean guardian = user.role().equals("GUARDIAN");
        if (guardian) {
            studentId = user.studentId();
        }
        String sql = """
                SELECT s.id AS student_id, s.name, s.admission_number, s.grade,
                       a.id AS attendance_id, a.status, a.attendance_date, a.updated_at
                FROM students s
                LEFT JOIN attendance a ON a.student_id = s.id AND a.attendance_date = ?
                WHERE (? IS NULL OR s.id = ?)
                  AND (? IS NULL OR s.grade = ?)
                ORDER BY s.grade, s.name
                """;
        Long filterStudent = studentId;
        String filterGrade = guardian ? null : grade;
        return jdbcTemplate.query(sql, (resultSet, row) -> new AttendanceEntry(
                resultSet.getLong("student_id"),
                resultSet.getString("name"),
                resultSet.getString("admission_number"),
                resultSet.getString("grade"),
                nullableLong(resultSet, "attendance_id"),
                resultSet.getString("status") == null ? "UNMARKED" : resultSet.getString("status"),
                attendanceDate,
                resultSet.getString("updated_at")
        ), attendanceDate.toString(), filterStudent, filterStudent, filterGrade, filterGrade);
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @ResponseStatus(HttpStatus.OK)
    public AttendanceEntry upsert(
            @Valid @RequestBody AttendanceRequest request,
            Authentication authentication,
            @AuthenticationPrincipal SchoolUserPrincipal user
    ) {
        if (jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM students WHERE id = ?", Integer.class, request.studentId()) == 0) {
            throw new IllegalArgumentException("Student was not found.");
        }
        jdbcTemplate.update("""
                INSERT INTO attendance (student_id, attendance_date, status, recorded_by)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(student_id, attendance_date) DO UPDATE SET
                    status = excluded.status, recorded_by = excluded.recorded_by,
                    updated_at = CURRENT_TIMESTAMP
                """, request.studentId(), request.date().toString(), request.status(), user.id());
        auditService.record(authentication, "UPSERT", "ATTENDANCE", request.studentId().toString(),
                request.date() + " marked " + request.status());
        return list(request.date(), null, request.studentId(), user).getFirst();
    }

    public record AttendanceRequest(
            @NotNull Long studentId,
            @NotNull LocalDate date,
            @NotBlank @Pattern(regexp = "PRESENT|ABSENT|LATE|EXCUSED") String status
    ) {
    }

    public record AttendanceEntry(
            long studentId,
            String studentName,
            String admissionNumber,
            String grade,
            Long attendanceId,
            String status,
            LocalDate date,
            String updatedAt
    ) {
    }

    private static Long nullableLong(java.sql.ResultSet resultSet, String column)
            throws java.sql.SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }
}
