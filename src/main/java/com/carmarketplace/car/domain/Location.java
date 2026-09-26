package com.carmarketplace.car.domain;

import java.util.Objects;

public record Location(Governorate governorate, String city) {

    public Location {
        Objects.requireNonNull(governorate, "governorate must not be null");
        city = city == null || city.isBlank() ? null : city.strip();
    }
}
