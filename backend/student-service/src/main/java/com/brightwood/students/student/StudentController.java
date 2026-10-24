package com.brightwood.students.student;

import java.util.List;

import com.brightwood.students.audit.AuditService;
import com.brightwood.students.security.SchoolUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class StudentController {

    private final StudentService studentService;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;

    public StudentController(StudentService studentService, AuditService auditService, JdbcTemplate jdbcTemplate) {
        this.studentService = studentService;
        this.auditService = auditService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("ok");
    }

    @GetMapping("/students")
    public List<Student> findAll(
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort,
            @AuthenticationPrincipal SchoolUserPrincipal user
    ) {
        if (user.role().equals("TEACHER")) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Teachers can access the student roster through attendance only.");
        }
        List<Student> students = studentService.findAll();
        if (user.role().equals("GUARDIAN")) {
            return students.stream()
                    .filter(student -> user.studentId() != null && student.id() == user.studentId())
                    .toList();
        }
        return students.stream()
                .filter(student -> grade == null || grade.isBlank() || student.grade().equals(grade))
                .filter(student -> status == null || status.isBlank() || student.status().equals(status))
                .sorted(switch (sort == null ? "name" : sort) {
                    case "grade" -> java.util.Comparator.comparing(Student::grade).thenComparing(Student::name);
                    case "admissionNumber" -> java.util.Comparator.comparing(Student::admissionNumber);
                    case "newest" -> java.util.Comparator.comparingLong(Student::id).reversed();
                    default -> java.util.Comparator.comparing(Student::name, String.CASE_INSENSITIVE_ORDER);
                })
                .toList();
    }

    @PostMapping("/students")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public Student create(
            @Valid @RequestBody StudentRequest request,
            Authentication authentication
    ) {
        Student student = studentService.create(request);
        auditService.record(authentication, "CREATE", "STUDENT", Long.toString(student.id()),
                "Created student " + student.admissionNumber());
        return student;
    }

    @PutMapping("/students/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Student update(
            @PathVariable long id,
            @Valid @RequestBody StudentRequest request,
            Authentication authentication
    ) {
        Student student = studentService.update(id, request);
        auditService.record(authentication, "UPDATE", "STUDENT", Long.toString(id),
                "Updated student " + student.admissionNumber());
        return student;
    }

    @DeleteMapping("/students/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id, Authentication authentication) {
        String admissionNumber = jdbcTemplate.query(
                "SELECT admission_number FROM students WHERE id = ?",
                resultSet -> resultSet.next() ? resultSet.getString(1) : null, id);
        studentService.delete(id);
        auditService.record(authentication, "DELETE", "STUDENT", Long.toString(id),
                "Deleted student " + admissionNumber);
    }

    public record HealthResponse(String status) {
    }
}
