package com.myhosh.booking;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOriginPatterns(
                                "http://localhost:*",
                                "https://*.vercel.app",
                                "https://myhosh-salisbury.co.uk",
                                "https://*.myhosh-salisbury.co.uk",
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