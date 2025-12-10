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
        logger.info("Register request for user: {}", request.username());
        try {
            // Delegate registration logic to UserService
            userService.registerScheduler(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "User registered successfully. Awaiting admin approval."));
        } catch (IllegalArgumentException e) {
            logger.warn("Registration failed for {}: {}", request.username(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Internal error during registration for {}:", request.username(), e);
            return ResponseEntity.internalServerError().body(Map.of("message", "An internal error occurred during registration."));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        logger.info("Login request for user: {}", request.username());
        try {
            // Delegate login logic (including AuthenticationManager call and token generation) to UserService
            AuthResponse response = userService.login(request);
            return ResponseEntity.ok(response);
        } catch (DisabledException e) {
            logger.warn("Login failed for {}: {}", request.username(), e.getMessage());
            // Return 401 Unauthorized for disabled accounts trying to log in
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            // This usually means bad credentials if AuthenticationException was caught in UserService
            logger.warn("Login failed for {}: {}", request.username(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Internal error during login for {}:", request.username(), e);
            return ResponseEntity.internalServerError().body(Map.of("message", "An internal error occurred during login."));
        }
    }
}

