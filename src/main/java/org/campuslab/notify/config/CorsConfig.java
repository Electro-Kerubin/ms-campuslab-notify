package org.campuslab.notify.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${CORS_ALLOWED_ORIGINS:}") String origins,
            @Value("${CORS_ALLOWED_METHODS:GET}") String methods,
            @Value("${CORS_ALLOWED_HEADERS:Authorization,Content-Type}") String headers) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(csv(origins));
        configuration.setAllowedMethods(csv(methods));
        configuration.setAllowedHeaders(csv(headers));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/actuator/**", configuration);
        return source;
    }

    private List<String> csv(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank() && !item.equals("*"))
                .toList();
    }
}