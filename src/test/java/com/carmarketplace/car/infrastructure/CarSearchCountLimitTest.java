package com.carmarketplace.car.infrastructure;

import com.carmarketplace.TestcontainersConfiguration;
import com.carmarketplace.car.domain.Car;
import com.carmarketplace.car.domain.CarSearchCriteria;
import com.carmarketplace.config.MongoConfig;
import com.carmarketplace.config.SearchConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.util.stream.IntStream;

import static com.carmarketplace.car.domain.CarBuilder.aCar;
import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Import({TestcontainersConfiguration.class, MongoConfig.class, SearchConfig.class})
@TestPropertySource(properties = "app.search.max-counted-results=5")
class CarSearchCountLimitTest {

    @Autowired
    private CarRepository carRepository;

    @BeforeEach
    void setUp() {
        carRepository.deleteAll();
        carRepository.saveAll(IntStream.range(0, 8).mapToObj(i -> aCar().build()).toList());
    }

    @Test
    void stopsCountingAtTheConfiguredLimit() {
        Page<Car> page = carRepository.search(CarSearchCriteria.bySeller(null), PageRequest.of(0, 2));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(5);
    }

    @Test
    void countsExactlyBelowTheLimit() {
        carRepository.deleteAll();
        carRepository.saveAll(IntStream.range(0, 3).mapToObj(i -> aCar().build()).toList());

        assertThat(carRepository.search(CarSearchCriteria.bySeller(null), PageRequest.of(0, 2)).getTotalElements())
                .isEqualTo(3);
    }
}
