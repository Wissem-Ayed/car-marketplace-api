package com.carmarketplace.car.api;

import com.carmarketplace.car.application.CarNotFoundException;
import com.carmarketplace.car.application.CarService;
import com.carmarketplace.car.application.PhotoStorage;
import com.carmarketplace.car.domain.Car;
import com.carmarketplace.car.domain.CarAccessDeniedException;
import com.carmarketplace.car.domain.CarSearchCriteria;
import com.carmarketplace.car.domain.CarStatus;
import com.carmarketplace.car.domain.Equipment;
import com.carmarketplace.car.domain.FuelType;
import com.carmarketplace.car.domain.Governorate;
import com.carmarketplace.car.domain.IllegalCarStateException;
import com.carmarketplace.common.api.ProblemDetailsSecurityHandler;
import com.carmarketplace.common.domain.BusinessRuleViolationException;
import com.carmarketplace.common.domain.PreconditionFailedException;
import com.carmarketplace.config.SecurityConfig;
import com.carmarketplace.config.SearchConfig;
import com.carmarketplace.config.WebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.List;

import static com.carmarketplace.TestUsers.SELLER_1;
import static com.carmarketplace.TestUsers.loggedInAs;
import static com.carmarketplace.car.domain.CarBuilder.aCar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@WebMvcTest(CarController.class)
@Import({CarResponseMapper.class, SecurityConfig.class, ProblemDetailsSecurityHandler.class, WebConfig.class,
        SearchConfig.class})
class CarControllerTest {

    private final Car car = aCar().id("abc123").version(3).build();

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CarService carService;

    @MockitoBean
    private PhotoStorage photoStorage;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void createReturns201WithLocationHeader() {
        given(carService.createCar(any(), any())).willReturn(car);

        assertThat(post("/api/v1/cars", CarJson.VALID))
                .hasStatus(HttpStatus.CREATED)
                .hasHeader("Location", "http://localhost/api/v1/cars/abc123")
                .bodyJson().extractingPath("$.id").isEqualTo("abc123");
        then(carService).should().createCar(argThat(draft ->
                draft.vehicle().brandId().equals("mercedes-benz")
                        && draft.price().amount().toPlainString().equals("185000.000")
                        && draft.equipment().contains(Equipment.PANORAMIC_ROOF)), eq(SELLER_1));
    }

    @Test
    void createRequiresAnAccessToken() {
        MvcTestResult result = mvc.post().uri("/api/v1/cars")
                .contentType(MediaType.APPLICATION_JSON).content(CarJson.VALID).exchange();

        assertThat(result)
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson().extractingPath("$.title").isEqualTo("Authentication required");
        assertThat(result.getResponse().getHeader("WWW-Authenticate")).startsWith("Bearer");
        then(carService).shouldHaveNoInteractions();
    }

    @Test
    void browsingIsPublic() {
        given(carService.getCar("abc123")).willReturn(car);

        assertThat(mvc.get().uri("/api/v1/cars/abc123"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.seller.name").isEqualTo("Yasmine Ben Ali");
    }

    @Test
    void updateReturns403WhenTheUserIsNotTheSeller() {
        given(carService.updateCar(eq("abc123"), any(), any(), any())).willThrow(new CarAccessDeniedException("abc123"));

        assertThat(put("/api/v1/cars/abc123", CarJson.VALID))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.detail")
                .isEqualTo("Only the seller or an administrator can manage car abc123");
    }

    @Test
    void createReportsEveryInvalidFieldWith400() {
        String invalidCar = CarJson.VALID
                .replace("\"brandId\": \"mercedes-benz\"", "\"brandId\": \"\"")
                .replace("185000.000", "-1");

        assertThat(post("/api/v1/cars", invalidCar))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray().containsExactlyInAnyOrder("vehicle.brandId", "price.amount");
        then(carService).shouldHaveNoInteractions();
    }

    @Test
    void createRejectsUnknownEquipmentCodesWith400() {
        String invalidCar = CarJson.VALID.replace("\"ABS\"", "\"FLYING_MODE\"");

        assertThat(post("/api/v1/cars", invalidCar)).hasStatus(HttpStatus.BAD_REQUEST);
        then(carService).shouldHaveNoInteractions();
    }

    @Test
    void createRejectsBrokenDomainRulesWith422() {
        String electricWithCylinders = CarJson.VALID.replace("\"PLUG_IN_HYBRID\"", "\"ELECTRIC\"");

        assertThat(post("/api/v1/cars", electricWithCylinders))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.detail").isEqualTo("An electric car has no cylinders or engine displacement");
        then(carService).shouldHaveNoInteractions();
    }

    @Test
    void createReturns422WhenTheCatalogRejectsTheVehicle() {
        given(carService.createCar(any(), any()))
                .willThrow(new BusinessRuleViolationException("Model 'x' does not exist for brand Mercedes-Benz"));

        assertThat(post("/api/v1/cars", CarJson.VALID))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.title").isEqualTo("Business rule violated");
    }

    @Test
    void getReturnsTheCar() {
        given(carService.getCar("abc123")).willReturn(car);

        assertThat(mvc.get().uri("/api/v1/cars/abc123"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.vehicle.brand.name").isEqualTo("Mercedes-Benz");
    }

    @Test
    void getReturns404WhenTheCarDoesNotExist() {
        given(carService.getCar("unknown")).willThrow(new CarNotFoundException("unknown"));

        assertThat(mvc.get().uri("/api/v1/cars/unknown"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").isEqualTo("Car with id 'unknown' was not found");
    }

    @Test
    void listBindsEveryFilterAndTheDefaultPaging() {
        PageRequest defaultPage = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"));
        CarSearchCriteria expectedCriteria = new CarSearchCriteria(
                "bmw", null, null, null, null, 2018, null, 100000,
                FuelType.DIESEL, null, null, null, CarStatus.AVAILABLE, List.of(Equipment.ABS, Equipment.CAMERA_360), null,
                List.of(Governorate.TUNIS, Governorate.ARIANA));
        given(carService.getCars(any(), any())).willReturn(new PageImpl<>(List.of(car), defaultPage, 1));

        assertThat(mvc.get().uri("/api/v1/cars?brandId=bmw&minYear=2018&maxMileageKm=100000"
                        + "&fuelType=DIESEL&status=AVAILABLE&equipment=ABS&equipment=CAMERA_360&governorate=TUNIS,ARIANA"))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.page.totalElements").isEqualTo(1);
                    assertThat(json).extractingPath("$.page.totalElementsExact").isEqualTo(true);
                });
        then(carService).should().getCars(eq(expectedCriteria), eq(defaultPage));
    }

    @Test
    void updateReturns409WhenTheCarIsSold() {
        given(carService.updateCar(eq("abc123"), any(), any(), any()))
                .willThrow(new IllegalCarStateException("Car abc123 is sold and can no longer be edited"));

        assertThat(put("/api/v1/cars/abc123", CarJson.VALID))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void updateReturns409WhenSomeoneElseChangedTheCarMeanwhile() {
        given(carService.updateCar(eq("abc123"), any(), any(), any())).willThrow(new OptimisticLockingFailureException("stale"));

        assertThat(put("/api/v1/cars/abc123", CarJson.VALID))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.title").isEqualTo("Concurrent modification");
    }

    @Test
    void changeStatusReturnsTheUpdatedCar() {
        given(carService.changeStatus(eq("abc123"), eq(CarStatus.SOLD), any(), any()))
                .willReturn(aCar().id("abc123").version(4).status(CarStatus.SOLD).build());

        assertThat(mvc.patch().uri("/api/v1/cars/abc123/status").with(loggedInAs(SELLER_1))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"SOLD\"}"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("SOLD");
    }

    @Test
    void changeStatusRejectsUnknownStatusWith400() {
        assertThat(mvc.patch().uri("/api/v1/cars/abc123/status").with(loggedInAs(SELLER_1))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"STOLEN\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deleteReturns204() {
        assertThat(mvc.delete().uri("/api/v1/cars/abc123").with(loggedInAs(SELLER_1))).hasStatus(HttpStatus.NO_CONTENT);
        then(carService).should().deleteCar("abc123", SELLER_1, null);
    }

    @Test
    void deleteReturns404WhenTheCarDoesNotExist() {
        willThrow(new CarNotFoundException("unknown")).given(carService).deleteCar(eq("unknown"), any(), any());

        assertThat(mvc.delete().uri("/api/v1/cars/unknown").with(loggedInAs(SELLER_1))).hasStatus(HttpStatus.NOT_FOUND);
    }

    private MockMvcTester.MockMvcRequestBuilder post(String uri, String json) {
        return mvc.post().uri(uri).with(loggedInAs(SELLER_1)).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private MockMvcTester.MockMvcRequestBuilder put(String uri, String json) {
        return mvc.put().uri(uri).with(loggedInAs(SELLER_1)).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    @Test
    void searchShowsOnlyAvailableListingsByDefault() {
        given(carService.getCars(any(), any())).willReturn(new PageImpl<>(List.of(car), PageRequest.of(0, 20), 1));

        assertThat(mvc.get().uri("/api/v1/cars")).hasStatusOk();
        then(carService).should().getCars(argThat(criteria -> criteria.status() == CarStatus.AVAILABLE), any());
    }

    @Test
    void rejectsSortingOnFieldsThatAreNotIndexed() {
        assertThat(mvc.get().uri("/api/v1/cars?sort=description,asc"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail")
                .isEqualTo("Cannot sort by 'description'; sortable fields are id, price.amount, vehicle.year, history.mileageKm");
        then(carService).shouldHaveNoInteractions();
    }

    @Test
    void rejectsPagesBeyondTheLastServedOne() {
        assertThat(mvc.get().uri("/api/v1/cars?page=100"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail")
                .isEqualTo("Only the first 100 pages are available; narrow the search with filters");
    }

    @Test
    void getReturnsAnETagAndAnswers304WhenNothingChanged() {
        given(carService.getCar("abc123")).willReturn(car);

        assertThat(mvc.get().uri("/api/v1/cars/abc123")).hasStatusOk().hasHeader("ETag", "\"3\"");
        assertThat(mvc.get().uri("/api/v1/cars/abc123").header("If-None-Match", "\"3\""))
                .hasStatus(HttpStatus.NOT_MODIFIED);
    }

    @Test
    void passesTheIfMatchVersionToTheServiceAndReturnsTheNewETag() {
        given(carService.updateCar(eq("abc123"), any(), any(), eq(3L))).willReturn(aCar().id("abc123").version(4).build());

        assertThat(mvc.put().uri("/api/v1/cars/abc123").with(loggedInAs(SELLER_1)).header("If-Match", "\"3\"")
                .contentType(MediaType.APPLICATION_JSON).content(CarJson.VALID))
                .hasStatusOk()
                .hasHeader("ETag", "\"4\"");
    }

    @Test
    void returns412WhenTheListingChangedSinceTheIfMatchVersion() {
        given(carService.changeStatus(eq("abc123"), any(), any(), eq(2L)))
                .willThrow(new PreconditionFailedException("Car abc123 has changed since version 2"));

        assertThat(mvc.patch().uri("/api/v1/cars/abc123/status").with(loggedInAs(SELLER_1)).header("If-Match", "\"2\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"SOLD\"}"))
                .hasStatus(HttpStatus.PRECONDITION_FAILED)
                .bodyJson().extractingPath("$.title").isEqualTo("Precondition failed");
    }

    @Test
    void unexpectedErrorsUseTheProblemDetailsFormatWithoutLeakingInternals() {
        given(carService.getCar("abc123")).willThrow(new IllegalStateException("database password is hunter2"));

        assertThat(mvc.get().uri("/api/v1/cars/abc123"))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.title").isEqualTo("Internal error");
                    assertThat(json).extractingPath("$.detail").asString().doesNotContain("hunter2");
                });
    }
}
