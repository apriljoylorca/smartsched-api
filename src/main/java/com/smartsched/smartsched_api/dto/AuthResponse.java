package com.smartsched.smartsched_api.dto;

/**
 * DTO (Data Transfer Object) to send back on successful login.
 */
public record AuthResponse(String token, String role) {
}
