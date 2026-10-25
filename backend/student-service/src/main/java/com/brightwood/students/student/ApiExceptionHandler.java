package com.brightwood.students.student;

import java.util.stream.Collectors;
import java.util.Objects;

import com.brightwood.students.assistant.AiAssistantException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AiAssistantException.class)
    public ResponseEntity<ApiError> handleAiAssistant(AiAssistantException exception) {
        return ResponseEntity.status(exception.status())
                .body(new ApiError(exception.getMessage()));
    }

    @ExceptionHandler(StudentNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(StudentNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> Objects.requireNonNullElse(
                        fieldError.getDefaultMessage(), "Invalid field value."))
                .distinct()
                .collect(Collectors.joining(" "));
        return ResponseEntity.badRequest().body(new ApiError(message));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiError> handleBadRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("The request contains invalid JSON or parameters."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleConflict(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("A record with that unique value already exists."));
    }

    @ExceptionHandler(UncategorizedSQLException.class)
    public ResponseEntity<ApiError> handleSqliteConstraint(UncategorizedSQLException exception) {
        if (exception.getSQLException().getErrorCode() == 19) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("The request conflicts with an existing or related record."));
        }
        throw exception;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ApiError(exception.getMessage()));
    }

    public record ApiError(String error) {
    }
}
