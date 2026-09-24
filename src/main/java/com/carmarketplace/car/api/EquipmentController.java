package com.carmarketplace.car.api;

import com.carmarketplace.car.domain.Equipment;
import com.carmarketplace.car.domain.EquipmentCategory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/equipment")
public class EquipmentController {

    private static final Map<EquipmentCategory, List<Equipment>> EQUIPMENT_BY_CATEGORY =
            Arrays.stream(Equipment.values())
                    .collect(Collectors.groupingBy(
                            Equipment::category,
                            () -> new EnumMap<>(EquipmentCategory.class),
                            Collectors.toUnmodifiableList()));

    @GetMapping
    public Map<EquipmentCategory, List<Equipment>> getEquipment() {
        return EQUIPMENT_BY_CATEGORY;
    }
}
