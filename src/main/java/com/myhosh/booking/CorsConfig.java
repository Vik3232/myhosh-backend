package com.myhosh.booking;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        // M4 Lockdown: Only allow your Vercel frontend, local dev, and official domain
                        .allowedOriginPatterns(
                                "http://localhost:*",
                                "https://*.vercel.app",
                                "https://myhosh.co.uk",
                                "https://*.myhosh.co.uk"
                        )
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("Content-Type", "X-Staff-Token", "Accept", "Origin", "Authorization")
                        .exposedHeaders("Retry-After")
                        .maxAge(3600);
            }
        };
    }
}