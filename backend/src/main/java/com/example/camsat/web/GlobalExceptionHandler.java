package com.example.camsat.web;

import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Converts every failure into the frontend's ApiErrorBody. Never leaks stack traces, SQL or internals. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static ResponseEntity<ApiErrorBody> body(HttpStatus status, String message, Map<String, String> fields) {
        return ResponseEntity.status(status).body(ApiErrorBody.of(status.value(), status.getReasonPhrase(), message, fields));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrorBody> api(ApiException e) { return body(e.status(), e.getMessage(), null); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorBody> validation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return body(HttpStatus.BAD_REQUEST, "Validation failed. Check the highlighted fields.", fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorBody> constraint(ConstraintViolationException e) {
        return body(HttpStatus.BAD_REQUEST, "Validation failed.", null);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    ResponseEntity<ApiErrorBody> unreadable(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "The request body or parameters are missing or malformed.", null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorBody> typeMismatch(MethodArgumentTypeMismatchException e) {
        return body(HttpStatus.BAD_REQUEST, "Invalid value for '" + e.getName() + "'.", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorBody> integrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return body(HttpStatus.CONFLICT, "The request conflicts with existing data.", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiErrorBody> method(HttpRequestMethodNotSupportedException e) {
        return body(HttpStatus.METHOD_NOT_ALLOWED, "This HTTP method is not supported here.", null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiErrorBody> media(HttpMediaTypeNotSupportedException e) {
        return body(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content type not supported. Use application/json.", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiErrorBody> noResource(NoResourceFoundException e) {
        return body(HttpStatus.NOT_FOUND, "The requested resource was not found.", null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorBody> unexpected(Exception e) {
        log.error("Unhandled exception", e); // full stack trace goes to the server log only, never to the client
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", null);
    }
}