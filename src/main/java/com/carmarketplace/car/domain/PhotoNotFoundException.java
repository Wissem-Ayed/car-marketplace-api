package com.carmarketplace.car.domain;

import com.carmarketplace.common.domain.NotFoundException;

public class PhotoNotFoundException extends NotFoundException {

    public PhotoNotFoundException(String carId, String photoId) {
        super("Car %s has no photo with id '%s'".formatted(carId, photoId));
    }
}
