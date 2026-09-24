package com.carmarketplace.catalog.application;

import com.carmarketplace.catalog.domain.Brand;
import com.carmarketplace.catalog.domain.CarModel;
import com.carmarketplace.catalog.domain.Generation;
import com.carmarketplace.catalog.infrastructure.BrandRepository;
import com.carmarketplace.catalog.infrastructure.CarModelRepository;
import com.carmarketplace.common.domain.BusinessRuleViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final BrandRepository brandRepository;
    private final CarModelRepository carModelRepository;

    public List<Brand> getBrands() {
        return brandRepository.findAll(Sort.by("name"));
    }

    public List<CarModel> getModels(String brandId) {
        if (!brandRepository.existsById(brandId)) {
            throw new BrandNotFoundException(brandId);
        }
        return carModelRepository.findByBrandIdOrderByNameAsc(brandId);
    }

    public CatalogSelection select(String brandId, String modelId, String generationId, int year) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new BusinessRuleViolationException("Unknown brand '%s'".formatted(brandId)));
        CarModel model = carModelRepository.findById(modelId)
                .filter(candidate -> candidate.brandId().equals(brandId))
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Model '%s' does not exist for brand %s".formatted(modelId, brand.name())));
        Generation generation = generationId == null
                ? inferGeneration(brand, model, year)
                : findGeneration(brand, model, generationId, year);
        return new CatalogSelection(brand, model, generation);
    }

    private static Generation findGeneration(Brand brand, CarModel model, String generationId, int year) {
        Generation generation = model.generations().stream()
                .filter(candidate -> candidate.id().equals(generationId))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Generation '%s' does not exist for %s %s".formatted(generationId, brand.name(), model.name())));
        if (!generation.covers(year)) {
            throw new BusinessRuleViolationException("%s %s %s was produced in %s, not in %d".formatted(
                    brand.name(), model.name(), generation.name(), generation.productionYears(), year));
        }
        return generation;
    }

    private static Generation inferGeneration(Brand brand, CarModel model, int year) {
        List<Generation> candidates = model.generations().stream()
                .filter(generation -> generation.covers(year))
                .toList();
        if (candidates.isEmpty()) {
            throw new BusinessRuleViolationException(
                    "%s %s was not produced in %d".formatted(brand.name(), model.name(), year));
        }
        if (candidates.size() > 1) {
            throw new BusinessRuleViolationException(
                    "Several generations of %s %s were produced in %d, generationId is required"
                            .formatted(brand.name(), model.name(), year));
        }
        return candidates.getFirst();
    }
}
