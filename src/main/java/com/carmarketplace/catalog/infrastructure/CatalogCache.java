package com.carmarketplace.catalog.infrastructure;

import com.carmarketplace.catalog.domain.Brand;
import com.carmarketplace.catalog.domain.CarModel;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CatalogCache {

    static final String BRANDS = "catalog-brands";
    static final String BRAND = "catalog-brand";
    static final String MODELS_OF_BRAND = "catalog-models-of-brand";
    static final String MODEL = "catalog-model";

    private final BrandRepository brandRepository;
    private final CarModelRepository carModelRepository;

    @Cacheable(BRANDS)
    public List<Brand> allBrands() {
        return List.copyOf(brandRepository.findAll(Sort.by("name")));
    }

    @Cacheable(BRAND)
    public Optional<Brand> brand(String brandId) {
        return brandRepository.findById(brandId);
    }

    @Cacheable(MODELS_OF_BRAND)
    public List<CarModel> modelsOf(String brandId) {
        return List.copyOf(carModelRepository.findByBrandIdOrderByNameAsc(brandId));
    }

    @Cacheable(MODEL)
    public Optional<CarModel> model(String modelId) {
        return carModelRepository.findById(modelId);
    }

    @CacheEvict(cacheNames = {BRANDS, BRAND, MODELS_OF_BRAND, MODEL}, allEntries = true)
    public void evictAll() {
    }
}
