package com.carmarketplace.car.api;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PhotoOrderRequest(
        @ArraySchema(arraySchema = @Schema(
                description = "Every photo id of the car, in the new display order. The first one becomes the cover.",
                example = "[\"7d1b4c22-0c55-4f3e-a1b2-6e8f9d0c3b11\", \"3f2c9a1e-8b4d-4c1a-9f0e-2d6b7a5c1e90\"]"))
        @NotEmpty List<@NotBlank String> photoIds) {
}
