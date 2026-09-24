package com.carmarketplace.car.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.carmarketplace.car.domain.Car;
import com.carmarketplace.car.domain.CarSearchCriteria;

public interface CarSearchRepository {

    Page<Car> search(CarSearchCriteria criteria, Pageable pageable);
}