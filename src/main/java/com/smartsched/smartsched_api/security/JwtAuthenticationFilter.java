package com.smartsched.smartsched_api.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component; // Direct dependency
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * This is the JWT Filter.
 * It MUST NOT depend on SecurityConfig.
 * It ONLY depends on JwtService and UserDetailsService.
 */
@Component
@RequiredArgsConstructor // <-- This annotation creates the constructor for the 'final' fields
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger filterLogger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    // 1. DEPENDENCY: Inject JwtService. This is correct.
    private final JwtService jwtService;
    // 2. DEPENDENCY: Inject UserDetailsService. This is correct.
    private final UserDetailsService userDetailsService;
    
    // NOTE: There is NO constructor asking for SecurityConfig. This breaks the cycle.

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);

        try {
            username = jwtService.extractUsername(jwt);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Use the injected UserDetailsService
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    filterLogger.debug("User '{}' authenticated successfully via JWT.", username);
                } else {
                     filterLogger.warn("JWT token validation failed for user '{}'.", username);
                }
            }
        } catch (ExpiredJwtException e) {
             filterLogger.warn("JWT token has expired: {}", e.getMessage());
        } catch (SignatureException e) {
             filterLogger.error("JWT signature validation failed: {}", e.getMessage());
        } catch (MalformedJwtException e) {
             filterLogger.error("JWT token is malformed: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
             filterLogger.error("Unsupported JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
             filterLogger.error("JWT claims string is empty or argument invalid: {}", e.getMessage());
        } catch (Exception e) {
             filterLogger.error("Error processing JWT or loading user details: {}", e.getMessage(), e);
        }

        filterChain.doFilter(request, response);
    }
}

