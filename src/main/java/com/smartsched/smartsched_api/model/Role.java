package com.smartsched.smartsched_api.model;

/**
 * Defines the user roles within the application.
 * Spring Security automatically prefixes roles with "ROLE_".
 */
public enum Role {
    ROLE_SCHEDULER,
    ROLE_ADMIN
}
