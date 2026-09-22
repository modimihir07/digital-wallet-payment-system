package com.wallet.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes Swagger UI at {@code /swagger-ui.html} with a global
 * JWT Authorize button (paste the Bearer token from /api/auth/login).
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Digital Wallet & Payment System API",
                version = "1.0.0",
                description = "JWT + BCrypt auth, auto-created wallets, top-ups, "
                        + "atomic P2P transfers, bill pay, admin freeze/audit/reports."),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {
}
