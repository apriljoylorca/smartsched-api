package com.smartsched.smartsched_api.service;

import com.smartsched.smartsched_api.dto.AuthResponse; // Ensure this DTO is imported
import com.smartsched.smartsched_api.dto.LoginRequest;
import com.smartsched.smartsched_api.dto.RegisterRequest;
import com.smartsched.smartsched_api.model.Role;
import com.smartsched.smartsched_api.model.User;
import com.smartsched.smartsched_api.repository.UserRepository;
import com.smartsched.smartsched_api.security.JwtService;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger; // Import Logger
import org.slf4j.LoggerFactory; // Import LoggerFactory
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException; // More specific exception
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException; // Catch general auth exceptions
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class); // Add Logger

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional
    public User registerScheduler(RegisterRequest request) {
        logger.info("Attempting to register user: {}", request.username()); // Add logging
        if (userRepository.findByUsername(request.username()).isPresent()) {
            logger.warn("Username {} already taken.", request.username()); // Add logging
            throw new IllegalArgumentException("Username '" + request.username() + "' is already taken.");
        }
        if (request.password() == null || request.password().length() < 8) { // Add password validation
             logger.warn("Registration password too short for user: {}", request.username());
             throw new IllegalArgumentException("Password must be at least 8 characters long.");
        }


        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.ROLE_SCHEDULER);
        user.setEnabled(false);

        User savedUser = userRepository.save(user);
        logger.info("User {} registered successfully with ID: {}. Pending approval.", savedUser.getUsername(), savedUser.getId()); // Add logging
        return savedUser;
    }

    public AuthResponse login(LoginRequest request) {
        logger.info("Attempting login for user: {}", request.username()); // Add logging
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            if (!userDetails.isEnabled()) {
                logger.warn("Login attempt for disabled user: {}", request.username()); // Add logging
                throw new DisabledException("User account " + userDetails.getUsername() + " is pending approval.");
            }

            String token = jwtService.generateToken(userDetails);

            String role = userDetails.getAuthorities().stream()
                    .findFirst()
                    .map(auth -> auth.getAuthority())
                    .orElse("ROLE_USER"); // Default fallback, though should always have a role

            logger.info("Login successful for user: {}", request.username()); // Add logging
            // This is the line you mentioned - ensure AuthResponse constructor matches
            return new AuthResponse(token, role);

        } catch (DisabledException e) {
             // Re-throw specific exception for AuthController to handle
            throw e;
        } catch (BadCredentialsException e) {
             logger.warn("Invalid credentials for user: {}", request.username()); // Add logging
             // Throw IllegalArgumentException for AuthController to return 401
             throw new IllegalArgumentException("Invalid username or password.");
        } catch (AuthenticationException e) {
            // Catch other authentication issues
            logger.error("Authentication failed for user {}: {}", request.username(), e.getMessage()); // Add logging
            throw new IllegalArgumentException("Authentication failed."); // General message for security
        } catch (Exception e) {
             // Catch unexpected errors during login
             logger.error("Unexpected error during login for user {}: {}", request.username(), e.getMessage(), e);
             throw new RuntimeException("An internal error occurred during login."); // For AuthController to return 500
        }
    }


    public List<User> getPendingUsers() {
        return userRepository.findByRoleAndEnabled(Role.ROLE_SCHEDULER, false);
    }

    @Transactional
    public User approveUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + userId));

        if(user.isEnabled()){
             logger.warn("Attempted to approve already enabled user: {}", user.getUsername()); // Add logging
             return user;
        }
        if(user.getRole() != Role.ROLE_SCHEDULER) {
            logger.error("Attempted to approve user {} with incorrect role: {}", user.getUsername(), user.getRole()); // Add logging
            throw new IllegalArgumentException("Cannot approve user with role: " + user.getRole());
        }

        user.setEnabled(true);
        User approvedUser = userRepository.save(user);
        logger.info("User {} (ID: {}) approved successfully.", approvedUser.getUsername(), approvedUser.getId()); // Add logging
        return approvedUser;
    }

     @Transactional
     public void deleteUser(String userId) {
         User user = userRepository.findById(userId)
             .orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + userId));

         // Add check to prevent deleting the last admin? (Optional)
         // if (user.getRole() == Role.ROLE_ADMIN) {
         //    List<User> admins = userRepository.findByRole(Role.ROLE_ADMIN);
         //    if (admins.size() <= 1) {
         //       logger.error("Attempted to delete the last admin user: {}", user.getUsername());
         //       throw new IllegalArgumentException("Cannot delete the last admin user.");
         //    }
         // }

         userRepository.deleteById(userId);
         logger.warn("User {} (ID: {}) deleted successfully.", user.getUsername(), userId); // Add logging
     }

     public List<User> getAllUsers() {
        return userRepository.findAll();
    }
}

