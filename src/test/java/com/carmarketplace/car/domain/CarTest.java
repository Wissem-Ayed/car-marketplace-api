package com.carmarketplace.car.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static com.carmarketplace.car.domain.CarBuilder.aCar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CarTest {

    @Test
    void newCarIsAvailableAndNotYetPersisted() {
        Car template = aCar().build();

        Car car = Car.create(template.vehicle(), template.engine(), template.history(), template.price(),
                template.equipment(), template.description());

        assertThat(car.status()).isEqualTo(CarStatus.AVAILABLE);
        assertThat(car.id()).isNull();
        assertThat(car.version()).isNull();
    }

    @Test
    void keepsEquipmentInCatalogOrderAndImmutable() {
        Car car = aCar().equipment(Equipment.PANORAMIC_ROOF, Equipment.ABS, Equipment.FRONT_AIRBAGS).build();

        assertThat(car.equipment()).containsExactly(Equipment.FRONT_AIRBAGS, Equipment.ABS, Equipment.PANORAMIC_ROOF);
        assertThatThrownBy(() -> car.equipment().add(Equipment.ESP)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Nested
    class ChangeStatus {

        @ParameterizedTest(name = "{0} -> {1} allowed: {2}")
        @CsvSource({
                "AVAILABLE, RESERVED,  true",
                "AVAILABLE, SOLD,      true",
                "RESERVED,  AVAILABLE, true",
                "RESERVED,  SOLD,      true",
                "SOLD,      AVAILABLE, false",
                "SOLD,      RESERVED,  false"
        })
        void followsTheListingLifecycle(CarStatus from, CarStatus to, boolean allowed) {
            assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
        }

        @Test
        void returnsACopyWithTheNewStatus() {
            Car car = aCar().id("abc").status(CarStatus.RESERVED).build();

            Car sold = car.changeStatus(CarStatus.SOLD);

            assertThat(sold.status()).isEqualTo(CarStatus.SOLD);
            assertThat(car.status()).isEqualTo(CarStatus.RESERVED);
        }

        @Test
        void soldCarCannotGoBackOnSale() {
            Car car = aCar().id("abc").status(CarStatus.SOLD).build();

            assertThatThrownBy(() -> car.changeStatus(CarStatus.AVAILABLE))
                    .isInstanceOf(IllegalCarStateException.class)
                    .hasMessage("Car abc cannot move from SOLD to AVAILABLE");
        }

        @Test
        void changingToTheCurrentStatusIsANoOp() {
            Car car = aCar().status(CarStatus.SOLD).build();

            assertThat(car.changeStatus(CarStatus.SOLD)).isSameAs(car);
        }
    }

    @Nested
    class Update {

        @Test
        void replacesTheDetailsAndKeepsIdentityStatusAndAuditData() {
            Instant createdAt = Instant.parse("2026-01-01T10:00:00Z");
            Car car = new Car("abc", aCar().build().vehicle(), aCar().build().engine(), aCar().build().history(),
                    aCar().build().price(), aCar().build().equipment(), "old", CarStatus.RESERVED, 3L, createdAt, createdAt);
            Car newDetails = aCar().mileageKm(30_000).price("170000").build();

            Car updated = car.update(newDetails.vehicle(), newDetails.engine(), newDetails.history(),
                    newDetails.price(), newDetails.equipment(), "new");

            assertThat(updated.history().mileageKm()).isEqualTo(30_000);
            assertThat(updated.description()).isEqualTo("new");
            assertThat(updated.id()).isEqualTo("abc");
            assertThat(updated.status()).isEqualTo(CarStatus.RESERVED);
            assertThat(updated.version()).isEqualTo(3L);
            assertThat(updated.createdAt()).isEqualTo(createdAt);
        }

        @Test
        void soldCarCannotBeEdited() {
            Car car = aCar().id("abc").status(CarStatus.SOLD).build();

            assertThatThrownBy(() -> car.update(car.vehicle(), car.engine(), car.history(), car.price(),
                    car.equipment(), car.description()))
                    .isInstanceOf(IllegalCarStateException.class)
                    .hasMessage("Car abc is sold and can no longer be edited");
        }
    }
}
