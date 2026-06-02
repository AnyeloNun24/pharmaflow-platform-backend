package com.pharmaflow.auth_service.config.filter;

import com.pharmaflow.auth_service.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class FilterConfig {

    private final JwtUtils jwtUtils;

    // Instancia el filtro como @Bean para que SecurityConfig pueda inyectarlo vía constructor
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(this.jwtUtils);
    }

    // Sin @Component, Spring Boot igual detecta el @Bean y lo registra en la cadena del servlet.
    // FilterRegistrationBean con setEnabled(false) cancela ese registro automático para que el
    // filtro corra únicamente dentro de la SecurityFilterChain (vía addFilterBefore en SecurityConfig)
    // y no se ejecute dos veces por request
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

}
