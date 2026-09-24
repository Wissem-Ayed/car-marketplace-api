package com.carmarketplace.catalog.application;

import com.carmarketplace.common.domain.NotFoundException;

public class BrandNotFoundException extends NotFoundException {

    public BrandNotFoundException(String id) {
        super("Brand with id '%s' was not found".formatted(id));
    }
}
