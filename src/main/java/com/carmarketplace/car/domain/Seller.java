package com.carmarketplace.car.domain;

import com.carmarketplace.common.domain.CurrentUser;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Objects;

public record Seller(@Field("id") String id, String name) {

    public Seller {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }

    public static Seller of(CurrentUser user) {
        return new Seller(user.id(), user.name());
    }
}
