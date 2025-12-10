package com.smartsched.smartsched_api.controller;

import com.smartsched.smartsched_api.model.User;
import com.smartsched.smartsched_api.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/pending")
    public ResponseEntity<List<User>> getPendingUsers() {
        return ResponseEntity.ok(userService.getPendingUsers());
    }

    @PostMapping("/approve/{userId}")
    public ResponseEntity<?> approveUser(@PathVariable String userId) {
        logger.info("Request to approve user: {}", userId);
        try {
            User approvedUser = userService.approveUser(userId);
            return ResponseEntity.ok(Map.of(
                    "message", "User approved successfully",
                    "username", approvedUser.getUsername()));
        } catch (NoSuchElementException e) {
            logger.warn("Failed to approve user: {}", e.getMessage());
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable String userId) {
        logger.info("Request to delete user: {}", userId);
        try {
            userService.deleteUser(userId);
            return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
        } catch (NoSuchElementException e) {
            logger.warn("Failed to delete user: {}", e.getMessage());
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }
    }
}
