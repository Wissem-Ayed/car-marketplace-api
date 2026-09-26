package com.carmarketplace.catalog.infrastructure;

import com.carmarketplace.catalog.domain.Brand;
import com.carmarketplace.catalog.domain.CarModel;
import com.carmarketplace.catalog.domain.Generation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
class CatalogSeeder implements ApplicationRunner {

    private static final String CATALOG_FILE = "catalog/catalog.json";

    private final BrandRepository brandRepository;
    private final CarModelRepository carModelRepository;
    private final JsonMapper jsonMapper;
    private final CatalogCache catalogCache;

    @Override
    public void run(ApplicationArguments args) throws IOException {
        CatalogFile catalog = readCatalogFile();

        List<Brand> brands = catalog.brands().stream()
                .map(brand -> new Brand(brand.id(), brand.name()))
                .toList();
        List<CarModel> models = catalog.brands().stream()
                .flatMap(brand -> brand.models().stream()
                        .map(model -> new CarModel(model.id(), brand.id(), model.name(), model.generations())))
                .toList();

        brandRepository.saveAll(brands);
        carModelRepository.saveAll(models);
        catalogCache.evictAll();
        log.info("Catalog loaded: {} brands, {} models", brands.size(), models.size());
    }

    private CatalogFile readCatalogFile() throws IOException {
        try (InputStream input = new ClassPathResource(CATALOG_FILE).getInputStream()) {
            return jsonMapper.readValue(input, CatalogFile.class);
        }
    }

    record CatalogFile(List<BrandEntry> brands) {
    }

    record BrandEntry(String id, String name, List<ModelEntry> models) {
    }

    record ModelEntry(String id, String name, List<Generation> generations) {
    }
}
