package com.carmarketplace.car.api;

public final class CarJson {

    public static final String VALID = car("mercedes-benz", "mercedes-benz-cla", "c118", 2022);

    private CarJson() {
    }

    public static String car(String brandId, String modelId, String generationId, int year) {
        return """
                {
                  "vehicle": {
                    "brandId": "%s", "modelId": "%s", "generationId": %s, "trim": "250e AMG Line",
                    "year": %d, "bodyType": "SEDAN", "doors": 4, "seats": 5, "color": "GREY"
                  },
                  "engine": {
                    "fuelType": "PLUG_IN_HYBRID", "transmission": "AUTOMATIC", "powerHp": 218,
                    "fiscalPower": 8, "cylinders": 4, "displacementCc": 1332
                  },
                  "history": {
                    "mileageKm": 28000, "mileageCertified": true, "condition": "LIKE_NEW",
                    "origin": "IMPORTED", "registeredInTunisia": false, "previousOwners": 1
                  },
                  "price": { "amount": 185000.000, "negotiable": true },
                  "equipment": ["PANORAMIC_ROOF", "ABS", "ESP", "APPLE_CARPLAY_ANDROID_AUTO"],
                  "description": "Burmester sound system, Alcantara interior"
                }
                """.formatted(brandId, modelId, generationId == null ? "null" : "\"" + generationId + "\"", year);
    }
}
