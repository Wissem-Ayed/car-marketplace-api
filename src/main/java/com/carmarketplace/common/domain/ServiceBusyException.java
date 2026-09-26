package com.carmarketplace.common.domain;

import java.time.Duration;

public class ServiceBusyException extends RuntimeException {

    private final Duration retryAfter;

    public ServiceBusyException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
