package com.smartsched.smartsched_api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.io.Decoders;

/**
 * Validates JWT secret configuration on application startup
 */
@Component
public class JwtSecretValidator implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(JwtSecretValidator.class);
    
    @Value("${jwt.secret:not-set}")
    private String jwtSecret;

    @Override
    public void run(String... args) {
        logger.info("=== JWT SECRET VALIDATION ===");
        
        if (jwtSecret == null || jwtSecret.isEmpty() || "not-set".equals(jwtSecret) || "change-me".equals(jwtSecret)) {
            logger.error("✗ JWT Secret is not configured!");
            logger.error("✗ Please set JWT_SECRET environment variable in Render.");
            logger.error("✗ Generate a secret using: openssl rand -base64 32");
            logger.error("✗ See GENERATE_JWT_SECRET.md for instructions.");
            logger.error("=== JWT SECRET VALIDATION: FAILED ===");
            return;
        }
        
        // Mask the secret for logging
        String masked = jwtSecret.length() > 10 
            ? jwtSecret.substring(0, 5) + "..." + jwtSecret.substring(jwtSecret.length() - 5)
            : "****";
        logger.info("JWT Secret length: {}, masked: {}", jwtSecret.length(), masked);
        
        // Check for invalid characters
        if (jwtSecret.contains("-") || jwtSecret.contains("_") || jwtSecret.contains(" ")) {
            logger.error("✗ JWT Secret contains invalid Base64 characters!");
            logger.error("✗ Base64 only allows: A-Z, a-z, 0-9, +, /, and =");
            logger.error("✗ Current secret appears to not be Base64 encoded.");
            logger.error("✗ Generate a new secret using: openssl rand -base64 32");
            logger.error("=== JWT SECRET VALIDATION: FAILED ===");
            return;
        }
        
        // Try to decode
        try {
            byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
            if (keyBytes.length < 32) {
                logger.error("✗ JWT Secret is too short ({} bytes). Must be at least 32 bytes.", keyBytes.length);
                logger.error("✗ Generate a new secret using: openssl rand -base64 32");
                logger.error("=== JWT SECRET VALIDATION: FAILED ===");
                return;
            }
            logger.info("✓ JWT Secret is valid Base64");
            logger.info("✓ Decoded key length: {} bytes (minimum 32 required)", keyBytes.length);
            logger.info("=== JWT SECRET VALIDATION: OK ===");
        } catch (IllegalArgumentException e) {
            logger.error("✗ JWT Secret is not valid Base64!");
            logger.error("✗ Error: {}", e.getMessage());
            logger.error("✗ Generate a new secret using: openssl rand -base64 32");
            logger.error("✗ See GENERATE_JWT_SECRET.md for detailed instructions.");
            logger.error("=== JWT SECRET VALIDATION: FAILED ===");
        } catch (Exception e) {
            logger.error("✗ Unexpected error validating JWT Secret: {}", e.getMessage());
            logger.error("=== JWT SECRET VALIDATION: FAILED ===");
        }
    }
}

