package com.carmarketplace.car.api;

import com.carmarketplace.car.application.CarDraft;
import com.carmarketplace.car.domain.BodyType;
import com.carmarketplace.car.domain.Color;
import com.carmarketplace.car.domain.Condition;
import com.carmarketplace.car.domain.Engine;
import com.carmarketplace.car.domain.Equipment;
import com.carmarketplace.car.domain.FuelType;
import com.carmarketplace.car.domain.History;
import com.carmarketplace.car.domain.Origin;
import com.carmarketplace.car.domain.Price;
import com.carmarketplace.car.domain.Transmission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;

public record CarRequest(
        @NotNull @Valid VehicleRequest vehicle,
        @NotNull @Valid EngineRequest engine,
        @NotNull @Valid HistoryRequest history,
        @NotNull @Valid PriceRequest price,
        Set<Equipment> equipment,
        @Size(max = 5000) String description) {

    public CarDraft toDraft() {
        return new CarDraft(vehicle.toSpec(), engine.toEngine(), history.toHistory(), price.toPrice(),
                equipment, description);
    }

    public record VehicleRequest(
            @NotBlank String brandId,
            @NotBlank String modelId,
            String generationId,
            @Size(max = 60) String trim,
            @NotNull @Min(1950) @Max(2100) Integer year,
            @NotNull BodyType bodyType,
            @NotNull @Min(2) @Max(5) Integer doors,
            @NotNull @Min(1) @Max(9) Integer seats,
            @NotNull Color color) {

        CarDraft.VehicleSpec toSpec() {
            return new CarDraft.VehicleSpec(brandId, modelId, generationId, trim, year, bodyType, doors, seats, color);
        }
    }

    public record EngineRequest(
            @NotNull FuelType fuelType,
            @NotNull Transmission transmission,
            @NotNull @Positive @Max(2000) Integer powerHp,
            @NotNull @Positive @Max(99) Integer fiscalPower,
            @Positive @Max(16) Integer cylinders,
            @Positive @Max(10000) Integer displacementCc) {

        Engine toEngine() {
            return new Engine(fuelType, transmission, powerHp, fiscalPower, cylinders, displacementCc);
        }
    }

    public record HistoryRequest(
            @NotNull @PositiveOrZero @Max(2_000_000) Integer mileageKm,
            boolean mileageCertified,
            @NotNull Condition condition,
            @NotNull Origin origin,
            boolean registeredInTunisia,
            @PositiveOrZero @Max(20) Integer previousOwners) {

        History toHistory() {
            return new History(mileageKm, mileageCertified, condition, origin, registeredInTunisia, previousOwners);
        }
    }

    public record PriceRequest(
            @NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal amount,
            boolean negotiable) {

        Price toPrice() {
            return Price.tnd(amount, negotiable);
        }
    }
}
