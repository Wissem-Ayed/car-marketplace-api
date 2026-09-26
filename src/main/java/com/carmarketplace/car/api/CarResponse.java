package com.carmarketplace.car.api;

import com.carmarketplace.car.domain.CarStatus;
import com.carmarketplace.car.domain.Engine;
import com.carmarketplace.car.domain.Equipment;
import com.carmarketplace.car.domain.History;
import com.carmarketplace.car.domain.Price;
import com.carmarketplace.car.domain.Seller;
import com.carmarketplace.car.domain.Vehicle;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Schema(name = "Car", description = "A car listing")
public record CarResponse(
        String id,
        @Schema(description = "Who published the listing")
        Seller seller,
        Vehicle vehicle,
        Engine engine,
        History history,
        Price price,
        Set<Equipment> equipment,
        String description,
        @Schema(description = "Photos in display order; the first one is the cover")
        List<PhotoResponse> photos,
        CarStatus status,
        @Schema(description = "Incremented on every change, used to detect concurrent edits")
        Long version,
        Instant createdAt,
        Instant updatedAt) {
}
