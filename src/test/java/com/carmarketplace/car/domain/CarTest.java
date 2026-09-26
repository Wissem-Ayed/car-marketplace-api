package com.carmarketplace.car.domain;

import com.carmarketplace.common.domain.BusinessRuleViolationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.List;

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
                    aCar().build().price(), aCar().build().equipment(), "old", List.of(), CarStatus.RESERVED, 3L, createdAt,
                    createdAt);
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

    @Nested
    class Photos {

        @Test
        void appendsNewPhotosAfterTheExistingOnes() {
            Car car = aCar().photos("a", "b").build();
            Car withNewPhoto = aCar().photos("c").build();

            Car updated = car.addPhotos(withNewPhoto.photos());

            assertThat(updated.photos()).extracting(Photo::id).containsExactly("a", "b", "c");
        }

        @Test
        void acceptsUpToTenPhotos() {
            Car car = aCar().photos("1", "2", "3", "4", "5", "6", "7", "8", "9").build();

            assertThat(car.addPhotos(aCar().photos("10").build().photos()).photos()).hasSize(Car.MAX_PHOTOS);
        }

        @Test
        void rejectsAnEleventhPhoto() {
            Car car = aCar().photos("1", "2", "3", "4", "5", "6", "7", "8", "9", "10").build();

            assertThatThrownBy(() -> car.ensurePhotosCanBeAdded(1))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessage("A car can have at most 10 photos; this one already has 10");
        }

        @Test
        void soldCarPhotosCannotChange() {
            Car car = aCar().id("abc").photos("a").status(CarStatus.SOLD).build();

            assertThatThrownBy(() -> car.ensurePhotosCanBeAdded(1)).isInstanceOf(IllegalCarStateException.class);
            assertThatThrownBy(() -> car.removePhoto("a")).isInstanceOf(IllegalCarStateException.class);
            assertThatThrownBy(() -> car.reorderPhotos(List.of("a"))).isInstanceOf(IllegalCarStateException.class);
        }

        @Test
        void removesAPhoto() {
            Car car = aCar().photos("a", "b", "c").build();

            assertThat(car.removePhoto("b").photos()).extracting(Photo::id).containsExactly("a", "c");
        }

        @Test
        void rejectsRemovingAnUnknownPhoto() {
            Car car = aCar().id("abc").photos("a").build();

            assertThatThrownBy(() -> car.removePhoto("zzz"))
                    .isInstanceOf(PhotoNotFoundException.class)
                    .hasMessage("Car abc has no photo with id 'zzz'");
        }

        @Test
        void reordersPhotosSoTheFirstBecomesTheCover() {
            Car car = aCar().photos("a", "b", "c").build();

            assertThat(car.reorderPhotos(List.of("c", "a", "b")).photos()).extracting(Photo::id)
                    .containsExactly("c", "a", "b");
        }

        @Test
        void rejectsAnOrderThatDoesNotListEachPhotoOnce() {
            Car car = aCar().photos("a", "b", "c").build();

            assertThatThrownBy(() -> car.reorderPhotos(List.of("a", "b")))
                    .isInstanceOf(BusinessRuleViolationException.class);
            assertThatThrownBy(() -> car.reorderPhotos(List.of("a", "a", "b")))
                    .isInstanceOf(BusinessRuleViolationException.class);
            assertThatThrownBy(() -> car.reorderPhotos(List.of("a", "b", "x")))
                    .hasMessage("The new order must list each of the car's 3 photos exactly once");
        }
    }
}
