package com.example.roombooking.advice;

import com.example.roombooking.exception.ConflictException;
import com.example.roombooking.exception.ForbiddenException;
import com.example.roombooking.exception.ResourceNotFoundException;
import com.example.roombooking.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Extends Spring's handler so framework errors (malformed JSON, unknown path, wrong method,
 * missing parameter, ...) get their proper 4xx status instead of falling into the catch-all 500.
 */
@RestControllerAdvice
@Slf4j
public class ControllerAdvice extends ResponseEntityExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream().findFirst()
                .map(e -> e.getField() + " " + e.getDefaultMessage()).orElse("Invalid request");
        return ResponseEntity.status(status).body(body(status, message));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String message = status.is4xxClientError() ? clientMessage(ex, resolved) : "An unexpected error occurred";
        return ResponseEntity.status(status).headers(headers).body(body(status, message));
    }

    private static String clientMessage(Exception ex, HttpStatus status) {
        if (ex instanceof HttpMessageNotReadableException) return "Malformed or unreadable request body";
        if (ex instanceof NoResourceFoundException) return "Not found";
        return status != null ? status.getReasonPhrase() : "Bad request";
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(body(status, message));
    }

    private static ErrorResponse body(HttpStatusCode status, String message) {
        return ErrorResponse.builder().message(message).status(status.value()).build();
    }
}
