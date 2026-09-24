package com.carmarketplace.car.domain;

import com.carmarketplace.common.domain.ConflictException;

public class IllegalCarStateException extends ConflictException {

    public IllegalCarStateException(String message) {
        super(message);
    }
}
