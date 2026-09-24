package com.carmarketplace.car.domain;

import com.carmarketplace.common.domain.BusinessRuleViolationException;

import java.util.Objects;

public record History(
        int mileageKm,
        boolean mileageCertified,
        Condition condition,
        Origin origin,
        boolean registeredInTunisia,
        Integer previousOwners) {

    static final int MAX_MILEAGE_OF_NEW_CAR_KM = 100;

    public History {
        Objects.requireNonNull(condition, "condition must not be null");
        Objects.requireNonNull(origin, "origin must not be null");
        if (condition == Condition.NEW && mileageKm > MAX_MILEAGE_OF_NEW_CAR_KM) {
            throw new BusinessRuleViolationException(
                    "A new car cannot have more than %d km".formatted(MAX_MILEAGE_OF_NEW_CAR_KM));
        }
        if (condition == Condition.NEW && previousOwners != null && previousOwners > 0) {
            throw new BusinessRuleViolationException("A new car cannot have previous owners");
        }
    }
}
