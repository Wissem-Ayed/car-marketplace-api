package com.carmarketplace.car;

import com.carmarketplace.TestcontainersConfiguration;
import com.carmarketplace.car.api.CarJson;
import com.carmarketplace.car.application.TestImages;
import com.carmarketplace.common.domain.CurrentUser;
import com.carmarketplace.config.StorageProperties;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import software.amazon.awssdk.services.s3.S3Client;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static com.carmarketplace.TestUsers.SELLER_1;
import static com.carmarketplace.TestUsers.SELLER_2;
import static com.carmarketplace.TestUsers.loggedInAs;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PhotoApiIntegrationTest {

    private final HttpClient browser = HttpClient.newHttpClient();

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private StorageProperties storageProperties;

    private String carId;

    @BeforeEach
    void setUp() {
        mongoTemplate.getCollection("cars").deleteMany(new Document());
        MvcTestResult created = mvc.post().uri("/api/v1/cars").with(loggedInAs(SELLER_1))
                .contentType(MediaType.APPLICATION_JSON).content(CarJson.VALID).exchange();
        String location = created.getResponse().getHeader("Location");
        carId = location.substring(location.lastIndexOf('/') + 1);
    }

    @Test
    void uploadsPhotosAndServesEverySizePublicly() throws Exception {
        MvcTestResult result = upload(photo("front.jpg", TestImages.jpeg(2400, 1600)), photo("interior.jpg", TestImages.jpeg(800, 600)));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        String body = body(result);
        List<String> photoIds = JsonPath.read(body, "$.photos[*].id");
        assertThat(photoIds).hasSize(2);
        assertThat(storedFiles()).hasSize(6);

        HttpResponse<byte[]> thumbnail = download(JsonPath.read(body, "$.photos[0].urls.thumbnail"));
        assertThat(thumbnail.statusCode()).isEqualTo(200);
        assertThat(thumbnail.headers().firstValue("Content-Type")).hasValue("image/jpeg");
        assertThat(TestImages.read(thumbnail.body()).getWidth()).isEqualTo(400);

        BufferedImage large = TestImages.read(download(JsonPath.read(body, "$.photos[0].urls.large")).body());
        assertThat(large.getWidth()).isEqualTo(1920);
        assertThat(mvc.get().uri("/api/v1/cars/" + carId)).bodyJson()
                .extractingPath("$.photos[*].id").asArray().containsExactlyElementsOf(photoIds);
    }

    @Test
    void storesNothingWhenOneFileOfTheBatchIsInvalid() {
        MvcTestResult result = upload(
                photo("front.jpg", TestImages.jpeg(800, 600)),
                photo("virus.jpg", "not an image".getBytes(StandardCharsets.UTF_8)));

        assertThat(result)
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.detail")
                .isEqualTo("Photo 'virus.jpg' is not a supported image; use JPEG, PNG or WebP");
        assertThat(storedFiles()).isEmpty();
        assertThat(mvc.get().uri("/api/v1/cars/" + carId)).bodyJson().extractingPath("$.photos").asArray().isEmpty();
    }

    @Test
    void acceptsTenPhotosAndRejectsTheEleventh() {
        MockMultipartFile[] ten = new MockMultipartFile[10];
        Arrays.setAll(ten, i -> photo("photo-" + i + ".jpg", TestImages.jpeg(200, 150)));

        assertThat(upload(ten)).hasStatus(HttpStatus.CREATED);
        assertThat(upload(photo("one-more.jpg", TestImages.jpeg(200, 150))))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.detail").isEqualTo("A car can have at most 10 photos; this one already has 10");
        assertThat(storedFiles()).hasSize(30);
    }

    @Test
    void reordersPhotosSoTheChosenOneBecomesTheCover() {
        List<String> ids = JsonPath.read(body(upload(
                photo("a.jpg", TestImages.jpeg(200, 150)), photo("b.jpg", TestImages.jpeg(200, 150)))), "$.photos[*].id");

        assertThat(mvc.put().uri("/api/v1/cars/" + carId + "/photos/order").with(loggedInAs(SELLER_1)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"photoIds\": [\"%s\", \"%s\"]}".formatted(ids.get(1), ids.get(0))))
                .hasStatusOk()
                .bodyJson().extractingPath("$.photos[0].id").isEqualTo(ids.get(1));
    }

    @Test
    void deletingAPhotoRemovesItsFiles() {
        List<String> ids = JsonPath.read(body(upload(
                photo("a.jpg", TestImages.jpeg(200, 150)), photo("b.jpg", TestImages.jpeg(200, 150)))), "$.photos[*].id");

        assertThat(mvc.delete().uri("/api/v1/cars/" + carId + "/photos/" + ids.get(0)).with(loggedInAs(SELLER_1))).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(storedFiles()).hasSize(3).noneMatch(key -> key.contains(ids.get(0)));
        assertThat(mvc.delete().uri("/api/v1/cars/" + carId + "/photos/" + ids.get(0)).with(loggedInAs(SELLER_1))).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void deletingACarRemovesAllItsPhotoFiles() {
        upload(photo("a.jpg", TestImages.jpeg(200, 150)), photo("b.jpg", TestImages.jpeg(200, 150)));

        assertThat(mvc.delete().uri("/api/v1/cars/" + carId).with(loggedInAs(SELLER_1))).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void soldCarPhotosCannotChange() {
        mvc.patch().uri("/api/v1/cars/" + carId + "/status").with(loggedInAs(SELLER_1))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"SOLD\"}").exchange();

        assertThat(upload(photo("late.jpg", TestImages.jpeg(200, 150)))).hasStatus(HttpStatus.CONFLICT);
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void anotherUserCannotAddPhotosToTheListing() {
        assertThat(upload(SELLER_2, photo("not-mine.jpg", TestImages.jpeg(200, 150)))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void anonymousVisitorsCanReadButNotWritePhotos() throws Exception {
        String thumbnailUrl = JsonPath.read(body(upload(photo("a.jpg", TestImages.jpeg(200, 150)))), "$.photos[0].urls.thumbnail");

        HttpResponse<Void> overwrite = browser.send(
                HttpRequest.newBuilder(URI.create(thumbnailUrl)).PUT(HttpRequest.BodyPublishers.ofString("hacked")).build(),
                HttpResponse.BodyHandlers.discarding());
        HttpResponse<Void> delete = browser.send(
                HttpRequest.newBuilder(URI.create(thumbnailUrl)).DELETE().build(), HttpResponse.BodyHandlers.discarding());

        assertThat(overwrite.statusCode()).isEqualTo(403);
        assertThat(delete.statusCode()).isEqualTo(403);
        assertThat(download(thumbnailUrl).statusCode()).isEqualTo(200);
    }

    private MvcTestResult upload(MockMultipartFile... files) {
        return upload(SELLER_1, files);
    }

    private MvcTestResult upload(CurrentUser user, MockMultipartFile... files) {
        var request = mvc.post().uri("/api/v1/cars/" + carId + "/photos").with(loggedInAs(user)).multipart();
        for (MockMultipartFile file : files) {
            request = request.file(file);
        }
        return request.exchange();
    }

    private static MockMultipartFile photo(String name, byte[] content) {
        return new MockMultipartFile("files", name, "application/octet-stream", content);
    }

    private List<String> storedFiles() {
        return s3Client.listObjectsV2(request -> request.bucket(storageProperties.bucket()).prefix("cars/" + carId + "/"))
                .contents().stream().map(object -> object.key()).toList();
    }

    private HttpResponse<byte[]> download(String url) throws IOException, InterruptedException {
        return browser.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static String body(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
