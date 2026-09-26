package com.carmarketplace.car.api;

import com.carmarketplace.car.application.PhotoStorage;
import com.carmarketplace.car.domain.Car;
import com.carmarketplace.car.domain.Photo;
import com.carmarketplace.car.domain.PhotoVariant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CarResponseMapper {

    private final PhotoStorage photoStorage;

    public CarResponse toResponse(Car car) {
        return new CarResponse(
                car.id(),
                car.vehicle(),
                car.engine(),
                car.history(),
                car.price(),
                car.equipment(),
                car.description(),
                car.photos().stream().map(photo -> toResponse(car.id(), photo)).toList(),
                car.status(),
                car.version(),
                car.createdAt(),
                car.updatedAt());
    }

    private PhotoResponse toResponse(String carId, Photo photo) {
        return new PhotoResponse(
                photo.id(),
                photo.width(),
                photo.height(),
                photo.uploadedAt(),
                new PhotoResponse.Urls(
                        url(carId, photo, PhotoVariant.THUMBNAIL),
                        url(carId, photo, PhotoVariant.MEDIUM),
                        url(carId, photo, PhotoVariant.LARGE)));
    }

    private String url(String carId, Photo photo, PhotoVariant variant) {
        return photoStorage.publicUrl(variant.storageKey(carId, photo.id()));
    }
}
