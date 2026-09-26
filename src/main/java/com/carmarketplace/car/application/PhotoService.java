package com.carmarketplace.car.application;

import com.carmarketplace.car.domain.Car;
import com.carmarketplace.car.domain.Photo;
import com.carmarketplace.car.domain.PhotoVariant;
import com.carmarketplace.car.infrastructure.CarRepository;
import com.carmarketplace.common.domain.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoService {

    private static final String JPEG = "image/jpeg";

    private final CarRepository carRepository;
    private final PhotoProcessor photoProcessor;
    private final PhotoStorage photoStorage;
    private final Clock clock;

    public Car addPhotos(String carId, List<PhotoUpload> uploads, CurrentUser user) {
        Car car = getManageableCar(carId, user);
        car.ensurePhotosCanBeAdded(uploads.size());
        List<ProcessedPhoto> processed = uploads.stream().map(photoProcessor::process).toList();

        List<String> storedKeys = new ArrayList<>();
        try {
            List<Photo> photos = new ArrayList<>();
            for (ProcessedPhoto photo : processed) {
                String photoId = UUID.randomUUID().toString();
                for (Map.Entry<PhotoVariant, byte[]> variant : photo.variants().entrySet()) {
                    String key = variant.getKey().storageKey(carId, photoId);
                    photoStorage.store(key, variant.getValue(), JPEG);
                    storedKeys.add(key);
                }
                photos.add(new Photo(photoId, photo.width(), photo.height(), Instant.now(clock)));
            }
            return carRepository.save(car.addPhotos(photos));
        } catch (RuntimeException e) {
            deleteQuietly(storedKeys);
            throw e;
        }
    }

    public Car removePhoto(String carId, String photoId, CurrentUser user) {
        Car updated = carRepository.save(getManageableCar(carId, user).removePhoto(photoId));
        deleteQuietly(storageKeys(carId, photoId));
        return updated;
    }

    public Car reorderPhotos(String carId, List<String> photoIds, CurrentUser user) {
        return carRepository.save(getManageableCar(carId, user).reorderPhotos(photoIds));
    }

    public void deleteAllPhotos(Car car) {
        deleteQuietly(car.photos().stream()
                .flatMap(photo -> storageKeys(car.id(), photo.id()).stream())
                .toList());
    }

    private Car getManageableCar(String carId, CurrentUser user) {
        Car car = carRepository.findById(carId).orElseThrow(() -> new CarNotFoundException(carId));
        car.ensureManageableBy(user);
        return car;
    }

    private static List<String> storageKeys(String carId, String photoId) {
        return Arrays.stream(PhotoVariant.values()).map(variant -> variant.storageKey(carId, photoId)).toList();
    }

    private void deleteQuietly(Collection<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        try {
            photoStorage.delete(keys);
        } catch (RuntimeException e) {
            log.warn("Could not delete {} photo files, they are now orphaned: {}", keys.size(), keys, e);
        }
    }
}
