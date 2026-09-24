package com.carmarketplace.car.domain;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

@Document(collection = "cars")
@CompoundIndex(name = "brand_model", def = "{'vehicle.brand.id': 1, 'vehicle.model.id': 1}")
@CompoundIndex(name = "price", def = "{'price.amount': 1}")
@CompoundIndex(name = "year", def = "{'vehicle.year': 1}")
@CompoundIndex(name = "equipment", def = "{'equipment': 1}")
public record Car(
        @Id String id,
        Vehicle vehicle,
        Engine engine,
        History history,
        Price price,
        Set<Equipment> equipment,
        String description,
        CarStatus status,
        @Version Long version,
        @CreatedDate Instant createdAt,
        @LastModifiedDate Instant updatedAt) {

    public Car {
        Objects.requireNonNull(vehicle, "vehicle must not be null");
        Objects.requireNonNull(engine, "engine must not be null");
        Objects.requireNonNull(history, "history must not be null");
        Objects.requireNonNull(price, "price must not be null");
        Objects.requireNonNull(status, "status must not be null");
        equipment = equipment == null || equipment.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(equipment));
        description = description == null || description.isBlank() ? null : description.strip();
    }

    public static Car create(Vehicle vehicle, Engine engine, History history, Price price,
                             Set<Equipment> equipment, String description) {
        return new Car(null, vehicle, engine, history, price, equipment, description,
                CarStatus.AVAILABLE, null, null, null);
    }

    public Car update(Vehicle vehicle, Engine engine, History history, Price price,
                      Set<Equipment> equipment, String description) {
        if (status == CarStatus.SOLD) {
            throw new IllegalCarStateException("Car %s is sold and can no longer be edited".formatted(id));
        }
        return new Car(id, vehicle, engine, history, price, equipment, description,
                status, version, createdAt, updatedAt);
    }

    public Car changeStatus(CarStatus target) {
        Objects.requireNonNull(target, "target must not be null");
        if (status == target) {
            return this;
        }
        if (!status.canTransitionTo(target)) {
            throw new IllegalCarStateException("Car %s cannot move from %s to %s".formatted(id, status, target));
        }
        return new Car(id, vehicle, engine, history, price, equipment, description,
                target, version, createdAt, updatedAt);
    }
}
