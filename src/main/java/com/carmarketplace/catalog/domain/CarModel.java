package com.carmarketplace.catalog.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "car_models")
public record CarModel(
        @Id String id,
        @Indexed String brandId,
        String name,
        List<Generation> generations) {

    public CarModel {
        generations = generations == null ? List.of() : List.copyOf(generations);
    }
}
