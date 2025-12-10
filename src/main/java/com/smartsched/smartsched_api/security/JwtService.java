package com.smartsched.smartsched_api.security;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;


@Service
public class JwtService {

    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);

    @Value("${jwt.secret}")
    private String secretKeyString;

    private static final long EXPIRATION_TIME_MS = 1000 * 60 * 60 * 24; // 24 hours

    // --- Core JWT Methods ---

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        String roles = userDetails.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.joining(","));
        extraClaims.put("role", roles);

        Key signingKey = getSigningKey();
        if (signingKey == null) {
             logger.error("Signing key is null during token generation!");
             throw new RuntimeException("JWT Signing Key configuration error.");
        }


        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME_MS))
                .signWith(signingKey, SignatureAlgorithm.HS256) // Use HS256
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            boolean isValid = (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
            if (!isValid) {
                 logger.warn("Token validation failed for user '{}'. Username match: {}, Not expired: {}",
                        userDetails.getUsername(),
                        username.equals(userDetails.getUsername()),
                        !isTokenExpired(token));
            }
             return isValid;
        } catch (SignatureException e) {
             logger.error("Invalid JWT signature: {}", e.getMessage());
        } catch (MalformedJwtException e) {
             logger.error("Invalid JWT token format: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
             logger.warn("Expired JWT token: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
             logger.error("Unsupported JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
             logger.error("JWT claims string is empty or argument is invalid: {}", e.getMessage());
        } catch (Exception e) {
             logger.error("Exception during token validation: {}", e.getMessage(), e);
        }
        return false;
    }

    // --- Helper Methods ---

    private boolean isTokenExpired(String token) {
         try {
             return extractExpiration(token).before(new Date());
         } catch (ExpiredJwtException e) {
             return true;
         } catch (Exception e) {
             logger.error("Could not determine token expiration: {}", e.getMessage());
             return true;
         }
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Parses the JWT token and extracts all claims.
     */
    private Claims extractAllClaims(String token) {
         Key signingKey = getSigningKey();
         if (signingKey == null) {
             logger.error("Signing key is null during claim extraction!");
             throw new IllegalArgumentException("JWT Signing Key configuration error.");
         }

         // --- USE ALTERNATIVE PARSING METHOD ---
         // Use Jwts.parser() which is less likely to have symbol issues
         // Note: setSigningKey might be deprecated but works. The modern way is verifyWith(key).
         return Jwts.parser()
                 .setSigningKey(signingKey) // Use the derived key
                 .build() // Build the parser
                 .parseClaimsJws(token) // Parse and verify signature
                 .getBody();
         // --- END ALTERNATIVE PARSING METHOD ---
    }


    /**
     * Gets the signing key used for JWT validation and generation.
     */
    private Key getSigningKey() {
        if (secretKeyString == null || secretKeyString.isEmpty()) {
             logger.error("FATAL: JWT Secret Key (jwt.secret) is not configured in application properties!");
             throw new IllegalArgumentException("JWT Secret Key is missing or empty in application properties.");
        }
        try {
            byte[] keyBytes = Decoders.BASE64.decode(secretKeyString);
             if (keyBytes.length < 32) {
                 logger.error("FATAL: JWT Secret Key is too short ({} bytes). It MUST be at least 32 bytes for HS256.", keyBytes.length);
                 throw new IllegalArgumentException("JWT Secret Key is too short. Must be at least 32 bytes (256 bits) after Base64 decoding.");
             }
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (IllegalArgumentException e) {
             logger.error("FATAL: Invalid Base64 encoding for JWT Secret Key (jwt.secret): {}", e.getMessage());
             throw new IllegalArgumentException("Invalid Base64 encoding for JWT Secret Key in application properties.", e);
        }
    }
}

