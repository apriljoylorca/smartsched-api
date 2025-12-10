package com.smartsched.smartsched_api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Validates JWT secret configuration on application startup
 * Now accepts any string - no Base64 encoding required!
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
            logger.error("");
            logger.error("╔══════════════════════════════════════════════════════════════════════════════╗");
            logger.error("║                    ⚠️  JWT SECRET NOT CONFIGURED  ⚠️                         ║");
            logger.error("╚══════════════════════════════════════════════════════════════════════════════╝");
            logger.error("");
            logger.error("The JWT_SECRET environment variable is not set or is using the default value.");
            logger.error("Authentication will FAIL until this is configured!");
            logger.error("");
            logger.error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            logger.error("QUICK FIX FOR RENDER:");
            logger.error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            logger.error("");
            logger.error("1. In Render Dashboard:");
            logger.error("   → Go to your service → Environment tab");
            logger.error("   → Click 'Add Environment Variable'");
            logger.error("   → Key: JWT_SECRET");
            logger.error("   → Value: [any secure string, e.g., 'my-super-secret-key-12345']");
            logger.error("   → Click 'Save'");
            logger.error("");
            logger.error("2. Redeploy your service");
            logger.error("");
            logger.error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            logger.error("NOTE: You can use ANY string - no Base64 encoding needed!");
            logger.error("      For better security, use at least 16 random characters.");
            logger.error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            logger.error("");
            logger.error("=== JWT SECRET VALIDATION: FAILED ===");
            logger.error("");
            return;
        }
        
        // Mask the secret for logging
        String masked = jwtSecret.length() > 10 
            ? jwtSecret.substring(0, 5) + "..." + jwtSecret.substring(jwtSecret.length() - 5)
            : "****";
        logger.info("JWT Secret length: {} characters, masked: {}", jwtSecret.length(), masked);
        
        // Simple validation - just check length
        if (jwtSecret.length() < 8) {
            logger.warn("⚠ JWT Secret is short ({} characters). Consider using at least 16 characters for better security.", jwtSecret.length());
        } else if (jwtSecret.length() >= 16) {
            logger.info("✓ JWT Secret length is good ({} characters)", jwtSecret.length());
        }
        
        logger.info("✓ JWT Secret is configured and will be automatically converted to a secure key");
        logger.info("=== JWT SECRET VALIDATION: OK ===");
    }
}

