package com.brightwood.students.student;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class StudentService {

    private final StudentRepository repository;

    public StudentService(StudentRepository repository) {
        this.repository = repository;
    }

    public List<Student> findAll() {
        return repository.findAll();
    }

    public Student create(StudentRequest request) {
        return repository.create(request);
    }

    public Student update(long id, StudentRequest request) {
        if (!repository.update(id, request)) {
            throw new StudentNotFoundException(id);
        }
        return repository.findById(id).orElseThrow(() -> new StudentNotFoundException(id));
    }

    public void delete(long id) {
        if (!repository.delete(id)) {
            throw new StudentNotFoundException(id);
        }
    }
}
