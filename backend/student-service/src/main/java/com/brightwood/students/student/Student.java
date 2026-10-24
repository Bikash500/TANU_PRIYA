package com.brightwood.students.student;

import java.time.LocalDate;

public record Student(
        long id,
        String admissionNumber,
        String name,
        String grade,
        String email,
        LocalDate dateOfBirth,
        String guardianName,
        String guardianEmail,
        String guardianPhone,
        String address,
        LocalDate enrollmentDate,
        String status
) {
}
