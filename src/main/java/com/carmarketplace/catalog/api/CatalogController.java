package com.carmarketplace.catalog.api;

import com.carmarketplace.catalog.application.CatalogService;
import com.carmarketplace.catalog.domain.Brand;
import com.carmarketplace.catalog.domain.CarModel;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/brands")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping
    public List<Brand> getBrands() {
        return catalogService.getBrands();
    }

    @GetMapping("/{brandId}/models")
    public List<CarModel> getModels(@PathVariable String brandId) {
        return catalogService.getModels(brandId);
    }
}
