package com.carmarketplace.car.domain;

import java.math.BigDecimal;
import java.util.List;

public record CarSearchCriteria(
        String brandId,
        String modelId,
        String generationId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Integer minYear,
        Integer maxYear,
        Integer maxMileageKm,
        FuelType fuelType,
        Transmission transmission,
        BodyType bodyType,
        Condition condition,
        CarStatus status,
        List<Equipment> equipment,
        String sellerId) {

    public static CarSearchCriteria bySeller(String sellerId) {
        return new CarSearchCriteria(null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, sellerId);
    }
}
