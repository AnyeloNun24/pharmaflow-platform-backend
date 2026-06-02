package com.pharmaflow.auth_service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .addServersItem(new Server().url("http://localhost:9001").description("Local (directo)"))
                .addServersItem(new Server().url("http://localhost:8090").description("Dev (vía api-gateway)"))
                // Aplica el esquema JWT globalmente: todos los endpoints mostrarán el candado en Swagger UI
                // Los endpoints con @SecurityRequirements vacío lo anulan individualmente
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, jwtSecurityScheme()));
    }

    private Info apiInfo() {
        return new Info()
                .title("PharmaFlow - Auth Service")
                .description("Servicio de autenticación y autorización de la plataforma PharmaFlow. " +
                        "Gestiona JWT, refresh tokens con TTL híbrido, RBAC y auditoría de accesos.")
                .version("1.0.0")
                .contact(new Contact()
                        .name("Anyelo N.")
                        .email("anunezobi2004@gmail.com")
                        .url("https://github.com/AnyeloNun24/pharmaflow-platform-backend"))
                .license(new License()
                        .name("Apache License, Version 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0.txt"));
    }

    // Define el esquema de seguridad Bearer JWT que Swagger UI usa para enviar
    // el header Authorization: Bearer <token> en cada request desde la documentación
    private SecurityScheme jwtSecurityScheme() {
        return new SecurityScheme()
                .name(BEARER_SCHEME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Ingresa el access token obtenido en POST /auth/login");
    }

}
