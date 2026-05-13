package com.medibook.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes Swagger UI at:  http://localhost:8081/swagger-ui/index.html
 * Raw OpenAPI JSON at:    http://localhost:8081/v3/api-docs
 *
 * The "bearerAuth" security scheme allows you to paste a JWT in the
 * Swagger UI's Authorize dialog and test protected endpoints directly.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI mediBookAuthOpenAPI() {
        final String schemeName = "bearerAuth";

        return new OpenAPI()
            .info(new Info()
                .title("MediBook — Auth Service API")
                .description("Handles user registration, JWT login/logout, OAuth2 (Google & GitHub), " +
                             "profile management, and token validation for the MediBook platform.")
                .version("v1.0")
                .contact(new Contact()
                    .name("MediBook Platform")
                    .email("support@medibook.com")))
            .addSecurityItem(new SecurityRequirement().addList(schemeName))
            .components(new Components()
                .addSecuritySchemes(schemeName,
                    new SecurityScheme()
                        .name(schemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Paste your JWT access token here (without the 'Bearer ' prefix)")));
    }
}