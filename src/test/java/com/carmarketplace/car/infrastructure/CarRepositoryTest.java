package com.carmarketplace.car.infrastructure;

import com.carmarketplace.TestcontainersConfiguration;
import com.carmarketplace.car.domain.Car;
import com.carmarketplace.car.domain.CarSearchCriteria;
import com.carmarketplace.car.domain.CarStatus;
import com.carmarketplace.car.domain.Equipment;
import com.carmarketplace.car.domain.FuelType;
import com.carmarketplace.car.domain.Transmission;
import com.carmarketplace.config.MongoConfig;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;

import java.math.BigDecimal;
import java.util.List;

import static com.carmarketplace.car.domain.CarBuilder.aCar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataMongoTest
@Import({TestcontainersConfiguration.class, MongoConfig.class})
class CarRepositoryTest {

    private static final Pageable FIRST_PAGE = PageRequest.of(0, 20);

    @Autowired
    private CarRepository carRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        carRepository.deleteAll();
    }

    @Nested
    class Search {

        @BeforeEach
        void setUp() {
            carRepository.saveAll(List.of(
                    aCar().brand("bmw", "BMW").model("bmw-serie-3", "Série 3").year(2016).price("14500")
                            .mileageKm(150_000).fuelType(FuelType.DIESEL).transmission(Transmission.MANUAL)
                            .equipment(Equipment.ABS).build(),
                    aCar().brand("bmw", "BMW").model("bmw-x3", "X3").year(2021).price("95000")
                            .mileageKm(40_000).fuelType(FuelType.PETROL)
                            .equipment(Equipment.ABS, Equipment.ESP, Equipment.CAMERA_360).build(),
                    aCar().brand("peugeot", "Peugeot").model("peugeot-208", "208").year(2022).price("52000")
                            .mileageKm(20_000).fuelType(FuelType.PETROL).transmission(Transmission.MANUAL)
                            .equipment(Equipment.ABS, Equipment.APPLE_CARPLAY_ANDROID_AUTO).build(),
                    aCar().brand("kia", "Kia").model("kia-picanto", "Picanto").year(2019).price("30000")
                            .mileageKm(60_000).fuelType(FuelType.PETROL).transmission(Transmission.MANUAL).build(),
                    aCar().brand("mg", "MG").model("mg-mg4", "MG4").year(2023).price("110000")
                            .mileageKm(10_000).fuelType(FuelType.ELECTRIC).status(CarStatus.SOLD)
                            .equipment(Equipment.ABS, Equipment.ESP, Equipment.APPLE_CARPLAY_ANDROID_AUTO).build()));
        }

        @Test
        void returnsEveryCarWithoutFilters() {
            assertThat(search(new SearchBuilder()).getTotalElements()).isEqualTo(5);
        }

        @Test
        void filtersByBrandAndModelIds() {
            assertThat(models(new SearchBuilder().brandId("bmw"))).containsExactlyInAnyOrder("Série 3", "X3");
            assertThat(models(new SearchBuilder().brandId("bmw").modelId("bmw-x3"))).containsExactly("X3");
        }

        @Test
        void filtersByPriceRangeComparingNumbers() {
            assertThat(models(new SearchBuilder().minPrice("30000").maxPrice("95000")))
                    .containsExactlyInAnyOrder("Picanto", "208", "X3");
        }

        @Test
        void filtersByYearRangeAndMaximumMileage() {
            assertThat(models(new SearchBuilder().minYear(2019).maxMileageKm(40_000)))
                    .containsExactlyInAnyOrder("X3", "208", "MG4");
        }

        @Test
        void filtersByFuelTypeTransmissionAndStatus() {
            assertThat(models(new SearchBuilder().fuelType(FuelType.PETROL).transmission(Transmission.MANUAL)))
                    .containsExactlyInAnyOrder("208", "Picanto");
            assertThat(models(new SearchBuilder().status(CarStatus.SOLD))).containsExactly("MG4");
        }

        @Test
        void requiresAllRequestedEquipment() {
            assertThat(models(new SearchBuilder().equipment(Equipment.ABS, Equipment.APPLE_CARPLAY_ANDROID_AUTO)))
                    .containsExactlyInAnyOrder("208", "MG4");
        }

        @Test
        void combinesFiltersWithPagingAndSorting() {
            Pageable cheapestFirst = PageRequest.of(0, 1, Sort.by("price.amount"));

            Page<Car> page = carRepository.search(new SearchBuilder().minPrice("40000").build(), cheapestFirst);

            assertThat(page.getContent()).extracting(car -> car.vehicle().model().name()).containsExactly("208");
            assertThat(page.getTotalElements()).isEqualTo(3);
        }

        private Page<Car> search(SearchBuilder search) {
            return carRepository.search(search.build(), FIRST_PAGE);
        }

        private List<String> models(SearchBuilder search) {
            return search(search).getContent().stream().map(car -> car.vehicle().model().name()).toList();
        }
    }

    @Nested
    class Persistence {

        @Test
        void storesReadableFieldNamesAndNumericPrices() {
            carRepository.save(aCar().price("185000").equipment(Equipment.ESP, Equipment.ABS).build());

            Document stored = mongoTemplate.getCollection("cars").find().first();

            Document brand = stored.get("vehicle", Document.class).get("brand", Document.class);
            assertThat(brand).containsEntry("id", "mercedes-benz").containsEntry("name", "Mercedes-Benz");
            assertThat(stored.get("price", Document.class).get("amount")).isEqualTo(new Decimal128(new BigDecimal("185000.000")));
            assertThat(stored.getList("equipment", String.class)).containsExactly("ABS", "ESP");
        }

        @Test
        void setsVersionAndAuditTimestamps() {
            Car created = carRepository.save(aCar().build());

            Car updated = carRepository.save(created.changeStatus(CarStatus.RESERVED));

            assertThat(created.version()).isZero();
            assertThat(created.createdAt()).isNotNull();
            assertThat(updated.version()).isEqualTo(1L);
            assertThat(updated.createdAt()).isEqualTo(created.createdAt());
            assertThat(updated.updatedAt()).isAfterOrEqualTo(created.updatedAt());
        }

        @Test
        void rejectsSavingAStaleCopy() {
            Car created = carRepository.save(aCar().build());
            carRepository.save(created.changeStatus(CarStatus.RESERVED));

            assertThatThrownBy(() -> carRepository.save(created.changeStatus(CarStatus.SOLD)))
                    .isInstanceOf(OptimisticLockingFailureException.class);
        }

        @Test
        void createsTheSearchIndexes() {
            carRepository.save(aCar().build());

            assertThat(mongoTemplate.indexOps(Car.class).getIndexInfo())
                    .extracting(IndexInfo::getName)
                    .contains("brand_model", "price", "year", "equipment");
        }
    }

    private static final class SearchBuilder {

        private String brandId;
        private String modelId;
        private BigDecimal minPrice;
        private BigDecimal maxPrice;
        private Integer minYear;
        private Integer maxMileageKm;
        private FuelType fuelType;
        private Transmission transmission;
        private CarStatus status;
        private List<Equipment> equipment;

        SearchBuilder brandId(String brandId) {
            this.brandId = brandId;
            return this;
        }

        SearchBuilder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        SearchBuilder minPrice(String minPrice) {
            this.minPrice = new BigDecimal(minPrice);
            return this;
        }

        SearchBuilder maxPrice(String maxPrice) {
            this.maxPrice = new BigDecimal(maxPrice);
            return this;
        }

        SearchBuilder minYear(int minYear) {
            this.minYear = minYear;
            return this;
        }

        SearchBuilder maxMileageKm(int maxMileageKm) {
            this.maxMileageKm = maxMileageKm;
            return this;
        }

        SearchBuilder fuelType(FuelType fuelType) {
            this.fuelType = fuelType;
            return this;
        }

        SearchBuilder transmission(Transmission transmission) {
            this.transmission = transmission;
            return this;
        }

        SearchBuilder status(CarStatus status) {
            this.status = status;
            return this;
        }

        SearchBuilder equipment(Equipment... equipment) {
            this.equipment = List.of(equipment);
            return this;
        }

        CarSearchCriteria build() {
            return new CarSearchCriteria(brandId, modelId, null, minPrice, maxPrice, minYear, null, maxMileageKm,
                    fuelType, transmission, null, null, status, equipment);
        }
    }
}
