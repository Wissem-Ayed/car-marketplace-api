package com.carmarketplace.car.application;

import com.carmarketplace.car.domain.Car;
import com.carmarketplace.car.domain.CarSearchCriteria;
import com.carmarketplace.car.domain.CarStatus;
import com.carmarketplace.car.domain.CatalogRef;
import com.carmarketplace.car.domain.Vehicle;
import com.carmarketplace.car.infrastructure.CarRepository;
import com.carmarketplace.catalog.application.CatalogSelection;
import com.carmarketplace.catalog.application.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CarService {

    private final CarRepository carRepository;
    private final CatalogService catalogService;
    private final PhotoService photoService;

    public Car createCar(CarDraft draft) {
        Car car = Car.create(toVehicle(draft.vehicle()), draft.engine(), draft.history(), draft.price(),
                draft.equipment(), draft.description());
        return carRepository.save(car);
    }

    public Car getCar(String id) {
        return carRepository.findById(id).orElseThrow(() -> new CarNotFoundException(id));
    }

    public Page<Car> getCars(CarSearchCriteria criteria, Pageable pageable) {
        return carRepository.search(criteria, pageable);
    }

    public Car updateCar(String id, CarDraft draft) {
        Car updated = getCar(id).update(toVehicle(draft.vehicle()), draft.engine(), draft.history(), draft.price(),
                draft.equipment(), draft.description());
        return carRepository.save(updated);
    }

    public Car changeStatus(String id, CarStatus status) {
        Car car = getCar(id);
        Car changed = car.changeStatus(status);
        return changed == car ? car : carRepository.save(changed);
    }

    public void deleteCar(String id) {
        Car car = getCar(id);
        carRepository.delete(car);
        photoService.deleteAllPhotos(car);
    }

    private Vehicle toVehicle(CarDraft.VehicleSpec spec) {
        CatalogSelection selection = catalogService.select(
                spec.brandId(), spec.modelId(), spec.generationId(), spec.year());
        return new Vehicle(
                new CatalogRef(selection.brand().id(), selection.brand().name()),
                new CatalogRef(selection.model().id(), selection.model().name()),
                new CatalogRef(selection.generation().id(), selection.generation().name()),
                spec.trim(),
                spec.year(),
                spec.bodyType(),
                spec.doors(),
                spec.seats(),
                spec.color());
    }
}
