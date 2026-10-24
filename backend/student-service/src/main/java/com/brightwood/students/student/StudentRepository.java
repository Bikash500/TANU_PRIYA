package com.brightwood.students.student;

import java.util.List;
import java.util.Optional;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StudentRepository {

    private static final String SELECT_COLUMNS =
            "id, admission_number, name, grade, email, date_of_birth, guardian_name, "
                    + "guardian_email, guardian_phone, address, enrollment_date, status";
    private final JdbcTemplate jdbcTemplate;

    public StudentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Student> findAll() {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM students ORDER BY id",
                this::mapStudent
        );
    }

    public Optional<Student> findById(long id) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM students WHERE id = ?",
                this::mapStudent,
                id
        ).stream().findFirst();
    }

    public Student create(StudentRequest request) {
        Long generatedId = jdbcTemplate.queryForObject(
                """
                INSERT INTO students (
                    admission_number, name, grade, email, date_of_birth,
                    guardian_name, guardian_email, guardian_phone, address, enrollment_date, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """,
                Long.class,
                request.admissionNumber().trim().toUpperCase(),
                request.name().trim(),
                request.grade().trim(),
                request.email().trim(),
                request.dateOfBirth() == null ? null : request.dateOfBirth().toString(),
                trimToNull(request.guardianName()),
                trimToNull(request.guardianEmail()),
                trimToNull(request.guardianPhone()),
                trimToNull(request.address()),
                request.enrollmentDate() == null
                        ? java.time.LocalDate.now().toString()
                        : request.enrollmentDate().toString(),
                request.status() == null || request.status().isBlank()
                        ? "ACTIVE"
                        : request.status().trim()
        );
        if (generatedId == null) {
            throw new IllegalStateException("The database did not return the new student ID.");
        }
        return findById(generatedId)
                .orElseThrow(() -> new IllegalStateException("The created student could not be read."));
    }

    public boolean update(long id, StudentRequest request) {
        int updated = jdbcTemplate.update(
                """
                UPDATE students
                SET admission_number = ?, name = ?, grade = ?, email = ?, date_of_birth = ?,
                    guardian_name = ?, guardian_email = ?, guardian_phone = ?, address = ?,
                    enrollment_date = ?, status = ?
                WHERE id = ?
                """,
                request.admissionNumber().trim().toUpperCase(),
                request.name().trim(),
                request.grade().trim(),
                request.email().trim(),
                request.dateOfBirth() == null ? null : request.dateOfBirth().toString(),
                trimToNull(request.guardianName()),
                trimToNull(request.guardianEmail()),
                trimToNull(request.guardianPhone()),
                trimToNull(request.address()),
                request.enrollmentDate() == null
                        ? java.time.LocalDate.now().toString()
                        : request.enrollmentDate().toString(),
                request.status() == null || request.status().isBlank()
                        ? "ACTIVE"
                        : request.status().trim(),
                id
        );
        return updated > 0;
    }

    public boolean delete(long id) {
        return jdbcTemplate.update("DELETE FROM students WHERE id = ?", id) > 0;
    }

    private Student mapStudent(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Student(
                resultSet.getLong("id"),
                resultSet.getString("admission_number"),
                resultSet.getString("name"),
                resultSet.getString("grade"),
                resultSet.getString("email"),
                localDate(resultSet.getString("date_of_birth")),
                resultSet.getString("guardian_name"),
                resultSet.getString("guardian_email"),
                resultSet.getString("guardian_phone"),
                resultSet.getString("address"),
                localDate(resultSet.getString("enrollment_date")),
                resultSet.getString("status")
        );
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static java.time.LocalDate localDate(String value) {
        return value == null ? null : java.time.LocalDate.parse(value);
    }
}
