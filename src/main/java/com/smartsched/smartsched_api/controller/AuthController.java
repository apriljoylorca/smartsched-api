package com.smartsched.smartsched_api.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity; // Use Lombok constructor
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartsched.smartsched_api.dto.AuthResponse;
import com.smartsched.smartsched_api.dto.LoginRequest;
import com.smartsched.smartsched_api.dto.RegisterRequest;
import com.smartsched.smartsched_api.model.User;
import com.smartsched.smartsched_api.service.UserService;

import lombok.RequiredArgsConstructor;

/**
 * Handles login and registration endpoints.
 * CORRECT Dependencies: UserService (which handles auth logic).
 * DOES NOT depend on SecurityConfig, AuthenticationManager, or JwtService directly.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor // Generates constructor for final fields
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    // Only inject UserService
    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        logger.info("=== REGISTER REQUEST ===");
        logger.info("Username: {}", request.username());
        logger.info("Password length: {}", request.password() != null ? request.password().length() : 0);
        logger.info("Request received from origin: {}", getRequestOrigin());
        try {
            // Delegate registration logic to UserService
            User savedUser = userService.registerScheduler(request);
            logger.info("=== REGISTRATION SUCCESS ===");
            logger.info("User ID: {}, Username: {}", savedUser.getId(), savedUser.getUsername());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "User registered successfully. Awaiting admin approval."));
        } catch (IllegalArgumentException e) {
            logger.warn("=== REGISTRATION FAILED (Validation) ===");
            logger.warn("Username: {}, Error: {}", request.username(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            logger.error("=== REGISTRATION FAILED (Internal Error) ===");
            logger.error("Username: {}, Error: {}", request.username(), e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("message", "An internal error occurred during registration: " + e.getMessage()));
        }
    }
    
    private String getRequestOrigin() {
        try {
            org.springframework.web.context.request.RequestAttributes requestAttributes = 
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (requestAttributes instanceof org.springframework.web.context.request.ServletRequestAttributes) {
                jakarta.servlet.http.HttpServletRequest request = 
                    ((org.springframework.web.context.request.ServletRequestAttributes) requestAttributes).getRequest();
                return request.getHeader("Origin") != null ? request.getHeader("Origin") : "Unknown";
            }
        } catch (Exception e) {
            logger.debug("Could not get request origin: {}", e.getMessage());
        }
        return "Unknown";
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        logger.info("=== LOGIN REQUEST ===");
        logger.info("Username: {}", request.username());
        logger.info("Password length: {}", request.password() != null ? request.password().length() : 0);
        logger.info("Request received from origin: {}", getRequestOrigin());
        try {
            // Delegate login logic (including AuthenticationManager call and token generation) to UserService
            AuthResponse response = userService.login(request);
            logger.info("=== LOGIN SUCCESS ===");
            logger.info("Username: {}, Role: {}", request.username(), response.role());
            return ResponseEntity.ok(response);
        } catch (DisabledException e) {
            logger.warn("=== LOGIN FAILED (Account Disabled) ===");
            logger.warn("Username: {}, Error: {}", request.username(), e.getMessage());
            // Return 401 Unauthorized for disabled accounts trying to log in
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            logger.warn("=== LOGIN FAILED (Invalid Credentials) ===");
            logger.warn("Username: {}, Error: {}", request.username(), e.getMessage());
            // This usually means bad credentials if AuthenticationException was caught in UserService
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            logger.error("=== LOGIN FAILED (Internal Error) ===");
            logger.error("Username: {}, Error: {}", request.username(), e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("message", "An internal error occurred during login: " + e.getMessage()));
        }
    }
}

