package com.carmarketplace.car.infrastructure;

import com.carmarketplace.car.domain.Car;
import com.carmarketplace.car.domain.CarSearchCriteria;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
class CarSearchRepositoryImpl implements CarSearchRepository {

    private final MongoTemplate mongoTemplate;

    @Override
    public Page<Car> search(CarSearchCriteria criteria, Pageable pageable) {
        Query query = new Query(toCriteria(criteria));
        List<Car> cars = mongoTemplate.find(Query.of(query).with(pageable), Car.class);
        return PageableExecutionUtils.getPage(cars, pageable, () -> mongoTemplate.count(query, Car.class));
    }

    private static Criteria toCriteria(CarSearchCriteria criteria) {
        List<Criteria> filters = new ArrayList<>();

        addEquals(filters, "vehicle.brand.id", criteria.brandId());
        addEquals(filters, "vehicle.model.id", criteria.modelId());
        addEquals(filters, "vehicle.generation.id", criteria.generationId());
        addEquals(filters, "vehicle.bodyType", criteria.bodyType());
        addEquals(filters, "engine.fuelType", criteria.fuelType());
        addEquals(filters, "engine.transmission", criteria.transmission());
        addEquals(filters, "history.condition", criteria.condition());
        addEquals(filters, "status", criteria.status());
        addRange(filters, "price.amount", criteria.minPrice(), criteria.maxPrice());
        addRange(filters, "vehicle.year", criteria.minYear(), criteria.maxYear());
        addRange(filters, "history.mileageKm", null, criteria.maxMileageKm());
        if (criteria.equipment() != null && !criteria.equipment().isEmpty()) {
            filters.add(Criteria.where("equipment").all(criteria.equipment()));
        }

        return filters.isEmpty() ? new Criteria() : new Criteria().andOperator(filters);
    }

    private static void addEquals(List<Criteria> filters, String field, Object value) {
        if (value != null) {
            filters.add(Criteria.where(field).is(value));
        }
    }

    private static void addRange(List<Criteria> filters, String field, Object min, Object max) {
        if (min == null && max == null) {
            return;
        }
        Criteria range = Criteria.where(field);
        if (min != null) {
            range.gte(min);
        }
        if (max != null) {
            range.lte(max);
        }
        filters.add(range);
    }
}
