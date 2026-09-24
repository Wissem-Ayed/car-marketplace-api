package com.carmarketplace.car.domain;

import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Objects;

public record CatalogRef(@Field("id") String id, String name) {

    public CatalogRef {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }
}
