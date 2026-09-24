package com.carmarketplace.catalog.infrastructure;

import com.carmarketplace.catalog.domain.Brand;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BrandRepository extends MongoRepository<Brand, String> {
}
