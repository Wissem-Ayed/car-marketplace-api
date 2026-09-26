package com.carmarketplace.car.domain;

import com.carmarketplace.common.domain.BusinessRuleViolationException;
import com.carmarketplace.common.domain.CurrentUser;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Document(collection = "cars")
@CompoundIndex(name = "brand_model", def = "{'vehicle.brand.id': 1, 'vehicle.model.id': 1}")
@CompoundIndex(name = "price", def = "{'price.amount': 1}")
@CompoundIndex(name = "year", def = "{'vehicle.year': 1}")
@CompoundIndex(name = "equipment", def = "{'equipment': 1}")
@CompoundIndex(name = "seller", def = "{'seller.id': 1}")
public record Car(
        @Id String id,
        Seller seller,
        Vehicle vehicle,
        Engine engine,
        History history,
        Price price,
        Set<Equipment> equipment,
        String description,
        List<Photo> photos,
        CarStatus status,
        @Version Long version,
        @CreatedDate Instant createdAt,
        @LastModifiedDate Instant updatedAt) {

    public static final int MAX_PHOTOS = 10;

    public Car {
        Objects.requireNonNull(seller, "seller must not be null");
        Objects.requireNonNull(vehicle, "vehicle must not be null");
        Objects.requireNonNull(engine, "engine must not be null");
        Objects.requireNonNull(history, "history must not be null");
        Objects.requireNonNull(price, "price must not be null");
        Objects.requireNonNull(status, "status must not be null");
        equipment = equipment == null || equipment.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(equipment));
        description = description == null || description.isBlank() ? null : description.strip();
        photos = photos == null ? List.of() : List.copyOf(photos);
    }

    public static Car create(Seller seller, Vehicle vehicle, Engine engine, History history, Price price,
                             Set<Equipment> equipment, String description) {
        return new Car(null, seller, vehicle, engine, history, price, equipment, description, List.of(),
                CarStatus.AVAILABLE, null, null, null);
    }

    public Car update(Vehicle vehicle, Engine engine, History history, Price price,
                      Set<Equipment> equipment, String description) {
        ensureEditable();
        return new Car(id, seller, vehicle, engine, history, price, equipment, description, photos,
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
        return new Car(id, seller, vehicle, engine, history, price, equipment, description, photos,
                target, version, createdAt, updatedAt);
    }

    public boolean isManageableBy(CurrentUser user) {
        return user.isAdmin() || seller.id().equals(user.id());
    }

    public void ensureManageableBy(CurrentUser user) {
        if (!isManageableBy(user)) {
            throw new CarAccessDeniedException(id);
        }
    }

    public void ensurePhotosCanBeAdded(int count) {
        ensureEditable();
        if (photos.size() + count > MAX_PHOTOS) {
            throw new BusinessRuleViolationException("A car can have at most %d photos; this one already has %d"
                    .formatted(MAX_PHOTOS, photos.size()));
        }
    }

    public Car addPhotos(List<Photo> newPhotos) {
        ensurePhotosCanBeAdded(newPhotos.size());
        List<Photo> allPhotos = new ArrayList<>(photos);
        allPhotos.addAll(newPhotos);
        return withPhotos(allPhotos);
    }

    public Car removePhoto(String photoId) {
        ensureEditable();
        if (photos.stream().noneMatch(photo -> photo.id().equals(photoId))) {
            throw new PhotoNotFoundException(id, photoId);
        }
        return withPhotos(photos.stream().filter(photo -> !photo.id().equals(photoId)).toList());
    }

    public Car reorderPhotos(List<String> photoIds) {
        ensureEditable();
        Map<String, Photo> photosById = photos.stream().collect(Collectors.toMap(Photo::id, Function.identity()));
        if (photoIds.size() != photos.size() || !photosById.keySet().equals(Set.copyOf(photoIds))) {
            throw new BusinessRuleViolationException(
                    "The new order must list each of the car's %d photos exactly once".formatted(photos.size()));
        }
        return withPhotos(photoIds.stream().map(photosById::get).toList());
    }

    private Car withPhotos(List<Photo> newPhotos) {
        return new Car(id, seller, vehicle, engine, history, price, equipment, description, newPhotos,
                status, version, createdAt, updatedAt);
    }

    private void ensureEditable() {
        if (status == CarStatus.SOLD) {
            throw new IllegalCarStateException("Car %s is sold and can no longer be edited".formatted(id));
        }
    }
}
