package com.smartsched.smartsched_api.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Global Exception Handler to catch exceptions from all controllers
 * and return standardized JSON error responses.
 */
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ResponseEntity<Object> buildErrorResponse(Exception ex, HttpStatus status) {
        logger.error("Exception caught by GlobalExceptionHandler: {}", ex.getMessage());
        return ResponseEntity.status(status).body(Map.of("message", ex.getMessage()));
    }

    // --- 404 Not Found ---
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Object> handleNoSuchElementException(NoSuchElementException ex, WebRequest request) {
        logger.warn("Resource not found: {}", ex.getMessage());
        return buildErrorResponse(ex, HttpStatus.NOT_FOUND);
    }

    // --- 400 Bad Request ---
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        logger.warn("Bad request: {}", ex.getMessage());
        return buildErrorResponse(ex, HttpStatus.BAD_REQUEST);
    }

    // --- 401 Unauthorized (Authentication Failures) ---
    @ExceptionHandler({ AuthenticationException.class, BadCredentialsException.class })
    public ResponseEntity<Object> handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        logger.warn("Authentication failed: {}", ex.getMessage());
        return buildErrorResponse(ex, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<Object> handleDisabledException(DisabledException ex, WebRequest request) {
        logger.warn("Login attempt for disabled account: {}", ex.getMessage());
        // Return a specific message for disabled accounts
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "User account is pending admin approval."));
    }

    // --- 403 Forbidden (Authorization Failures) ---
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
        logger.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "You do not have permission to perform this action."));
    }

    // --- 500 Internal Server Error (Generic Fallback) ---
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAllUncaughtException(Exception ex, WebRequest request) {
        logger.error("An unexpected internal server error occurred:", ex);
        return buildErrorResponse(new RuntimeException("An unexpected internal error occurred. Please contact support."),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
