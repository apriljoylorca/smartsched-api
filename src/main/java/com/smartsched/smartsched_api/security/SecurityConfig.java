package com.smartsched.smartsched_api.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.smartsched.smartsched_api.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * This is the main Security Configuration.
 * It DEPENDS ON: JwtAuthenticationFilter (to add to chain) and UserRepository (to create UserDetailsService).
 * It DOES NOT depend on anything that depends on it.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    // 2. DEPENDENCY: Inject the repository. This is correct.
    private final UserRepository userRepository;
    
    @Autowired
    private ApplicationContext applicationContext;

    @Value("#{'${cors.allowed-origins:http://localhost:3000,http://localhost:3001,http://127.0.0.1:3000,http://127.0.0.1:3001}'.split(',')}")
    private java.util.List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Get JwtAuthenticationFilter from application context to avoid circular dependency
        JwtAuthenticationFilter jwtAuthFilter = applicationContext.getBean(JwtAuthenticationFilter.class);
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/teachers/**", "/api/sections/**", "/api/classrooms/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/teachers/**", "/api/sections/**", "/api/classrooms/**", "/api/schedules/problem/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/teachers", "/api/sections", "/api/classrooms", "/api/schedules/solve").hasAnyRole("ADMIN", "SCHEDULER")
                        .requestMatchers(HttpMethod.GET, "/api/teachers/**", "/api/sections/**", "/api/classrooms/**", "/api/schedules/**").hasAnyRole("ADMIN", "SCHEDULER")
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 3. PROVIDER: Reference the auth provider bean
                .authenticationProvider(authenticationProvider())
                // 4. FILTER: Add the filter to the chain
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BEAN: Creates the UserDetailsService.
     * DEPENDS ON: UserRepository.
     * This is injected into JwtAuthenticationFilter and DaoAuthenticationProvider.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
    }

    /**
     * BEAN: Creates the PasswordEncoder.
     * DEPENDS ON: Nothing.
     * This is injected into UserService and DaoAuthenticationProvider.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * BEAN: Creates the AuthenticationProvider.
     * DEPENDS ON: UserDetailsService (bean) and PasswordEncoder (bean).
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService());
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * BEAN: Creates the AuthenticationManager.
     * DEPENDS ON: AuthenticationConfiguration (from Spring).
     * This is injected into AuthController.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * BEAN: Creates the CORS Configuration.
     * DEPENDS ON: Nothing.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Use allowedOriginPatterns instead of allowedOrigins when allowCredentials is true
        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Cache-Control", "Content-Type", "Pragma", "Expires", "Accept", "User-Agent", "Referer"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}

