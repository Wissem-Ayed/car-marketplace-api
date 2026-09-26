package com.carmarketplace.car.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "A listing photo, available in three sizes")
public record PhotoResponse(
        @Schema(description = "Photo id", example = "3f2c9a1e-8b4d-4c1a-9f0e-2d6b7a5c1e90")
        String id,

        @Schema(description = "Width of the large version in pixels", example = "1920")
        int width,

        @Schema(description = "Height of the large version in pixels", example = "1080")
        int height,

        Instant uploadedAt,

        @Schema(description = "Public URLs of each size")
        Urls urls) {

    @Schema(name = "PhotoUrls")
    public record Urls(
            @Schema(description = "At most 400 px, for search results") String thumbnail,
            @Schema(description = "At most 1024 px, for the listing page") String medium,
            @Schema(description = "At most 1920 px, for full screen") String large) {
    }
}
