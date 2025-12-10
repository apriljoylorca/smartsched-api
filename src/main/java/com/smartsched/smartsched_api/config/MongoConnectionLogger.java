package com.smartsched.smartsched_api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Logs MongoDB connection status on application startup
 */
@Component
public class MongoConnectionLogger implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(MongoConnectionLogger.class);
    
    private final MongoTemplate mongoTemplate;
    
    @Value("${spring.data.mongodb.uri:not-set}")
    private String mongoUri;
    
    @Value("${spring.data.mongodb.database:not-set}")
    private String mongoDatabase;

    public MongoConnectionLogger(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(String... args) {
        logger.info("=== MONGODB CONNECTION CHECK ===");
        logger.info("MongoDB URI: {}", mongoUri.replaceAll(":[^:@]+@", ":****@") + " (masked)");
        logger.info("MongoDB Database: {}", mongoDatabase);
        
        try {
            String dbName = mongoTemplate.getDb().getName();
            logger.info("✓ MongoDB connection successful!");
            logger.info("✓ Connected to database: {}", dbName);
            logger.info("=== MONGODB CONNECTION: OK ===");
        } catch (Exception e) {
            logger.error("✗ MongoDB connection failed!");
            logger.error("✗ Error: {}", e.getMessage(), e);
            logger.error("=== MONGODB CONNECTION: FAILED ===");
        }
    }
}

