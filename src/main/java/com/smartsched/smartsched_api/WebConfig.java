package com.smartsched.smartsched_api;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global CORS configuration (mirrors SecurityConfig) with env-driven origins.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("#{'${cors.allowed-origins:https://smartsched-client.vercel.app,http://localhost:3000,http://localhost:3001,http://127.0.0.1:3000,http://127.0.0.1:3001}'.split(',')}")
    private List<String> allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Cache-Control", "Content-Type", "Pragma", "Expires", "Accept", "User-Agent", "Referer")
                .allowCredentials(true);
        registry.addMapping("/")
                .allowedOrigins(allowedOrigins.toArray(new String[0]))
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
