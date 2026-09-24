package com.carmarketplace.car.infrastructure;

import com.carmarketplace.car.domain.Car;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CarRepository extends MongoRepository<Car,String>,CarSearchRepository {
}
