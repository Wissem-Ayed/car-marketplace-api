package com.carmarketplace.account.api;

import com.carmarketplace.common.domain.CurrentUser;
import com.carmarketplace.common.domain.Role;
import com.carmarketplace.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@Tag(name = OpenApiConfig.ACCOUNT_TAG)
public class MeController {

    @GetMapping("/api/v1/me")
    @Operation(summary = "Who am I", description = "The logged-in user, as seen by the API.")
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME)
    @ApiResponse(responseCode = "200", description = "The current user")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    public MeResponse me(CurrentUser user) {
        return new MeResponse(user.id(), user.name(), user.roles());
    }

    @Schema(name = "Me")
    public record MeResponse(
            @Schema(description = "Permanent user id, also used as seller id", example = "5f0c1a2e-0000-4000-8000-000000000001")
            String id,
            @Schema(description = "Display name", example = "Yasmine Ben Ali")
            String name,
            Set<Role> roles) {
    }
}
