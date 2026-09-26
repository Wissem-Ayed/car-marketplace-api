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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

        List<Photo> photos = new ArrayList<>();
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (ProcessedPhoto photo : processed) {
            String photoId = UUID.randomUUID().toString();
            photo.variants().forEach((variant, content) -> files.put(variant.storageKey(carId, photoId), content));
            photos.add(new Photo(photoId, photo.width(), photo.height(), Instant.now(clock)));
        }

        List<String> storedKeys = storeInParallel(files);
        try {
            return carRepository.save(car.addPhotos(photos));
        } catch (RuntimeException e) {
            deleteQuietly(storedKeys);
            throw e;
        }
    }

    private List<String> storeInParallel(Map<String, byte[]> files) {
        List<String> storedKeys = Collections.synchronizedList(new ArrayList<>());
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> uploads = files.entrySet().stream()
                    .<Future<?>>map(file -> executor.submit(() -> {
                        photoStorage.store(file.getKey(), file.getValue(), JPEG);
                        storedKeys.add(file.getKey());
                    }))
                    .toList();
            for (Future<?> upload : uploads) {
                upload.get();
            }
        } catch (ExecutionException e) {
            deleteQuietly(List.copyOf(storedKeys));
            throw e.getCause() instanceof RuntimeException runtime ? runtime : new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            deleteQuietly(List.copyOf(storedKeys));
            throw new IllegalStateException("Photo upload was interrupted", e);
        }
        return List.copyOf(storedKeys);
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
