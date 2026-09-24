package com.carmarketplace.car.api;

import com.carmarketplace.car.application.CarNotFoundException;
import com.carmarketplace.car.application.CarService;
import com.carmarketplace.car.domain.Car;
import com.carmarketplace.car.domain.CarSearchCriteria;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@WebMvcTest(CarController.class)
class CarControllerTest {

    private static final String VALID_CAR_JSON = """
            {"brand": "BMW", "model": "X3", "year": 2021, "mileageKm": 42000, "price": 25990.00}
            """;

    private final Car bmw = new Car("abc123", "BMW", "X3", 2021, 42000, new BigDecimal("25990.00"));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CarService carService;

    @Test
    void createReturns201WithLocationHeader() {
        given(carService.saveCar(any())).willReturn(bmw);

        assertThat(mvc.post().uri("/api/v1/cars").contentType(MediaType.APPLICATION_JSON).content(VALID_CAR_JSON))
                .hasStatus(HttpStatus.CREATED)
                .hasHeader("Location", "http://localhost/api/v1/cars/abc123")
                .bodyJson().extractingPath("$.id").isEqualTo("abc123");
    }

    @Test
    void createRejectsInvalidCarWith400() {
        String invalidCar = """
                {"brand": "", "model": "X3", "year": 2021, "mileageKm": -5, "price": 25990.00}
                """;

        assertThat(mvc.post().uri("/api/v1/cars").contentType(MediaType.APPLICATION_JSON).content(invalidCar))
                .hasStatus(HttpStatus.BAD_REQUEST);
        then(carService).shouldHaveNoInteractions();
    }

    @Test
    void getReturnsCar() {
        given(carService.getCar("abc123")).willReturn(bmw);

        assertThat(mvc.get().uri("/api/v1/cars/abc123"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.brand").isEqualTo("BMW");
    }

    @Test
    void getReturns404ProblemDetailWhenCarDoesNotExist() {
        given(carService.getCar("unknown")).willThrow(new CarNotFoundException("unknown"));

        assertThat(mvc.get().uri("/api/v1/cars/unknown"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").isEqualTo("Car with id 'unknown' was not found");
    }

    @Test
    void listBindsFiltersAndDefaultPaging() {
        PageRequest defaultPage = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"));
        CarSearchCriteria expectedCriteria = new CarSearchCriteria("bmw", new BigDecimal("10000"), null, 2018, null);
        given(carService.getCars(any(), any())).willReturn(new PageImpl<>(List.of(bmw), defaultPage, 1));

        assertThat(mvc.get().uri("/api/v1/cars?brand=bmw&minPrice=10000&minYear=2018"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.page.totalElements").isEqualTo(1);
        then(carService).should().getCars(eq(expectedCriteria), eq(defaultPage));
    }

    @Test
    void updateReturns404WhenCarDoesNotExist() {
        given(carService.updateCar(eq("unknown"), any())).willThrow(new CarNotFoundException("unknown"));

        assertThat(mvc.put().uri("/api/v1/cars/unknown").contentType(MediaType.APPLICATION_JSON).content(VALID_CAR_JSON))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteReturns204() {
        assertThat(mvc.delete().uri("/api/v1/cars/abc123"))
                .hasStatus(HttpStatus.NO_CONTENT);
        then(carService).should().deleteCar("abc123");
    }

    @Test
    void deleteReturns404WhenCarDoesNotExist() {
        willThrow(new CarNotFoundException("unknown")).given(carService).deleteCar("unknown");

        assertThat(mvc.delete().uri("/api/v1/cars/unknown"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }
}
