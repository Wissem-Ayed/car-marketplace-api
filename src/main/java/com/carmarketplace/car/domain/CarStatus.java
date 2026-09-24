package com.carmarketplace.car.domain;

public enum CarStatus {
    AVAILABLE,
    RESERVED,
    SOLD;

    public boolean canTransitionTo(CarStatus target) {
        return switch (this) {
            case AVAILABLE -> target == RESERVED || target == SOLD;
            case RESERVED -> target == AVAILABLE || target == SOLD;
            case SOLD -> false;
        };
    }
}
