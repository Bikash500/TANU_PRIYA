package com.brightwood.students.student;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record StudentRequest(
        @NotBlank(message = "Admission number is required.")
        @Pattern(regexp = "[A-Za-z0-9-]{2,20}", message = "Admission number must be 2–20 letters, numbers, or hyphens.")
        String admissionNumber,

        @NotBlank(message = "Name is required.")
        @Size(max = 100, message = "Name must be 100 characters or fewer.")
        String name,

        @NotBlank(message = "Grade is required.")
        @Pattern(regexp = "Grade (?:[1-9]|1[0-2])", message = "Choose a valid grade from Grade 1 to Grade 12.")
        String grade,

        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 254, message = "Email must be 254 characters or fewer.")
        String email,

        LocalDate dateOfBirth,

        @Size(max = 100, message = "Guardian name must be 100 characters or fewer.")
        String guardianName,

        @Email(message = "Enter a valid guardian email address.")
        @Size(max = 254, message = "Guardian email must be 254 characters or fewer.")
        String guardianEmail,

        @Size(max = 30, message = "Guardian phone must be 30 characters or fewer.")
        String guardianPhone,

        @Size(max = 500, message = "Address must be 500 characters or fewer.")
        String address,

        LocalDate enrollmentDate,

        @Pattern(regexp = "ACTIVE|TRANSFERRED|GRADUATED", message = "Choose Active, Transferred, or Graduated.")
        String status
) {
}
