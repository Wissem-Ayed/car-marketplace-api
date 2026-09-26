package com.carmarketplace.car.api;

import com.carmarketplace.car.domain.Equipment;
import com.carmarketplace.car.domain.EquipmentCategory;
import com.carmarketplace.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/equipment")
@Tag(name = OpenApiConfig.REFERENCE_DATA_TAG)
public class EquipmentController {

    private static final Map<EquipmentCategory, List<Equipment>> EQUIPMENT_BY_CATEGORY =
            Arrays.stream(Equipment.values())
                    .collect(Collectors.groupingBy(
                            Equipment::category,
                            () -> new EnumMap<>(EquipmentCategory.class),
                            Collectors.toUnmodifiableList()));

    @GetMapping
    @Operation(
            summary = "List equipment codes",
            description = "Codes accepted in a listing's `equipment`, grouped by category. "
                    + "They are language-neutral: clients translate them for display.")
    @ApiResponse(responseCode = "200", description = "Equipment codes by category")
    public ResponseEntity<Map<EquipmentCategory, List<Equipment>>> getEquipment() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(EQUIPMENT_BY_CATEGORY);
    }
}
