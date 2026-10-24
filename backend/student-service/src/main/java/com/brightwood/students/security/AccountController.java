package com.brightwood.students.security;

import java.util.List;

import com.brightwood.students.audit.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.jdbc.core.JdbcTemplate;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class AccountController {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AccountController(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            AuditService auditService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @GetMapping
    public List<Account> list() {
        return jdbcTemplate.query("""
                SELECT u.id, u.username, u.role, u.student_id, s.name AS student_name
                FROM users u LEFT JOIN students s ON s.id = u.student_id
                ORDER BY u.role, u.username
                """, (resultSet, row) -> {
            long studentId = resultSet.getLong("student_id");
            Long linkedStudentId = resultSet.wasNull() ? null : studentId;
            return new Account(resultSet.getLong("id"), resultSet.getString("username"),
                    resultSet.getString("role"), linkedStudentId, resultSet.getString("student_name"));
        });
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Account create(
            @Valid @RequestBody CreateAccountRequest request,
            Authentication authentication
    ) {
        String role = request.role().toUpperCase();
        Long studentId = role.equals("GUARDIAN") ? request.studentId() : null;
        if (role.equals("GUARDIAN") && (studentId == null
                || jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM students WHERE id = ?", Integer.class, studentId) == 0)) {
            throw new IllegalArgumentException("A guardian account must be linked to an existing student.");
        }

        jdbcTemplate.update("""
                INSERT INTO users (username, password_hash, role, student_id)
                VALUES (?, ?, ?, ?)
                """, request.username().trim().toLowerCase(),
                passwordEncoder.encode(request.password()), role, studentId);
        Long createdId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ? COLLATE NOCASE",
                Long.class, request.username().trim());
        if (createdId == null) {
            throw new IllegalStateException("Created account could not be read.");
        }
        long id = createdId;
        auditService.record(authentication, "CREATE", "USER", Long.toString(id),
                "Created " + role + " account " + request.username().trim());
        return new Account(id, request.username().trim(), role, studentId,
                studentId == null ? null : jdbcTemplate.queryForObject(
                        "SELECT name FROM students WHERE id = ?", String.class, studentId));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id, Authentication authentication) {
        SchoolUserPrincipal currentUser = (SchoolUserPrincipal) authentication.getPrincipal();
        if (currentUser.id() == id) {
            throw new IllegalArgumentException("You cannot delete your own signed-in account.");
        }
        String username = jdbcTemplate.query(
                "SELECT username FROM users WHERE id = ?",
                resultSet -> resultSet.next() ? resultSet.getString(1) : null,
                id
        );
        if (username == null) {
            throw new IllegalArgumentException("User account was not found.");
        }
        Integer administratorCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role = 'ADMIN'",
                Integer.class
        );
        String role = jdbcTemplate.queryForObject(
                "SELECT role FROM users WHERE id = ?", String.class, id);
        if ("ADMIN".equals(role) && administratorCount != null && administratorCount <= 1) {
            throw new IllegalArgumentException("The last administrator account cannot be removed.");
        }
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", id);
        auditService.record(authentication, "DELETE", "USER", Long.toString(id),
                "Deleted account " + username);
    }

    public record CreateAccountRequest(
            @NotBlank @Email @Size(max = 254) String username,
            @NotBlank @Size(min = 10, max = 100) String password,
            @NotBlank @Pattern(regexp = "ADMIN|TEACHER|GUARDIAN") String role,
            Long studentId
    ) {
    }

    public record Account(long id, String username, String role, Long studentId, String studentName) {
    }
}
