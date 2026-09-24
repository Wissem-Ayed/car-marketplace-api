package com.carmarketplace.car.application;

import com.carmarketplace.car.domain.BodyType;
import com.carmarketplace.car.domain.Color;
import com.carmarketplace.car.domain.Engine;
import com.carmarketplace.car.domain.Equipment;
import com.carmarketplace.car.domain.History;
import com.carmarketplace.car.domain.Price;

import java.util.Set;

public record CarDraft(
        VehicleSpec vehicle,
        Engine engine,
        History history,
        Price price,
        Set<Equipment> equipment,
        String description) {

    public record VehicleSpec(
            String brandId,
            String modelId,
            String generationId,
            String trim,
            int year,
            BodyType bodyType,
            int doors,
            int seats,
            Color color) {
    }
}
