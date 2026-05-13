package com.amrit.scim.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the OpenAPI 3.0 specification exposed by springdoc-openapi.
 * <p>
 * This bean customises the metadata shown in Swagger UI (title, description,
 * contact) and registers the {@code bearerAuth} security scheme so that
 * Swagger UI shows an "Authorize" button where you can paste the bearer token
 * and test endpoints without writing curl commands.
 * <p>
 * Interview talking-point: the {@code @SecurityRequirement(name = "bearerAuth")}
 * annotation on {@link UserController} references this scheme by name —
 * the names must match exactly or Swagger UI won't show the lock icon.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI scimOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SCIM 2.0 User Provisioning Service")
                        .description("""
                                A SCIM 2.0 compliant user provisioning API built with Java 17 and Spring Boot 3.
                                Implements RFC 7643 (SCIM Core Schema) and RFC 7644 (SCIM Protocol).
                                Designed for integration with IdPs such as Okta and Microsoft Entra ID.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Amrit")
                                .url("https://github.com/amrit"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("opaque")
                                        .description("Static bearer token — set scim.auth.token in application.properties")));
    }
}
