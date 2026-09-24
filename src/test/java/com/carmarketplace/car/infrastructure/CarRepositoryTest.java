package com.carmarketplace.car.infrastructure;

import com.carmarketplace.TestcontainersConfiguration;
import com.carmarketplace.car.domain.Car;
import com.carmarketplace.car.domain.CarSearchCriteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Import(TestcontainersConfiguration.class)
class CarRepositoryTest {

    private static final Pageable FIRST_PAGE = PageRequest.of(0, 20);

    @Autowired
    private CarRepository carRepository;

    @BeforeEach
    void setUp() {
        carRepository.deleteAll();
        carRepository.saveAll(List.of(
                car("BMW", "X3", 2021, "25990.00"),
                car("BMW", "320d", 2016, "14500.00"),
                car("Audi", "A4", 2019, "18000.00"),
                car("Peugeot", "208", 2022, "9000.00"),
                car("Tesla", "Model 3", 2023, "38000.00")));
    }

    @Test
    void returnsAllCarsWhenNoFilterIsGiven() {
        Page<Car> result = carRepository.search(criteria(null, null, null, null, null), FIRST_PAGE);

        assertThat(result.getTotalElements()).isEqualTo(5);
    }

    @Test
    void filtersByBrandIgnoringCase() {
        Page<Car> result = carRepository.search(criteria("bmw", null, null, null, null), FIRST_PAGE);

        assertThat(result.getContent()).extracting(Car::model).containsExactlyInAnyOrder("X3", "320d");
    }

    @Test
    void treatsBrandAsLiteralTextNotAsRegex() {
        Page<Car> result = carRepository.search(criteria(".*", null, null, null, null), FIRST_PAGE);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void filtersByPriceRangeComparingNumbersNotText() {
        Page<Car> result = carRepository.search(criteria(null, "9000", "18000", null, null), FIRST_PAGE);

        assertThat(result.getContent()).extracting(Car::model).containsExactlyInAnyOrder("208", "320d", "A4");
    }

    @Test
    void filtersByYearRange() {
        Page<Car> result = carRepository.search(criteria(null, null, null, 2019, 2022), FIRST_PAGE);

        assertThat(result.getContent()).extracting(Car::model).containsExactlyInAnyOrder("X3", "A4", "208");
    }

    @Test
    void combinesFiltersWithPagingAndSorting() {
        Pageable cheapestFirst = PageRequest.of(0, 1, Sort.by("price"));

        Page<Car> result = carRepository.search(criteria(null, "10000", null, 2016, null), cheapestFirst);

        assertThat(result.getContent()).extracting(Car::model).containsExactly("320d");
        assertThat(result.getTotalElements()).isEqualTo(4);
        assertThat(result.getTotalPages()).isEqualTo(4);
    }

    private static Car car(String brand, String model, int year, String price) {
        return new Car(null, brand, model, year, 50000, new BigDecimal(price));
    }

    private static CarSearchCriteria criteria(String brand, String minPrice, String maxPrice, Integer minYear, Integer maxYear) {
        return new CarSearchCriteria(
                brand,
                minPrice == null ? null : new BigDecimal(minPrice),
                maxPrice == null ? null : new BigDecimal(maxPrice),
                minYear,
                maxYear);
    }
}
