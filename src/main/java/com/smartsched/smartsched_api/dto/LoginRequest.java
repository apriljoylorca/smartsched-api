package com.smartsched.smartsched_api.dto;

/**
 * DTO (Data Transfer Object) for login requests.
 */
public record LoginRequest(String username, String password) {
}
