package com.example.featureflags.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(FlagNotFoundException.class)
    ResponseEntity<ProblemDetail> handleFlagNotFound(FlagNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Feature Flag Not Found");
        pd.setType(URI.create("urn:problem-type:feature-flag-not-found"));
        pd.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(pd);
    }

    @ExceptionHandler(DuplicateFlagNameException.class)
    ResponseEntity<ProblemDetail> handleDuplicateName(DuplicateFlagNameException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Duplicate Flag Name");
        pd.setType(URI.create("urn:problem-type:duplicate-flag-name"));
        pd.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Request validation failed; see 'errors' for field-level details"
        );
        pd.setTitle("Validation Error");
        pd.setType(URI.create("urn:problem-type:validation-error"));
        pd.setProperty("timestamp", Instant.now());

        List<Map<String, String>> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> Map.of(
                        "field",    fe.getField(),
                        "message",  fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid value",
                        "rejected", fe.getRejectedValue() != null ? String.valueOf(fe.getRejectedValue()) : "null"
                ))
                .toList();

        pd.setProperty("errors", fieldErrors);

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(pd);
    }
}
