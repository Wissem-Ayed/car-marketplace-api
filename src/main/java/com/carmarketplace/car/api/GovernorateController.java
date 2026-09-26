package com.carmarketplace.car.api;

import com.carmarketplace.car.domain.Governorate;
import com.carmarketplace.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/governorates")
@Tag(name = OpenApiConfig.REFERENCE_DATA_TAG)
public class GovernorateController {

    private static final List<GovernorateResponse> GOVERNORATES = Arrays.stream(Governorate.values())
            .map(governorate -> new GovernorateResponse(governorate, governorate.displayName()))
            .toList();

    @GetMapping
    @Operation(summary = "List governorates", description = "The 24 Tunisian governorates a listing can be located in.")
    @ApiResponse(responseCode = "200", description = "Governorate codes with their display names")
    public ResponseEntity<List<GovernorateResponse>> getGovernorates() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(GOVERNORATES);
    }

    @Schema(name = "Governorate")
    public record GovernorateResponse(
            @Schema(description = "Code used in listings and filters", example = "SFAX") Governorate code,
            @Schema(description = "Display name", example = "Sfax") String name) {
    }
}
