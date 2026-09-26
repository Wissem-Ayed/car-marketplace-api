package com.carmarketplace.car.domain;

import com.carmarketplace.TestUsers;
import com.carmarketplace.common.domain.CurrentUser;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class CarBuilder {

    private String id;
    private Long version;
    private Seller seller = Seller.of(TestUsers.SELLER_1);
    private CatalogRef brand = new CatalogRef("mercedes-benz", "Mercedes-Benz");
    private CatalogRef model = new CatalogRef("mercedes-benz-cla", "CLA");
    private CatalogRef generation = new CatalogRef("c118", "C118, X118");
    private int year = 2022;
    private FuelType fuelType = FuelType.PLUG_IN_HYBRID;
    private Transmission transmission = Transmission.AUTOMATIC;
    private int mileageKm = 28_000;
    private BigDecimal price = new BigDecimal("185000");
    private Set<Equipment> equipment = Set.of(Equipment.ABS, Equipment.ESP);
    private CarStatus status = CarStatus.AVAILABLE;
    private List<Photo> photos = List.of();
    private Governorate governorate = Governorate.TUNIS;

    private CarBuilder() {
    }

    public static CarBuilder aCar() {
        return new CarBuilder();
    }

    public CarBuilder seller(CurrentUser user) {
        this.seller = Seller.of(user);
        return this;
    }

    public CarBuilder version(long version) {
        this.version = version;
        return this;
    }

    public CarBuilder id(String id) {
        this.id = id;
        return this;
    }

    public CarBuilder brand(String id, String name) {
        this.brand = new CatalogRef(id, name);
        return this;
    }

    public CarBuilder model(String id, String name) {
        this.model = new CatalogRef(id, name);
        return this;
    }

    public CarBuilder year(int year) {
        this.year = year;
        return this;
    }

    public CarBuilder fuelType(FuelType fuelType) {
        this.fuelType = fuelType;
        return this;
    }

    public CarBuilder transmission(Transmission transmission) {
        this.transmission = transmission;
        return this;
    }

    public CarBuilder mileageKm(int mileageKm) {
        this.mileageKm = mileageKm;
        return this;
    }

    public CarBuilder price(String price) {
        this.price = new BigDecimal(price);
        return this;
    }

    public CarBuilder equipment(Equipment... equipment) {
        this.equipment = Set.of(equipment);
        return this;
    }

    public CarBuilder photos(String... photoIds) {
        this.photos = Arrays.stream(photoIds)
                .map(photoId -> new Photo(photoId, 1920, 1080, Instant.parse("2026-01-01T10:00:00Z")))
                .toList();
        return this;
    }

    public CarBuilder governorate(Governorate governorate) {
        this.governorate = governorate;
        return this;
    }

    public CarBuilder status(CarStatus status) {
        this.status = status;
        return this;
    }

    public Car build() {
        boolean electric = fuelType == FuelType.ELECTRIC;
        return new Car(
                id,
                seller,
                new Vehicle(brand, model, generation, "Test trim", year, BodyType.SEDAN, 4, 5, Color.GREY),
                new Engine(fuelType, transmission, 150, 8, electric ? null : 4, electric ? null : 1500),
                new History(mileageKm, true, Condition.VERY_GOOD, Origin.LOCAL, true, 1),
                Price.tnd(price, true),
                new Location(governorate, "Test city"),
                equipment,
                "Test description",
                photos,
                status,
                version,
                null,
                null);
    }
}
