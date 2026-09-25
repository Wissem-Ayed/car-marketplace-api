package com.carmarketplace.car.api;

import com.carmarketplace.car.domain.CarStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record StatusRequest(
        @Schema(description = "Target status", example = "RESERVED")
        @NotNull CarStatus status) {
}
