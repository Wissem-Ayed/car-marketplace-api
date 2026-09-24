package com.carmarketplace.car.api;

import com.carmarketplace.car.domain.Car;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreateCarRequest(
        @NotBlank @Size(max = 50) String brand,
        @NotBlank @Size(max = 50) String model,
        @NotNull @Min(1886) @Max(2100) Integer year,
        @NotNull @PositiveOrZero Integer mileageKm,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal price
) {
    public Car toCar() {
        return new Car(null, brand, model, year, mileageKm, price);
    }
}
