package com.dextre.primerpaso.configuracion;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ConfiguracionWeb implements WebMvcConfigurer {

    private final String[] origenes;

    public ConfiguracionWeb(@Value("${primerpaso.cors.origenes}") String origenes) {
        this.origenes = Arrays.stream(origenes.split(","))
                .map(origen -> origen.strip())
                .filter(origen -> !origen.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registro) {
        registro.addMapping("/api/registro/**").allowedOrigins(origenes)
                .allowedMethods("POST", "OPTIONS").allowedHeaders("Content-Type")
                .maxAge(3600);
        registro.addMapping("/api/sesion/**").allowedOrigins(origenes)
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "X-CSRF-Token").allowCredentials(true).maxAge(3600);
    }
}
