package com.carmarketplace.car.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;

import com.carmarketplace.car.domain.Car;
import com.carmarketplace.car.domain.CarSearchCriteria;

import lombok.RequiredArgsConstructor;

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

        if (criteria.brand() != null && !criteria.brand().isBlank()) {
            filters.add(Criteria.where("brand").regex("^" + Pattern.quote(criteria.brand().strip()) + "$", "i"));
        }
        if (criteria.minPrice() != null || criteria.maxPrice() != null) {
            Criteria price = Criteria.where("price");
            if (criteria.minPrice() != null) {
                price.gte(criteria.minPrice());
            }
            if (criteria.maxPrice() != null) {
                price.lte(criteria.maxPrice());
            }
            filters.add(price);
        }
        if (criteria.minYear() != null || criteria.maxYear() != null) {
            Criteria year = Criteria.where("year");
            if (criteria.minYear() != null) {
                year.gte(criteria.minYear());
            }
            if (criteria.maxYear() != null) {
                year.lte(criteria.maxYear());
            }
            filters.add(year);
        }

        return filters.isEmpty() ? new Criteria() : new Criteria().andOperator(filters);
    }
}