package com.carmarketplace.catalog.application;

import com.carmarketplace.catalog.domain.Brand;
import com.carmarketplace.catalog.domain.CarModel;
import com.carmarketplace.catalog.domain.Generation;

public record CatalogSelection(Brand brand, CarModel model, Generation generation) {
}
