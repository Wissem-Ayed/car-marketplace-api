package com.carmarketplace.car.application;

import com.carmarketplace.car.domain.PhotoVariant;

import java.util.Map;

public record ProcessedPhoto(int width, int height, Map<PhotoVariant, byte[]> variants) {
}
