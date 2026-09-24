package com.carmarketplace.car.application;

import com.carmarketplace.common.domain.NotFoundException;

public class CarNotFoundException extends NotFoundException {

    public CarNotFoundException(String id) {
        super("Car with id '%s' was not found".formatted(id));
    }
}
