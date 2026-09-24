package com.carmarketplace.car.application;

import com.carmarketplace.car.domain.Car;
import com.carmarketplace.car.domain.CarSearchCriteria;
import com.carmarketplace.car.infrastructure.CarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CarService {

    private final CarRepository carRepository;

    public Car saveCar(Car car) {
        return carRepository.save(car);
    }

    public Car getCar(String carId){
        return  carRepository.findById(carId).orElseThrow(()->new CarNotFoundException(carId));
    }

    public Page<Car> getCars(CarSearchCriteria carSearchCriteria, Pageable pageable) {
        return carRepository.search(carSearchCriteria, pageable);
    }

}
