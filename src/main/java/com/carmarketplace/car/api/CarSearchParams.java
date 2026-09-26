package com.carmarketplace.car.api;

import com.carmarketplace.car.domain.BodyType;
import com.carmarketplace.car.domain.CarSearchCriteria;
import com.carmarketplace.car.domain.CarStatus;
import com.carmarketplace.car.domain.Condition;
import com.carmarketplace.car.domain.Equipment;
import com.carmarketplace.car.domain.FuelType;
import com.carmarketplace.car.domain.Governorate;
import com.carmarketplace.car.domain.Transmission;
import io.swagger.v3.oas.annotations.Parameter;

import java.math.BigDecimal;
import java.util.List;

public record CarSearchParams(
        @Parameter(description = "Catalog id of the brand", example = "peugeot")
        String brandId,

        @Parameter(description = "Catalog id of the model", example = "peugeot-208")
        String modelId,

        @Parameter(description = "Catalog id of the generation", example = "p21")
        String generationId,

        @Parameter(description = "Minimum price in TND, inclusive", example = "30000")
        BigDecimal minPrice,

        @Parameter(description = "Maximum price in TND, inclusive", example = "80000")
        BigDecimal maxPrice,

        @Parameter(description = "Oldest model year, inclusive", example = "2018")
        Integer minYear,

        @Parameter(description = "Newest model year, inclusive", example = "2024")
        Integer maxYear,

        @Parameter(description = "Maximum mileage in km", example = "100000")
        Integer maxMileageKm,

        @Parameter(description = "Fuel type")
        FuelType fuelType,

        @Parameter(description = "Gearbox type")
        Transmission transmission,

        @Parameter(description = "Body type")
        BodyType bodyType,

        @Parameter(description = "Overall condition")
        Condition condition,

        @Parameter(description = "Listing status; defaults to AVAILABLE so sold cars stay out of search results")
        CarStatus status,

        @Parameter(description = "Equipment the car must have; every code is required. "
                + "Repeat the parameter or separate codes with commas. Codes: `GET /api/v1/equipment`")
        List<Equipment> equipment,

        @Parameter(description = "Id of the seller, to list one seller's cars", example = "5f0c1a2e-0000-4000-8000-000000000001")
        String sellerId,

        @Parameter(description = "Governorates the car must be in; repeat the parameter or separate codes with commas. "
                + "Codes: `GET /api/v1/governorates`", example = "TUNIS,ARIANA,BEN_AROUS,MANOUBA")
        List<Governorate> governorate) {

    public CarSearchCriteria toCriteria() {
        return new CarSearchCriteria(brandId, modelId, generationId, minPrice, maxPrice, minYear, maxYear,
                maxMileageKm, fuelType, transmission, bodyType, condition, status != null ? status : CarStatus.AVAILABLE,
                equipment, sellerId, governorate);
    }
}
