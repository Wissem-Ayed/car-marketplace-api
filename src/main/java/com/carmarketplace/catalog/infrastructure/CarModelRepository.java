package com.carmarketplace.catalog.infrastructure;

import com.carmarketplace.catalog.domain.CarModel;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CarModelRepository extends MongoRepository<CarModel, String> {

    List<CarModel> findByBrandIdOrderByNameAsc(String brandId);
}
