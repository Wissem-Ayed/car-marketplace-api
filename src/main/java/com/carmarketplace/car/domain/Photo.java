package com.carmarketplace.car.domain;

import java.time.Instant;
import java.util.Objects;

public record Photo(String id, int width, int height, Instant uploadedAt) {

    public Photo {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(uploadedAt, "uploadedAt must not be null");
    }
}
