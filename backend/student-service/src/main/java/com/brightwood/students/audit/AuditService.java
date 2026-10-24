package com.brightwood.students.audit;

import java.util.List;

import com.brightwood.students.security.SchoolUserPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final JdbcTemplate jdbcTemplate;

    public AuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(Authentication authentication, String action, String type, String id, String details) {
        SchoolUserPrincipal user = (SchoolUserPrincipal) authentication.getPrincipal();
        jdbcTemplate.update("""
                INSERT INTO audit_logs (user_id, username, action, entity_type, entity_id, details)
                VALUES (?, ?, ?, ?, ?, ?)
                """, user.id(), user.username(), action, type, id, details);
    }

    public List<AuditEntry> findRecent(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        return jdbcTemplate.query("""
                SELECT id, username, action, entity_type, entity_id, details, created_at
                FROM audit_logs ORDER BY id DESC LIMIT ?
                """, (resultSet, row) -> new AuditEntry(
                resultSet.getLong("id"),
                resultSet.getString("username"),
                resultSet.getString("action"),
                resultSet.getString("entity_type"),
                resultSet.getString("entity_id"),
                resultSet.getString("details"),
                resultSet.getString("created_at")
        ), safeLimit);
    }

    public List<AuditEntry> findStudentActivity(long studentId) {
        return jdbcTemplate.query("""
                SELECT id, username, action, entity_type, entity_id, details, created_at
                FROM audit_logs
                WHERE (entity_type IN ('STUDENT', 'ATTENDANCE') AND entity_id = ?)
                   OR (entity_type = 'ACADEMIC_RECORD' AND EXISTS (
                       SELECT 1 FROM academic_records a
                       WHERE CAST(a.id AS TEXT) = audit_logs.entity_id
                         AND a.student_id = ?
                   ))
                ORDER BY id DESC
                LIMIT 100
                """, (resultSet, row) -> new AuditEntry(
                resultSet.getLong("id"),
                resultSet.getString("username"),
                resultSet.getString("action"),
                resultSet.getString("entity_type"),
                resultSet.getString("entity_id"),
                resultSet.getString("details"),
                resultSet.getString("created_at")
        ), Long.toString(studentId), studentId);
    }

    public record AuditEntry(
            long id,
            String username,
            String action,
            String entityType,
            String entityId,
            String details,
            String createdAt
    ) {
    }
}
