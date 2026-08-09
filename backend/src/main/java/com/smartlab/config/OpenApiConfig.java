package com.smartlab.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {
    public static final String BEARER_AUTH = "bearerAuth";

    @Value("${smartlab.openapi.server-url:http://localhost:8080/api/v1.0}")
    private String serverUrl;

    @Bean
    public OpenAPI smartLabOpenAPI() {
        return new OpenAPI()
                .servers(List.of(new Server()
                        .url(serverUrl)
                        .description("SmartLab public API")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .name(BEARER_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste JWT access token from /login. Do not include the Bearer prefix.")))
                .info(new Info()
                        .title("SmartLab API")
                        .version("v1.0")
                        .description("Public OpenAPI documentation for SmartLab authentication, RBAC, account provisioning, invitations, and profile endpoints.")
                        .contact(new Contact()
                                .name("SmartLab Backend")
                                .email("tdgaming090@gmail.com")));
    }
}
