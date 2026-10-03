package com.Workmanagement.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String COOKIE_AUTH_SCHEME = "cookieAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        Schema<?> errorSchema = new Schema<>()
                .$ref("#/components/schemas/ApiErrorResponse");

        Content errorContent = new Content()
                .addMediaType("application/json", new MediaType().schema(errorSchema));

        return new OpenAPI()
                .info(new Info()
                        .title("AI-Powered Work Management Platform API")
                        .version("1.0.0")
                        .description("Backend API for an AI-powered work management platform " +
                                "that manages projects, tasks, submissions, AI evaluations, " +
                                "employee work, notifications, comments, and audit logs."))
                .components(new Components()
                        .addSecuritySchemes(COOKIE_AUTH_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("jwt")
                                .description("HttpOnly JWT authentication cookie named 'jwt'. " +
                                        "Automatically set by the server upon successful login (/api/auth/login) " +
                                        "and included by browsers in subsequent requests."))
                        .addResponses("400BadRequest", new ApiResponse()
                                .description("Bad Request - validation failed or malformed parameters")
                                .content(errorContent))
                        .addResponses("401Unauthorized", new ApiResponse()
                                .description("Unauthorized - missing, expired, or invalid authentication credentials")
                                .content(errorContent))
                        .addResponses("403Forbidden", new ApiResponse()
                                .description("Forbidden - authenticated user lacks necessary permissions")
                                .content(errorContent))
                        .addResponses("404NotFound", new ApiResponse()
                                .description("Not Found - the requested resource does not exist")
                                .content(errorContent))
                        .addResponses("409Conflict", new ApiResponse()
                                .description("Conflict - entity conflict or duplicate data violation")
                                .content(errorContent))
                        .addResponses("500InternalServerError", new ApiResponse()
                                .description("Internal Server Error - an unexpected error occurred")
                                .content(errorContent)))
                .addSecurityItem(new SecurityRequirement().addList(COOKIE_AUTH_SCHEME));
    }
}
