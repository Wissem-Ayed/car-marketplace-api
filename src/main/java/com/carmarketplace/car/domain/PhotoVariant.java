package com.carmarketplace.car.domain;

public enum PhotoVariant {
    THUMBNAIL(400),
    MEDIUM(1024),
    LARGE(1920);

    private final int maxSize;

    PhotoVariant(int maxSize) {
        this.maxSize = maxSize;
    }

    public int maxSize() {
        return maxSize;
    }

    public String storageKey(String carId, String photoId) {
        return "cars/%s/%s/%s.jpg".formatted(carId, photoId, name().toLowerCase());
    }
}
