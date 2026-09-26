package com.carmarketplace.car.domain;

import com.carmarketplace.common.domain.ForbiddenException;

public class CarAccessDeniedException extends ForbiddenException {

    public CarAccessDeniedException(String carId) {
        super("Only the seller or an administrator can manage car %s".formatted(carId));
    }
}
