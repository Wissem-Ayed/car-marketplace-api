package com.carmarketplace.common.domain;

public abstract class ForbiddenException extends RuntimeException {

    protected ForbiddenException(String message) {
        super(message);
    }
}
