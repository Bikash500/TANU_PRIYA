package com.brightwood.students.student;

public class StudentNotFoundException extends RuntimeException {

    public StudentNotFoundException(long id) {
        super("Student with ID " + id + " was not found.");
    }
}
