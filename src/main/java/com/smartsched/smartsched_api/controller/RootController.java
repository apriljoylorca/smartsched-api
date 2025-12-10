package com.smartsched.smartsched_api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Root endpoint to provide API information
 */
@RestController
public class RootController {

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        return ResponseEntity.ok(Map.of(
            "service", "smartsched-api",
            "status", "running",
            "version", "1.0.0",
            "endpoints", Map.of(
                "health", "/api/health",
                "auth", "/api/auth/login, /api/auth/register"
            ),
            "message", "SmartSched API is running. Use /api/health for health check."
        ));
    }
}

