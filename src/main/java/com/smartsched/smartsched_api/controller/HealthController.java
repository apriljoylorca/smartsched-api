package com.smartsched.smartsched_api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Health check endpoint to diagnose connection issues
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);
    
    private final MongoTemplate mongoTemplate;
    
    @Value("${spring.data.mongodb.uri:not-set}")
    private String mongoUri;
    
    @Value("${spring.data.mongodb.database:not-set}")
    private String mongoDatabase;
    
    @Value("${cors.allowed-origins:not-set}")
    private String corsOrigins;

    public HealthController(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "smartsched-api");
        
        // Test MongoDB connection
        Map<String, Object> mongoStatus = new HashMap<>();
        try {
            mongoTemplate.getDb().getName(); // Test connection
            mongoStatus.put("connected", true);
            mongoStatus.put("database", mongoTemplate.getDb().getName());
            logger.info("MongoDB health check: Connected to database {}", mongoTemplate.getDb().getName());
        } catch (Exception e) {
            mongoStatus.put("connected", false);
            mongoStatus.put("error", e.getMessage());
            logger.error("MongoDB health check failed: {}", e.getMessage(), e);
        }
        mongoStatus.put("uri", mongoUri.replaceAll(":[^:@]+@", ":****@") + " (masked)");
        health.put("mongodb", mongoStatus);
        
        // Configuration info
        Map<String, Object> config = new HashMap<>();
        config.put("corsOrigins", corsOrigins);
        health.put("config", config);
        
        return ResponseEntity.ok(health);
    }
}

