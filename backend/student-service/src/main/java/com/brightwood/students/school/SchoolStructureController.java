package com.brightwood.students.school;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import com.brightwood.students.audit.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
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
@RequestMapping("/api/school")
public class SchoolStructureController {

    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;

    public SchoolStructureController(JdbcTemplate jdbcTemplate, AuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    @GetMapping("/academic-years")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public List<AcademicYear> academicYears() {
        return jdbcTemplate.query("""
                SELECT id, name, starts_on, ends_on, is_current
                FROM academic_years
                ORDER BY starts_on DESC, id DESC
                """, (resultSet, row) -> new AcademicYear(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                LocalDate.parse(resultSet.getString("starts_on")),
                LocalDate.parse(resultSet.getString("ends_on")),
                resultSet.getBoolean("is_current")
        ));
    }

    @PostMapping("/academic-years")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public AcademicYear createAcademicYear(
            @Valid @RequestBody AcademicYearRequest request,
            Authentication authentication
    ) {
        validateDateRange(request.startsOn(), request.endsOn());
        jdbcTemplate.update("""
                INSERT INTO academic_years (name, starts_on, ends_on)
                VALUES (?, ?, ?)
                """, request.name().trim(), request.startsOn().toString(), request.endsOn().toString());
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM academic_years WHERE lower(name) = lower(?)",
                Long.class, request.name().trim());
        if (id == null) {
            throw new IllegalStateException("The academic year was created but could not be loaded.");
        }
        auditService.record(authentication, "CREATE", "ACADEMIC_YEAR", id.toString(),
                "Created academic year " + request.name().trim());
        return findAcademicYear(id);
    }

    @PutMapping("/academic-years/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public AcademicYear activateAcademicYear(@PathVariable long id, Authentication authentication) {
        AcademicYear year = findAcademicYear(id);
        jdbcTemplate.update("UPDATE academic_years SET is_current = 0 WHERE is_current = 1");
        jdbcTemplate.update("UPDATE academic_years SET is_current = 1 WHERE id = ?", id);
        jdbcTemplate.update("""
                UPDATE students
                SET grade = (
                    SELECT c.grade
                    FROM student_enrollments e
                    JOIN class_sections c ON c.id = e.class_section_id
                    WHERE e.student_id = students.id
                      AND e.academic_year_id = ?
                      AND e.status = 'ENROLLED'
                )
                WHERE EXISTS (
                    SELECT 1 FROM student_enrollments e
                    WHERE e.student_id = students.id
                      AND e.academic_year_id = ?
                      AND e.status = 'ENROLLED'
                )
                """, id, id);
        auditService.record(authentication, "ACTIVATE", "ACADEMIC_YEAR", Long.toString(id),
                "Activated academic year " + year.name());
        return findAcademicYear(id);
    }

    @GetMapping("/sections")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public List<ClassSection> sections(@RequestParam long academicYearId) {
        requireAcademicYear(academicYearId);
        return jdbcTemplate.query("""
                SELECT c.id, c.academic_year_id, y.name AS academic_year,
                       c.grade, c.name, c.room,
                       (SELECT COUNT(*) FROM student_enrollments e
                        WHERE e.class_section_id = c.id AND e.status = 'ENROLLED') AS enrolled_count,
                       (SELECT COUNT(*) FROM student_enrollments e
                        WHERE e.class_section_id = c.id) AS enrollment_count
                FROM class_sections c
                JOIN academic_years y ON y.id = c.academic_year_id
                WHERE c.academic_year_id = ?
                ORDER BY c.grade, c.name
                """, (resultSet, row) -> new ClassSection(
                resultSet.getLong("id"),
                resultSet.getLong("academic_year_id"),
                resultSet.getString("academic_year"),
                resultSet.getString("grade"),
                resultSet.getString("name"),
                resultSet.getString("room"),
                resultSet.getInt("enrolled_count"),
                resultSet.getInt("enrollment_count")
        ), academicYearId);
    }

    @PostMapping("/sections")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ClassSection createSection(
            @Valid @RequestBody ClassSectionRequest request,
            Authentication authentication
    ) {
        requireAcademicYear(request.academicYearId());
        jdbcTemplate.update("""
                INSERT INTO class_sections (academic_year_id, grade, name, room)
                VALUES (?, ?, ?, ?)
                """, request.academicYearId(), request.grade(), normalizeSectionName(request.name()),
                blankToNull(request.room()));
        Long id = jdbcTemplate.queryForObject("""
                SELECT id FROM class_sections
                WHERE academic_year_id = ? AND grade = ? AND lower(name) = lower(?)
                """, Long.class, request.academicYearId(), request.grade(), normalizeSectionName(request.name()));
        if (id == null) {
            throw new IllegalStateException("The class section was created but could not be loaded.");
        }
        auditService.record(authentication, "CREATE", "CLASS_SECTION", id.toString(),
                request.grade() + " / " + normalizeSectionName(request.name()));
        return findSection(id);
    }

    @PutMapping("/sections/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ClassSection updateSection(
            @PathVariable long id,
            @Valid @RequestBody UpdateClassSectionRequest request,
            Authentication authentication
    ) {
        ClassSection current = findSection(id);
        String name = normalizeSectionName(request.name());
        jdbcTemplate.update("""
                UPDATE class_sections SET name = ?, room = ?
                WHERE id = ?
                """, name, blankToNull(request.room()), id);
        auditService.record(authentication, "UPDATE", "CLASS_SECTION", Long.toString(id),
                "Updated " + current.grade() + " / " + name);
        return findSection(id);
    }

    @DeleteMapping("/sections/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSection(@PathVariable long id, Authentication authentication) {
        ClassSection section = findSection(id);
        Integer enrollmentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_enrollments WHERE class_section_id = ?",
                Integer.class, id);
        if (enrollmentCount != null && enrollmentCount > 0) {
            throw new IllegalArgumentException("A section with enrollment history cannot be deleted.");
        }
        jdbcTemplate.update("DELETE FROM class_sections WHERE id = ?", id);
        auditService.record(authentication, "DELETE", "CLASS_SECTION", Long.toString(id),
                section.grade() + " / " + section.name());
    }

    @GetMapping("/enrollments")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Enrollment> enrollments(@RequestParam long academicYearId) {
        requireAcademicYear(academicYearId);
        return jdbcTemplate.query("""
                SELECT e.id, e.academic_year_id, e.student_id, s.name AS student_name,
                       s.admission_number, c.grade, c.id AS class_section_id,
                       c.name AS section_name, e.status
                FROM student_enrollments e
                JOIN students s ON s.id = e.student_id
                JOIN class_sections c ON c.id = e.class_section_id
                WHERE e.academic_year_id = ?
                ORDER BY c.grade, c.name, lower(s.name)
                """, (resultSet, row) -> new Enrollment(
                resultSet.getLong("id"),
                resultSet.getLong("academic_year_id"),
                resultSet.getLong("student_id"),
                resultSet.getString("student_name"),
                resultSet.getString("admission_number"),
                resultSet.getString("grade"),
                resultSet.getLong("class_section_id"),
                resultSet.getString("section_name"),
                resultSet.getString("status")
        ), academicYearId);
    }

    @PostMapping("/enrollments")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public Enrollment enrollStudent(
            @Valid @RequestBody EnrollmentRequest request,
            Authentication authentication
    ) {
        ClassSection section = findSection(request.classSectionId());
        requireStudent(request.studentId());
        jdbcTemplate.update("""
                INSERT INTO student_enrollments (
                    student_id, academic_year_id, class_section_id, status
                ) VALUES (?, ?, ?, 'ENROLLED')
                """, request.studentId(), section.academicYearId(), request.classSectionId());
        Long id = jdbcTemplate.queryForObject("""
                SELECT id FROM student_enrollments
                WHERE student_id = ? AND academic_year_id = ?
                """, Long.class, request.studentId(), section.academicYearId());
        if (id == null) {
            throw new IllegalStateException("The student enrollment was created but could not be loaded.");
        }
        updateCurrentStudentGrade(section.academicYearId(), request.studentId(), section.grade());
        auditService.record(authentication, "ENROLL", "STUDENT_ENROLLMENT", id.toString(),
                "Enrolled student " + request.studentId() + " in " + section.grade() + " / " + section.name());
        return findEnrollment(id);
    }

    @PutMapping("/enrollments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Enrollment updateEnrollment(
            @PathVariable long id,
            @Valid @RequestBody UpdateEnrollmentRequest request,
            Authentication authentication
    ) {
        Enrollment current = findEnrollment(id);
        ClassSection section = findSection(request.classSectionId());
        if (section.academicYearId() != current.academicYearId()) {
            throw new IllegalArgumentException("The selected section belongs to a different academic year.");
        }
        jdbcTemplate.update("""
                UPDATE student_enrollments
                SET class_section_id = ?, status = ?
                WHERE id = ?
                """, request.classSectionId(), request.status(), id);
        if (request.status().equals("ENROLLED")) {
            updateCurrentStudentGrade(current.academicYearId(), current.studentId(), section.grade());
        }
        auditService.record(authentication, "UPDATE", "STUDENT_ENROLLMENT", Long.toString(id),
                "Updated enrollment for " + current.studentName() + " to "
                        + section.grade() + " / " + section.name() + " (" + request.status() + ")");
        return findEnrollment(id);
    }

    private AcademicYear findAcademicYear(long id) {
        return jdbcTemplate.query("""
                SELECT id, name, starts_on, ends_on, is_current
                FROM academic_years WHERE id = ?
                """, resultSet -> {
            if (!resultSet.next()) {
                throw new IllegalArgumentException("Academic year was not found.");
            }
            return new AcademicYear(resultSet.getLong("id"), resultSet.getString("name"),
                    LocalDate.parse(resultSet.getString("starts_on")),
                    LocalDate.parse(resultSet.getString("ends_on")),
                    resultSet.getBoolean("is_current"));
        }, id);
    }

    private ClassSection findSection(long id) {
        return jdbcTemplate.query("""
                SELECT c.id, c.academic_year_id, y.name AS academic_year,
                       c.grade, c.name, c.room,
                       (SELECT COUNT(*) FROM student_enrollments e
                        WHERE e.class_section_id = c.id AND e.status = 'ENROLLED') AS enrolled_count,
                       (SELECT COUNT(*) FROM student_enrollments e
                        WHERE e.class_section_id = c.id) AS enrollment_count
                FROM class_sections c
                JOIN academic_years y ON y.id = c.academic_year_id
                WHERE c.id = ?
                """, resultSet -> {
            if (!resultSet.next()) {
                throw new IllegalArgumentException("Class section was not found.");
            }
            return new ClassSection(resultSet.getLong("id"), resultSet.getLong("academic_year_id"),
                    resultSet.getString("academic_year"), resultSet.getString("grade"),
                    resultSet.getString("name"), resultSet.getString("room"),
                    resultSet.getInt("enrolled_count"),
                    resultSet.getInt("enrollment_count"));
        }, id);
    }

    private Enrollment findEnrollment(long id) {
        return jdbcTemplate.query("""
                SELECT e.id, e.academic_year_id, e.student_id, s.name AS student_name,
                       s.admission_number, c.grade, c.id AS class_section_id,
                       c.name AS section_name, e.status
                FROM student_enrollments e
                JOIN students s ON s.id = e.student_id
                JOIN class_sections c ON c.id = e.class_section_id
                WHERE e.id = ?
                """, resultSet -> {
            if (!resultSet.next()) {
                throw new IllegalArgumentException("Student enrollment was not found.");
            }
            return new Enrollment(resultSet.getLong("id"), resultSet.getLong("academic_year_id"),
                    resultSet.getLong("student_id"), resultSet.getString("student_name"),
                    resultSet.getString("admission_number"), resultSet.getString("grade"),
                    resultSet.getLong("class_section_id"), resultSet.getString("section_name"),
                    resultSet.getString("status"));
        }, id);
    }

    private void requireAcademicYear(Long id) {
        if (id == null || jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM academic_years WHERE id = ?", Integer.class, id) == 0) {
            throw new IllegalArgumentException("Academic year was not found.");
        }
    }

    private void requireStudent(Long id) {
        if (id == null || jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM students WHERE id = ?", Integer.class, id) == 0) {
            throw new IllegalArgumentException("Student was not found.");
        }
    }

    private void updateCurrentStudentGrade(long academicYearId, long studentId, String grade) {
        jdbcTemplate.update("""
                UPDATE students SET grade = ?
                WHERE id = ? AND EXISTS (
                    SELECT 1 FROM academic_years WHERE id = ? AND is_current = 1
                )
                """, grade, studentId, academicYearId);
    }

    private static void validateDateRange(LocalDate startsOn, LocalDate endsOn) {
        if (endsOn.isBefore(startsOn)) {
            throw new IllegalArgumentException("The academic year end date must be after its start date.");
        }
    }

    private static String normalizeSectionName(String name) {
        return name.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record AcademicYear(
            long id,
            String name,
            LocalDate startsOn,
            LocalDate endsOn,
            boolean isCurrent
    ) {
    }

    public record AcademicYearRequest(
            @NotBlank @Size(min = 4, max = 20) String name,
            @NotNull LocalDate startsOn,
            @NotNull LocalDate endsOn
    ) {
    }

    public record ClassSection(
            long id,
            long academicYearId,
            String academicYear,
            String grade,
            String name,
            String room,
            int enrolledCount,
            int enrollmentCount
    ) {
    }

    public record ClassSectionRequest(
            @NotNull Long academicYearId,
            @NotBlank @Pattern(regexp = "Grade (?:[1-9]|1[0-2])") String grade,
            @NotBlank @Size(max = 20) String name,
            @Size(max = 40) String room
    ) {
    }

    public record UpdateClassSectionRequest(
            @NotBlank @Size(max = 20) String name,
            @Size(max = 40) String room
    ) {
    }

    public record Enrollment(
            long id,
            long academicYearId,
            long studentId,
            String studentName,
            String admissionNumber,
            String grade,
            long classSectionId,
            String sectionName,
            String status
    ) {
    }

    public record EnrollmentRequest(@NotNull Long studentId, @NotNull Long classSectionId) {
    }

    public record UpdateEnrollmentRequest(
            @NotNull Long classSectionId,
            @NotBlank @Pattern(regexp = "ENROLLED|COMPLETED|WITHDRAWN") String status
    ) {
    }
}
