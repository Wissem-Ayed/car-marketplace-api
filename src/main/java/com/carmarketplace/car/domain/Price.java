package com.carmarketplace.car.domain;

import com.carmarketplace.common.domain.BusinessRuleViolationException;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Price(
        @Field(targetType = FieldType.DECIMAL128) BigDecimal amount,
        String currency,
        boolean negotiable) {

    public static final String TND = "TND";
    private static final int TND_DECIMALS = 3;

    public Price {
        Objects.requireNonNull(amount, "amount must not be null");
        if (!TND.equals(currency)) {
            throw new BusinessRuleViolationException("Only prices in %s are supported".formatted(TND));
        }
        if (amount.signum() <= 0) {
            throw new BusinessRuleViolationException("Price must be positive");
        }
        if (amount.stripTrailingZeros().scale() > TND_DECIMALS) {
            throw new BusinessRuleViolationException(
                    "A price in %s has at most %d decimals".formatted(TND, TND_DECIMALS));
        }
        amount = amount.setScale(TND_DECIMALS, RoundingMode.UNNECESSARY);
    }

    public static Price tnd(BigDecimal amount, boolean negotiable) {
        return new Price(amount, TND, negotiable);
    }
}
