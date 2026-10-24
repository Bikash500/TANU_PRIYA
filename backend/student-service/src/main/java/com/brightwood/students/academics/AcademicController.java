package com.brightwood.students.academics;

import java.util.List;

import com.brightwood.students.audit.AuditService;
import com.brightwood.students.security.SchoolUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/academics")
public class AcademicController {

    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;

    public AcademicController(JdbcTemplate jdbcTemplate, AuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    @GetMapping
    public List<AcademicRecord> list(
            @RequestParam(required = false) Long studentId,
            @AuthenticationPrincipal SchoolUserPrincipal user
    ) {
        if (user.role().equals("GUARDIAN")) {
            studentId = user.studentId();
        } else if (!user.role().equals("ADMIN")) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only administrators and guardians can read academic records.");
        }
        String sql = """
                SELECT a.id, a.student_id, s.name AS student_name, s.admission_number,
                       a.subject, a.term, a.score, a.remarks, a.created_at
                FROM academic_records a JOIN students s ON s.id = a.student_id
                WHERE (? IS NULL OR a.student_id = ?)
                ORDER BY a.term DESC, s.name, a.subject
                """;
        Long filterId = studentId;
        return jdbcTemplate.query(sql, (resultSet, row) -> new AcademicRecord(
                resultSet.getLong("id"),
                resultSet.getLong("student_id"),
                resultSet.getString("student_name"),
                resultSet.getString("admission_number"),
                resultSet.getString("subject"),
                resultSet.getString("term"),
                resultSet.getDouble("score"),
                resultSet.getString("remarks"),
                resultSet.getString("created_at")
        ), filterId, filterId);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public AcademicRecord create(
            @Valid @RequestBody AcademicRequest request,
            Authentication authentication,
            @AuthenticationPrincipal SchoolUserPrincipal user
    ) {
        Integer studentExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM students WHERE id = ?", Integer.class, request.studentId());
        if (studentExists == null || studentExists == 0) {
            throw new IllegalArgumentException("Student was not found.");
        }
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO academic_records (student_id, subject, term, score, remarks, recorded_by)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, request.studentId(), request.subject().trim(), request.term().trim(),
                request.score(), trimToNull(request.remarks()), user.id());
        auditService.record(authentication, "CREATE", "ACADEMIC_RECORD", id.toString(),
                request.subject().trim() + " / " + request.term().trim());
        return list(request.studentId(), user).stream()
                .filter(record -> record.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Created academic record could not be read."));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id, Authentication authentication) {
        int deleted = jdbcTemplate.update("DELETE FROM academic_records WHERE id = ?", id);
        if (deleted == 0) {
            throw new IllegalArgumentException("Academic record was not found.");
        }
        auditService.record(authentication, "DELETE", "ACADEMIC_RECORD", Long.toString(id),
                "Deleted academic record");
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record AcademicRequest(
            @NotNull Long studentId,
            @NotBlank @Size(max = 100) String subject,
            @NotBlank @Size(max = 50) String term,
            @NotNull @DecimalMin("0.0") @DecimalMax("100.0") Double score,
            @Size(max = 500) String remarks
    ) {
    }

    public record AcademicRecord(
            Long id,
            long studentId,
            String studentName,
            String admissionNumber,
            String subject,
            String term,
            double score,
            String remarks,
            String createdAt
    ) {
    }
}
