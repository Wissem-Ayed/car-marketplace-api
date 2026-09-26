package com.carmarketplace.catalog.application;

import com.carmarketplace.catalog.domain.Brand;
import com.carmarketplace.catalog.domain.CarModel;
import com.carmarketplace.catalog.domain.Generation;
import com.carmarketplace.catalog.infrastructure.CatalogCache;
import com.carmarketplace.common.domain.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    private static final Brand MERCEDES = new Brand("mercedes-benz", "Mercedes-Benz");
    private static final Generation C117 = new Generation("c117", "C117", 2013, 2019);
    private static final Generation C118 = new Generation("c118", "C118, X118", 2019, null);
    private static final CarModel CLA = new CarModel("mercedes-benz-cla", "mercedes-benz", "CLA", List.of(C117, C118));

    @Mock
    private CatalogCache catalog;

    @InjectMocks
    private CatalogService catalogService;

    @Test
    void selectsBrandModelAndGeneration() {
        givenCatalogContainsMercedesCla();

        CatalogSelection selection = catalogService.select("mercedes-benz", "mercedes-benz-cla", "c118", 2022);

        assertThat(selection).isEqualTo(new CatalogSelection(MERCEDES, CLA, C118));
    }

    @Test
    void infersTheGenerationFromTheYearWhenItIsNotGiven() {
        givenCatalogContainsMercedesCla();

        CatalogSelection selection = catalogService.select("mercedes-benz", "mercedes-benz-cla", null, 2016);

        assertThat(selection.generation()).isEqualTo(C117);
    }

    @Test
    void requiresTheGenerationWhenTwoGenerationsWereProducedThatYear() {
        givenCatalogContainsMercedesCla();

        assertThatThrownBy(() -> catalogService.select("mercedes-benz", "mercedes-benz-cla", null, 2019))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Several generations of Mercedes-Benz CLA were produced in 2019, generationId is required");
    }

    @Test
    void rejectsAYearWhenNoGenerationWasProduced() {
        givenCatalogContainsMercedesCla();

        assertThatThrownBy(() -> catalogService.select("mercedes-benz", "mercedes-benz-cla", null, 2010))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Mercedes-Benz CLA was not produced in 2010");
    }

    @Test
    void rejectsAYearOutsideTheChosenGeneration() {
        givenCatalogContainsMercedesCla();

        assertThatThrownBy(() -> catalogService.select("mercedes-benz", "mercedes-benz-cla", "c118", 2015))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Mercedes-Benz CLA C118, X118 was produced in 2019–present, not in 2015");
    }

    @Test
    void rejectsAnUnknownGeneration() {
        givenCatalogContainsMercedesCla();

        assertThatThrownBy(() -> catalogService.select("mercedes-benz", "mercedes-benz-cla", "w999", 2022))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Generation 'w999' does not exist for Mercedes-Benz CLA");
    }

    @Test
    void rejectsAModelOfAnotherBrand() {
        CarModel golf = new CarModel("volkswagen-golf", "volkswagen", "Golf", List.of());
        given(catalog.brand("mercedes-benz")).willReturn(Optional.of(MERCEDES));
        given(catalog.model("volkswagen-golf")).willReturn(Optional.of(golf));

        assertThatThrownBy(() -> catalogService.select("mercedes-benz", "volkswagen-golf", null, 2022))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Model 'volkswagen-golf' does not exist for brand Mercedes-Benz");
    }

    @Test
    void rejectsAnUnknownBrand() {
        given(catalog.brand("tesla-motors")).willReturn(Optional.empty());

        assertThatThrownBy(() -> catalogService.select("tesla-motors", "tesla-model-3", null, 2022))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Unknown brand 'tesla-motors'");
    }

    private void givenCatalogContainsMercedesCla() {
        given(catalog.brand("mercedes-benz")).willReturn(Optional.of(MERCEDES));
        given(catalog.model("mercedes-benz-cla")).willReturn(Optional.of(CLA));
    }
}
