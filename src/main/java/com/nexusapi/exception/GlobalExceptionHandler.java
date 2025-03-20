package com.nexusapi.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.*;

/**
 * Centralised exception handling for all controllers.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} to intercept Spring MVC's
 * built-in exceptions (e.g. validation failures) and converts them into a
 * consistent, API-friendly JSON structure.
 *
 * <p>Response body format:
 * <pre>
 * {
 *   "timestamp": "2024-01-15T12:00:00Z",
 *   "status": 404,
 *   "error": "Not Found",
 *   "message": "Task with id 'abc' not found",
 *   "path": "/api/v1/tasks/abc"
 * }
 * </pre>
 *
 * <p>Validation error response includes a per-field breakdown:
 * <pre>
 * {
 *   "errors": {
 *     "title": "must not be blank",
 *     "priority": "must not be null"
 *   }
 * }
 * </pre>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // ---------------------------------------------------------------------------
    // Domain exceptions
    // ---------------------------------------------------------------------------

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
        ResourceNotFoundException ex, WebRequest request
    ) {
        log.debug("Resource not found: {}", ex.getMessage());
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(
        ForbiddenException ex, WebRequest request
    ) {
        log.warn("Access denied: {}", ex.getMessage());
        return buildError(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(
        ConflictException ex, WebRequest request
    ) {
        return buildError(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(
        IllegalStateException ex, WebRequest request
    ) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    // ---------------------------------------------------------------------------
    // Security exceptions
    // ---------------------------------------------------------------------------

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(
        AccessDeniedException ex, WebRequest request
    ) {
        return buildError(HttpStatus.FORBIDDEN, "Access denied", request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(
        BadCredentialsException ex, WebRequest request
    ) {
        // Generic message — never reveal whether the email or password was wrong
        return buildError(HttpStatus.UNAUTHORIZED, "Invalid credentials", request);
    }

    // ---------------------------------------------------------------------------
    // Validation exceptions
    // ---------------------------------------------------------------------------

    /**
     * Handles {@code @Valid} failures on request bodies.
     * Returns a 400 with a field-by-field error map.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = error instanceof FieldError fe ? fe.getField() : error.getObjectName();
            fieldErrors.put(field, error.getDefaultMessage());
        });

        ApiError body = ApiError.builder()
            .timestamp(Instant.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Validation failed")
            .message("Request body contains invalid fields")
            .path(extractPath(request))
            .fieldErrors(fieldErrors)
            .build();

        log.debug("Validation failed: {}", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    // ---------------------------------------------------------------------------
    // Catch-all
    // ---------------------------------------------------------------------------

    /**
     * Safety net for any unhandled exceptions.
     * Logs the full stack trace but returns a generic message to the client
     * to avoid leaking internal implementation details.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneral(Exception ex, WebRequest request) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return buildError(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred. Please try again later.",
            request
        );
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private ResponseEntity<ApiError> buildError(
        HttpStatus status, String message, WebRequest request
    ) {
        ApiError body = ApiError.builder()
            .timestamp(Instant.now())
            .status(status.value())
            .error(status.getReasonPhrase())
            .message(message)
            .path(extractPath(request))
            .build();
        return ResponseEntity.status(status).body(body);
    }

    private String extractPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}
