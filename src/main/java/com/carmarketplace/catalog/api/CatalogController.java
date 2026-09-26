package com.carmarketplace.catalog.api;

import com.carmarketplace.catalog.application.CatalogService;
import com.carmarketplace.catalog.domain.Brand;
import com.carmarketplace.catalog.domain.CarModel;
import com.carmarketplace.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/v1/brands")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.REFERENCE_DATA_TAG)
public class CatalogController {

    private static final CacheControl REFERENCE_DATA_CACHE = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();

    private final CatalogService catalogService;

    @GetMapping
    @Operation(summary = "List brands", description = "Every brand of the catalog, sorted by name.")
    @ApiResponse(responseCode = "200", description = "The brands")
    public ResponseEntity<List<Brand>> getBrands() {
        return ResponseEntity.ok().cacheControl(REFERENCE_DATA_CACHE).body(catalogService.getBrands());
    }

    @GetMapping("/{brandId}/models")
    @Operation(
            summary = "List the models of a brand",
            description = "Each model comes with its generations and their production years (`toYear` is null "
                    + "while the generation is still produced).")
    @ApiResponse(responseCode = "200", description = "The models, sorted by name")
    @ApiResponse(responseCode = "404", description = "No brand with this id")
    public ResponseEntity<List<CarModel>> getModels(
            @Parameter(description = "Catalog id of the brand", example = "peugeot") @PathVariable String brandId) {
        return ResponseEntity.ok().cacheControl(REFERENCE_DATA_CACHE).body(catalogService.getModels(brandId));
    }
}
