package com.carmarketplace.catalog;

import com.carmarketplace.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CatalogIntegrationTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void seedsTheCatalogAtStartup() {
        assertThat(mongoTemplate.getCollection("brands").countDocuments()).isEqualTo(21);
        assertThat(mongoTemplate.getCollection("car_models").countDocuments()).isEqualTo(111);
    }

    @Test
    void createsAnIndexOnBrandId() {
        assertThat(mongoTemplate.indexOps("car_models").getIndexInfo())
                .anySatisfy(index -> assertThat(index.isIndexForFields(List.of("brandId"))).isTrue());
    }

    @Test
    void listsBrandsSortedByName() {
        assertThat(mvc.get().uri("/api/v1/brands"))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.length()").isEqualTo(21);
                    assertThat(json).extractingPath("$[0].name").isEqualTo("Audi");
                });
    }

    @Test
    void listsModelsOfABrandWithTheirGenerations() {
        assertThat(mvc.get().uri("/api/v1/brands/mercedes-benz/models"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[?(@.id == 'mercedes-benz-cla')].generations[1].name")
                .asArray().containsExactly("C118, X118");
    }

    @Test
    void returns404ForUnknownBrand() {
        assertThat(mvc.get().uri("/api/v1/brands/lamborghini-tractors/models"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").isEqualTo("Brand with id 'lamborghini-tractors' was not found");
    }
}
