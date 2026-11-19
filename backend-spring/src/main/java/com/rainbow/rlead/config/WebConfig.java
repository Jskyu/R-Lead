package com.rainbow.rlead.config;

import org.springframework.context.annotation.Configuration;
import com.rainbow.rlead.domain.model.LeadStatus;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 설정.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "http://localhost:5173",
                        "http://localhost:3000",
                        "http://localhost:8000",
                        "http://localhost:8080"
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /** ?status=extracted / EXTRACTED 모두 허용. */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, LeadStatus.class, LeadStatus::fromValue);
    }
}
