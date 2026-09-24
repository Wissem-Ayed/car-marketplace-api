package com.carmarketplace.car.api;

import com.carmarketplace.car.domain.CarStatus;
import jakarta.validation.constraints.NotNull;

public record StatusRequest(@NotNull CarStatus status) {
}
