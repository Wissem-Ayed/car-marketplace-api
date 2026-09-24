package com.carmarketplace.car.domain;

import java.util.Objects;

public record Vehicle(
        CatalogRef brand,
        CatalogRef model,
        CatalogRef generation,
        String trim,
        int year,
        BodyType bodyType,
        int doors,
        int seats,
        Color color) {

    public Vehicle {
        Objects.requireNonNull(brand, "brand must not be null");
        Objects.requireNonNull(model, "model must not be null");
        Objects.requireNonNull(generation, "generation must not be null");
        Objects.requireNonNull(bodyType, "bodyType must not be null");
        Objects.requireNonNull(color, "color must not be null");
        trim = trim == null || trim.isBlank() ? null : trim.strip();
    }
}
