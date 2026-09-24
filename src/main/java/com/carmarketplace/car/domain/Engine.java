package com.carmarketplace.car.domain;

import com.carmarketplace.common.domain.BusinessRuleViolationException;

import java.util.Objects;

public record Engine(
        FuelType fuelType,
        Transmission transmission,
        int powerHp,
        int fiscalPower,
        Integer cylinders,
        Integer displacementCc) {

    public Engine {
        Objects.requireNonNull(fuelType, "fuelType must not be null");
        Objects.requireNonNull(transmission, "transmission must not be null");
        if (fuelType == FuelType.ELECTRIC && (cylinders != null || displacementCc != null)) {
            throw new BusinessRuleViolationException("An electric car has no cylinders or engine displacement");
        }
    }
}
