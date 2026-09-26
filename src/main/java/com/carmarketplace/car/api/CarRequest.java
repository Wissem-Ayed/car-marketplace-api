package com.carmarketplace.car.api;

import com.carmarketplace.car.application.CarDraft;
import com.carmarketplace.car.domain.BodyType;
import com.carmarketplace.car.domain.Color;
import com.carmarketplace.car.domain.Condition;
import com.carmarketplace.car.domain.Engine;
import com.carmarketplace.car.domain.Equipment;
import com.carmarketplace.car.domain.FuelType;
import com.carmarketplace.car.domain.Governorate;
import com.carmarketplace.car.domain.History;
import com.carmarketplace.car.domain.Location;
import com.carmarketplace.car.domain.Origin;
import com.carmarketplace.car.domain.Price;
import com.carmarketplace.car.domain.Transmission;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
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

@Schema(description = "Everything a seller provides to publish or replace a listing")
public record CarRequest(
        @Schema(description = "What the car is: catalog references and body")
        @NotNull @Valid VehicleRequest vehicle,

        @Schema(description = "Engine and gearbox")
        @NotNull @Valid EngineRequest engine,

        @Schema(description = "Mileage, condition and ownership")
        @NotNull @Valid HistoryRequest history,

        @Schema(description = "Asking price")
        @NotNull @Valid PriceRequest price,

        @Schema(description = "Where the car can be seen")
        @NotNull @Valid LocationRequest location,

        @ArraySchema(
                arraySchema = @Schema(
                        description = "Equipment codes, see `GET /api/v1/equipment`. Duplicates are ignored.",
                        example = "[\"ABS\", \"ESP\", \"APPLE_CARPLAY_ANDROID_AUTO\", \"REAR_PARKING_SENSORS\"]"),
                uniqueItems = true)
        Set<Equipment> equipment,

        @Schema(description = "Free text shown on the listing", example = "First hand, full service history.",
                maxLength = 5000)
        @Size(max = 5000) String description) {

    public CarDraft toDraft() {
        return new CarDraft(vehicle.toSpec(), engine.toEngine(), history.toHistory(), price.toPrice(),
                location.toLocation(), equipment, description);
    }

    @Schema(name = "VehicleRequest")
    public record VehicleRequest(
            @Schema(description = "Catalog id of the brand, from `GET /api/v1/brands`", example = "peugeot")
            @NotBlank String brandId,

            @Schema(description = "Catalog id of the model, from `GET /api/v1/brands/{brandId}/models`",
                    example = "peugeot-208")
            @NotBlank String modelId,

            @Schema(description = "Catalog id of the generation. Optional when only one generation of the model "
                    + "was produced in `year`", example = "p21", nullable = true)
            String generationId,

            @Schema(description = "Version or finish level, free text", example = "Allure", nullable = true)
            @Size(max = 60) String trim,

            @Schema(description = "Model year, within the generation's production years", example = "2021")
            @NotNull @Min(1950) @Max(2100) Integer year,

            @Schema(example = "HATCHBACK")
            @NotNull BodyType bodyType,

            @Schema(example = "5")
            @NotNull @Min(2) @Max(5) Integer doors,

            @Schema(example = "5")
            @NotNull @Min(1) @Max(9) Integer seats,

            @Schema(example = "WHITE")
            @NotNull Color color) {

        CarDraft.VehicleSpec toSpec() {
            return new CarDraft.VehicleSpec(brandId, modelId, generationId, trim, year, bodyType, doors, seats, color);
        }
    }

    @Schema(name = "EngineRequest")
    public record EngineRequest(
            @Schema(example = "PETROL")
            @NotNull FuelType fuelType,

            @Schema(example = "MANUAL")
            @NotNull Transmission transmission,

            @Schema(description = "Power in horsepower", example = "100")
            @NotNull @Positive @Max(2000) Integer powerHp,

            @Schema(description = "Fiscal horsepower (CV fiscaux), which sets tax and insurance in Tunisia",
                    example = "5")
            @NotNull @Positive @Max(99) Integer fiscalPower,

            @Schema(description = "Number of cylinders. Must be empty for an electric car", example = "3",
                    nullable = true)
            @Positive @Max(16) Integer cylinders,

            @Schema(description = "Engine displacement in cm³. Must be empty for an electric car", example = "1199",
                    nullable = true)
            @Positive @Max(10000) Integer displacementCc) {

        Engine toEngine() {
            return new Engine(fuelType, transmission, powerHp, fiscalPower, cylinders, displacementCc);
        }
    }

    @Schema(name = "HistoryRequest")
    public record HistoryRequest(
            @Schema(description = "Mileage in km. At most 100 for a `NEW` car", example = "45000")
            @NotNull @PositiveOrZero @Max(2_000_000) Integer mileageKm,

            @Schema(description = "Whether the mileage is backed by service records or an inspection",
                    example = "true")
            boolean mileageCertified,

            @Schema(example = "VERY_GOOD")
            @NotNull Condition condition,

            @Schema(description = "`LOCAL`: first sold in Tunisia. `IMPORTED`: brought in from abroad",
                    example = "LOCAL")
            @NotNull Origin origin,

            @Schema(description = "Whether the car already has Tunisian registration plates", example = "true")
            boolean registeredInTunisia,

            @Schema(description = "Number of previous owners. Must be 0 for a `NEW` car", example = "1",
                    nullable = true)
            @PositiveOrZero @Max(20) Integer previousOwners) {

        History toHistory() {
            return new History(mileageKm, mileageCertified, condition, origin, registeredInTunisia, previousOwners);
        }
    }

    @Schema(name = "LocationRequest")
    public record LocationRequest(
            @Schema(description = "Governorate, see `GET /api/v1/governorates`", example = "SFAX")
            @NotNull Governorate governorate,

            @Schema(description = "City or delegation, free text", example = "Sakiet Ezzit", nullable = true)
            @Size(max = 60) String city) {

        Location toLocation() {
            return new Location(governorate, city);
        }
    }

    @Schema(name = "PriceRequest")
    public record PriceRequest(
            @Schema(description = "Asking price in Tunisian dinars (TND), up to 3 decimals", example = "62000")
            @NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal amount,

            @Schema(description = "Whether the seller accepts offers", example = "true")
            boolean negotiable) {

        Price toPrice() {
            return Price.tnd(amount, negotiable);
        }
    }
}
