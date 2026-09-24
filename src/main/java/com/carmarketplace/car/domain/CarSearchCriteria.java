package com.carmarketplace.car.domain;


import java.math.BigDecimal;

public record CarSearchCriteria(
        String brand,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Integer minYear,
        Integer maxYear) {
}