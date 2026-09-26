package com.carmarketplace.car;

import com.carmarketplace.TestcontainersConfiguration;
import com.carmarketplace.car.api.CarJson;
import com.carmarketplace.common.domain.CurrentUser;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.carmarketplace.TestUsers.ADMIN;
import static com.carmarketplace.TestUsers.SELLER_1;
import static com.carmarketplace.TestUsers.SELLER_2;
import static com.carmarketplace.TestUsers.loggedInAs;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CarApiIntegrationTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        mongoTemplate.getCollection("cars").deleteMany(new Document());
    }

    @Test
    void createsACarWithCatalogNamesStatusVersionAndTimestamps() {
        assertThat(create(CarJson.VALID))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.vehicle.brand.name").isEqualTo("Mercedes-Benz");
                    assertThat(json).extractingPath("$.vehicle.model.name").isEqualTo("CLA");
                    assertThat(json).extractingPath("$.vehicle.generation.name").isEqualTo("C118, X118");
                    assertThat(json).extractingPath("$.price.currency").isEqualTo("TND");
                    assertThat(json).extractingPath("$.equipment")
                            .asArray().containsExactly("ABS", "ESP", "APPLE_CARPLAY_ANDROID_AUTO", "PANORAMIC_ROOF");
                    assertThat(json).extractingPath("$.status").isEqualTo("AVAILABLE");
                    assertThat(json).extractingPath("$.version").isEqualTo(0);
                    assertThat(json).extractingPath("$.createdAt").isNotNull();
                });
    }

    @Test
    void theLoggedInUserBecomesTheSeller() {
        assertThat(create(CarJson.VALID, SELLER_2))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.seller.id").isEqualTo(SELLER_2.id());
                    assertThat(json).extractingPath("$.seller.name").isEqualTo("Karim Haddad");
                });
    }

    @Test
    void anonymousVisitorsCanBrowseButNotPublish() {
        String id = idOf(create(CarJson.VALID));

        assertThat(mvc.get().uri("/api/v1/cars/" + id)).hasStatusOk();
        assertThat(mvc.post().uri("/api/v1/cars").contentType(MediaType.APPLICATION_JSON).content(CarJson.VALID))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void onlyTheSellerOrAnAdministratorCanManageAListing() {
        String id = idOf(create(CarJson.VALID, SELLER_1));

        assertThat(changeStatus(id, "RESERVED", SELLER_2)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.put().uri("/api/v1/cars/" + id).with(loggedInAs(SELLER_2))
                .contentType(MediaType.APPLICATION_JSON).content(CarJson.VALID))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.delete().uri("/api/v1/cars/" + id).with(loggedInAs(SELLER_2))).hasStatus(HttpStatus.FORBIDDEN);

        assertThat(changeStatus(id, "RESERVED", ADMIN)).hasStatusOk();
        assertThat(changeStatus(id, "AVAILABLE", SELLER_1)).hasStatusOk();
    }

    @Test
    void listsTheListingsOfTheLoggedInUser() {
        create(CarJson.VALID, SELLER_1);
        create(CarJson.car("peugeot", "peugeot-208", "p21", 2021), SELLER_2);

        assertThat(mvc.get().uri("/api/v1/me/cars").with(loggedInAs(SELLER_1)))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.page.totalElements").isEqualTo(1);
                    assertThat(json).extractingPath("$.content[0].seller.id").isEqualTo(SELLER_1.id());
                });
        assertThat(mvc.get().uri("/api/v1/me").with(loggedInAs(ADMIN)))
                .hasStatusOk()
                .bodyJson().extractingPath("$.roles").asArray().containsExactlyInAnyOrder("USER", "ADMIN");
        assertThat(mvc.get().uri("/api/v1/me/cars")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void infersTheGenerationFromTheYear() {
        assertThat(create(CarJson.car("peugeot", "peugeot-208", null, 2016)))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.vehicle.generation.id").isEqualTo("a9");
    }

    @Test
    void rejectsAModelThatDoesNotBelongToTheBrand() {
        assertThat(create(CarJson.car("peugeot", "renault-clio", null, 2020)))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.detail").isEqualTo("Model 'renault-clio' does not exist for brand Peugeot");
    }

    @Test
    void rejectsAYearOutsideTheGeneration() {
        assertThat(create(CarJson.car("mercedes-benz", "mercedes-benz-cla", "c118", 2015)))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.detail")
                .isEqualTo("Mercedes-Benz CLA C118, X118 was produced in 2019–present, not in 2015");
    }

    @Test
    void followsTheListingLifecycle() {
        String id = idOf(create(CarJson.VALID));

        assertThat(changeStatus(id, "RESERVED")).hasStatusOk();
        assertThat(changeStatus(id, "SOLD")).hasStatusOk().bodyJson().extractingPath("$.version").isEqualTo(2);
        assertThat(changeStatus(id, "AVAILABLE")).hasStatus(HttpStatus.CONFLICT);
        assertThat(mvc.put().uri("/api/v1/cars/" + id).with(loggedInAs(SELLER_1)).contentType(MediaType.APPLICATION_JSON).content(CarJson.VALID))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").isEqualTo("Car " + id + " is sold and can no longer be edited");
    }

    @Test
    void searchesByCatalogIdsAndEquipment() {
        create(CarJson.VALID);
        create(CarJson.car("peugeot", "peugeot-208", "p21", 2021));

        assertThat(mvc.get().uri("/api/v1/cars?brandId=mercedes-benz&equipment=ABS,PANORAMIC_ROOF"))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.page.totalElements").isEqualTo(1);
                    assertThat(json).extractingPath("$.content[0].vehicle.model.name").isEqualTo("CLA");
                });
    }

    @Test
    void storesTheLocationAndFiltersByGovernorate() {
        create(CarJson.VALID);
        create(CarJson.car("peugeot", "peugeot-208", "p21", 2021).replace("\"SFAX\"", "\"TUNIS\""));

        assertThat(mvc.get().uri("/api/v1/cars?governorate=SFAX"))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.page.totalElements").isEqualTo(1);
                    assertThat(json).extractingPath("$.content[0].location.governorate").isEqualTo("SFAX");
                    assertThat(json).extractingPath("$.content[0].location.city").isEqualTo("Sakiet Ezzit");
                });
        assertThat(mvc.get().uri("/api/v1/cars?governorate=SFAX,TUNIS"))
                .bodyJson().extractingPath("$.page.totalElements").isEqualTo(2);
    }

    @Test
    void rejectsAListingWithoutGovernorate() {
        String withoutLocation = CarJson.VALID.replace(
                "\"location\": { \"governorate\": \"SFAX\", \"city\": \"Sakiet Ezzit\" },", "");

        assertThat(create(withoutLocation))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[*].field").asArray().containsExactly("location");
    }

    @Test
    void searchHidesSoldListingsUnlessAsked() {
        String sold = idOf(create(CarJson.VALID));
        changeStatus(sold, "SOLD");
        create(CarJson.car("peugeot", "peugeot-208", "p21", 2021));

        assertThat(mvc.get().uri("/api/v1/cars")).bodyJson().extractingPath("$.page.totalElements").isEqualTo(1);
        assertThat(mvc.get().uri("/api/v1/cars?status=SOLD")).bodyJson().extractingPath("$.content[0].id").isEqualTo(sold);
    }

    @Test
    void preventsLostUpdatesWithIfMatch() {
        String id = idOf(create(CarJson.VALID));
        String eTag = mvc.get().uri("/api/v1/cars/" + id).exchange().getResponse().getHeader("ETag");
        assertThat(eTag).isEqualTo("\"0\"");

        assertThat(mvc.patch().uri("/api/v1/cars/" + id + "/status").with(loggedInAs(SELLER_1)).header("If-Match", eTag)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"RESERVED\"}"))
                .hasStatusOk()
                .hasHeader("ETag", "\"1\"");
        assertThat(mvc.put().uri("/api/v1/cars/" + id).with(loggedInAs(SELLER_1)).header("If-Match", eTag)
                .contentType(MediaType.APPLICATION_JSON).content(CarJson.VALID))
                .hasStatus(HttpStatus.PRECONDITION_FAILED)
                .bodyJson().extractingPath("$.detail")
                .isEqualTo("Car " + id + " has changed since version 0 (it is now at version 1); reload it and try again");
        assertThat(mvc.get().uri("/api/v1/cars/" + id)).bodyJson().extractingPath("$.status").isEqualTo("RESERVED");
    }

    @Test
    void listsTheTwentyFourGovernorates() {
        assertThat(mvc.get().uri("/api/v1/governorates"))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.length()").isEqualTo(24);
                    assertThat(json).extractingPath("$[?(@.code == 'KEF')].name").asArray().containsExactly("Le Kef");
                });
    }

    @Test
    void listsEquipmentGroupedByCategory() {
        assertThat(mvc.get().uri("/api/v1/equipment"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.PASSIVE_SAFETY")
                .asArray().contains("FRONT_AIRBAGS", "ISOFIX");
    }

    private MvcTestResult create(String json) {
        return create(json, SELLER_1);
    }

    private MvcTestResult create(String json, CurrentUser user) {
        return mvc.post().uri("/api/v1/cars").with(loggedInAs(user))
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult changeStatus(String id, String status) {
        return changeStatus(id, status, SELLER_1);
    }

    private MvcTestResult changeStatus(String id, String status, CurrentUser user) {
        return mvc.patch().uri("/api/v1/cars/" + id + "/status").with(loggedInAs(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"%s\"}".formatted(status))
                .exchange();
    }

    private static String idOf(MvcTestResult result) {
        String location = result.getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }
}
