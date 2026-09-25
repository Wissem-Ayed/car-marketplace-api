package com.carmarketplace.car.api;

import com.carmarketplace.car.application.CarService;
import com.carmarketplace.car.domain.Car;
import com.carmarketplace.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/cars")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.CARS_TAG)
public class CarController {

    private static final String CAR_ID = "Id of the car listing";
    private static final String CAR_ID_EXAMPLE = "6ab5500b9fce1c0a50b91e83";

    private final CarService carService;

    @PostMapping
    @Operation(
            summary = "Publish a car listing",
            description = """
                    Brand, model and generation are validated against the catalog. `generationId` can be \
                    omitted when only one generation of the model was produced in the given year. \
                    A new listing is `AVAILABLE`.""")
    @ApiResponse(responseCode = "201", description = "Listing created",
            headers = @Header(name = "Location", description = "URL of the new listing"))
    @ApiResponse(responseCode = "400", description = "Malformed JSON or invalid fields, listed in `errors`")
    @ApiResponse(responseCode = "422",
            description = "Business rule violated: unknown catalog ids, year outside the generation, inconsistent engine…")
    public ResponseEntity<Car> createCar(@Valid @RequestBody CarRequest request) {
        Car created = carService.createCar(request.toDraft());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a car listing")
    @ApiResponse(responseCode = "200", description = "The listing")
    @ApiResponse(responseCode = "404", description = "No listing with this id")
    public Car getCar(@Parameter(description = CAR_ID, example = CAR_ID_EXAMPLE) @PathVariable String id) {
        return carService.getCar(id);
    }

    @GetMapping
    @Operation(
            summary = "Search car listings",
            description = """
                    All filters are optional and combined with AND. Results are paginated and sorted newest \
                    first unless `sort` is given. Sort on field paths such as `price.amount`, `vehicle.year` or \
                    `history.mileageKm`.""")
    @ApiResponse(responseCode = "200", description = "One page of listings")
    @ApiResponse(responseCode = "400", description = "Invalid filter value, e.g. an unknown enum code")
    public PagedModel<Car> getCars(
            @ParameterObject CarSearchParams params,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(carService.getCars(params.toCriteria(), pageable));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Replace a listing's details",
            description = "Replaces every detail of the listing. Status, id and creation date are kept.")
    @ApiResponse(responseCode = "200", description = "The updated listing")
    @ApiResponse(responseCode = "400", description = "Malformed JSON or invalid fields, listed in `errors`")
    @ApiResponse(responseCode = "404", description = "No listing with this id")
    @ApiResponse(responseCode = "409",
            description = "The listing is sold, or it was modified by another request in the meantime")
    @ApiResponse(responseCode = "422", description = "Business rule violated")
    public Car updateCar(
            @Parameter(description = CAR_ID, example = CAR_ID_EXAMPLE) @PathVariable String id,
            @Valid @RequestBody CarRequest request) {
        return carService.updateCar(id, request.toDraft());
    }

    @PatchMapping("/{id}/status")
    @Operation(
            summary = "Change a listing's status",
            description = "Allowed moves: `AVAILABLE ⇄ RESERVED`, `AVAILABLE → SOLD`, `RESERVED → SOLD`. `SOLD` is final.")
    @ApiResponse(responseCode = "200", description = "The listing with its new status")
    @ApiResponse(responseCode = "400", description = "Missing or unknown status")
    @ApiResponse(responseCode = "404", description = "No listing with this id")
    @ApiResponse(responseCode = "409", description = "The move is not allowed from the current status")
    public Car changeStatus(
            @Parameter(description = CAR_ID, example = CAR_ID_EXAMPLE) @PathVariable String id,
            @Valid @RequestBody StatusRequest request) {
        return carService.changeStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a car listing")
    @ApiResponse(responseCode = "204", description = "Listing deleted")
    @ApiResponse(responseCode = "404", description = "No listing with this id")
    public void deleteCar(@Parameter(description = CAR_ID, example = CAR_ID_EXAMPLE) @PathVariable String id) {
        carService.deleteCar(id);
    }
}
