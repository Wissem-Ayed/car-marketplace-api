package com.carmarketplace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiDocumentationTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void documentsTheApi() throws IOException {
        MvcTestResult result = mvc.get().uri("/v3/api-docs").exchange();
        Files.writeString(Path.of("target", "openapi.json"),
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.info.title").isEqualTo("Car Marketplace API");
                    assertThat(json).extractingPath("$.paths['/api/v1/cars'].post.summary")
                            .isEqualTo("Publish a car listing");
                    assertThat(json).extractingPath("$.paths['/api/v1/cars/{id}'].get.responses['404'].content"
                                    + "['application/problem+json'].schema.$ref")
                            .isEqualTo("#/components/schemas/Problem");
                    assertThat(json).extractingPath("$.paths['/api/v1/cars'].get.parameters[?(@.name == 'brandId')]"
                                    + ".description")
                            .asArray().containsExactly("Catalog id of the brand");
                    assertThat(json).extractingPath("$.components.schemas.VehicleRequest.properties.brandId.example")
                            .isEqualTo("peugeot");
                });
    }
}
