package com.carmarketplace.car.application;

public class CarNotFoundException extends RuntimeException{

        public CarNotFoundException(String id) {
            super("Car with id '%s' was not found".formatted(id));
        }
}
