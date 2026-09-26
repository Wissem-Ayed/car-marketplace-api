package com.carmarketplace;

import com.carmarketplace.car.api.CarJson;
import com.jayway.jsonpath.JsonPath;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, KeycloakContainerConfiguration.class})
class KeycloakAuthenticationIntegrationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuer;

    @Autowired
    @Qualifier("mailpitContainer")
    private GenericContainer<?> mailpit;

    @BeforeEach
    void setUp() {
        mongoTemplate.getCollection("cars").deleteMany(new Document());
    }

    @Test
    void aKeycloakUserPublishesUnderTheirOwnIdentity() throws Exception {
        MvcTestResult created = createCar(tokenFor("seller1"));

        assertThat(created)
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.seller.id").isEqualTo(TestUsers.SELLER_1.id());
                    assertThat(json).extractingPath("$.seller.name").isEqualTo("Yasmine Ben Ali");
                });
    }

    @Test
    void keycloakRolesAreRecognised() throws Exception {
        assertThat(mvc.get().uri("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(tokenFor("admin"))))
                .hasStatusOk()
                .bodyJson().extractingPath("$.roles").asArray().containsExactlyInAnyOrder("USER", "ADMIN");
    }

    @Test
    void anotherSellerCannotChangeTheListingButTheAdministratorCan() throws Exception {
        String carId = idOf(createCar(tokenFor("seller1")));

        assertThat(changeStatus(carId, tokenFor("seller2"))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(changeStatus(carId, tokenFor("admin"))).hasStatusOk();
    }

    @Test
    void rejectsAForgedToken() throws Exception {
        String[] parts = tokenFor("seller2").split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace(TestUsers.SELLER_2.id(), TestUsers.SELLER_1.id());
        String forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        MvcTestResult result = createCar(forged);

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .startsWith("Bearer")
                .contains("error=\"invalid_token\"")
                .contains("Invalid signature");
    }

    @Test
    void rejectsRequestsWithoutAToken() {
        assertThat(mvc.post().uri("/api/v1/cars").contentType(MediaType.APPLICATION_JSON).content(CarJson.VALID))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson().extractingPath("$.title").isEqualTo("Authentication required");
    }

    @Test
    void keycloakRejectsAWrongPassword() throws Exception {
        HttpResponse<String> response = requestToken("seller1", "wrong-password");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.error")).isEqualTo("invalid_grant");
    }

    @Test
    void sendsPasswordResetEmailsThroughTheMailServer() throws Exception {
        String masterToken = JsonPath.read(send(HttpRequest.newBuilder(URI.create(
                        issuer.replace("/realms/car-marketplace", "/realms/master/protocol/openid-connect/token")))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=password&client_id=admin-cli&username=%s&password=%s"
                        .formatted(KeycloakContainerConfiguration.ADMIN_USERNAME, KeycloakContainerConfiguration.ADMIN_PASSWORD)))
                .build()).body(), "$.access_token");

        HttpResponse<String> resetRequest = send(HttpRequest.newBuilder(URI.create(
                        issuer.replace("/realms/", "/admin/realms/") + "/users/" + TestUsers.SELLER_1.id()
                                + "/execute-actions-email"))
                .header(HttpHeaders.AUTHORIZATION, bearer(masterToken))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("[\"UPDATE_PASSWORD\"]"))
                .build());

        assertThat(resetRequest.statusCode()).isEqualTo(204);
        String inbox = "http://%s:%d/api/v1/search?query=to:seller1@car-marketplace.test".formatted(
                mailpit.getHost(), mailpit.getMappedPort(KeycloakContainerConfiguration.MAILPIT_WEB_PORT));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            String messages = send(HttpRequest.newBuilder(URI.create(inbox)).GET().build()).body();
            assertThat(JsonPath.<Integer>read(messages, "$.messages_count")).isEqualTo(1);
            assertThat(JsonPath.<String>read(messages, "$.messages[0].Subject")).isEqualTo("Update Your Account");
        });
    }

    private MvcTestResult createCar(String accessToken) {
        return mvc.post().uri("/api/v1/cars")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(CarJson.VALID)
                .exchange();
    }

    private MvcTestResult changeStatus(String carId, String accessToken) {
        return mvc.patch().uri("/api/v1/cars/" + carId + "/status")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"RESERVED\"}")
                .exchange();
    }

    private String tokenFor(String username) throws IOException, InterruptedException {
        HttpResponse<String> response = requestToken(username, username + "-password");
        assertThat(response.statusCode()).isEqualTo(200);
        return JsonPath.read(response.body(), "$.access_token");
    }

    private HttpResponse<String> requestToken(String username, String password) throws IOException, InterruptedException {
        String form = "grant_type=password&client_id=car-marketplace-cli&username=%s&password=%s"
                .formatted(encode(username), encode(password));
        return http.send(HttpRequest.newBuilder(URI.create(issuer + "/protocol/openid-connect/token"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String idOf(MvcTestResult result) {
        String location = result.getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }
}
