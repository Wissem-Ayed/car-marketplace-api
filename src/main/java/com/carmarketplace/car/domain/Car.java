package com.carmarketplace.car.domain;


import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;

@Document(collection = "cars")
public record Car(
        @Id
        String id,
        String brand,
        String model,
        int year,
        int mileageKm,
        @Field(targetType = FieldType.DECIMAL128)
        BigDecimal price


) {
}
