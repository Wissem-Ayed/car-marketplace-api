package com.carmarketplace.car.api;

import com.carmarketplace.car.application.CarService;
import com.carmarketplace.car.domain.Car;
import com.carmarketplace.common.api.ETags;
import com.carmarketplace.common.api.PageResponse;
import com.carmarketplace.common.domain.CurrentUser;
import com.carmarketplace.config.OpenApiConfig;
import com.carmarketplace.config.SearchProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/cars")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.CARS_TAG)
public class CarController {

    private static final String CAR_ID = "Id of the car listing";
    private static final String CAR_ID_EXAMPLE = "6ab5500b9fce1c0a50b91e83";
    private static final String IF_MATCH = "ETag of the version you edited (from a previous response). "
            + "If the listing changed since, the request fails with 412 instead of overwriting the other change.";

    private final CarService carService;
    private final CarResponseMapper mapper;
    private final SearchProperties searchProperties;

    @PostMapping
    @Operation(
            summary = "Publish a car listing",
            description = """
                    Brand, model and generation are validated against the catalog. `generationId` can be \
                    omitted when only one generation of the model was produced in the given year. \
                    The logged-in user becomes the seller. A new listing is `AVAILABLE`.""")
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME)
    @ApiResponse(responseCode = "201", description = "Listing created",
            headers = {
                    @Header(name = "Location", description = "URL of the new listing"),
                    @Header(name = "ETag", description = "Version of the listing, for conditional requests")})
    @ApiResponse(responseCode = "400", description = "Malformed JSON or invalid fields, listed in `errors`")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "422",
            description = "Business rule violated: unknown catalog ids, year outside the generation, inconsistent engine…")
    public ResponseEntity<CarResponse> createCar(@Valid @RequestBody CarRequest request, CurrentUser user) {
        Car created = carService.createCar(request.toDraft(), user);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).eTag(ETags.of(created.version())).body(mapper.toResponse(created));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get a car listing",
            description = "Returns an `ETag`. Send it back in `If-None-Match` to get `304 Not Modified` "
                    + "when the listing hasn't changed.")
    @ApiResponse(responseCode = "200", description = "The listing",
            headers = @Header(name = "ETag", description = "Version of the listing"))
    @ApiResponse(responseCode = "304", description = "The listing hasn't changed since the ETag sent in If-None-Match")
    @ApiResponse(responseCode = "404", description = "No listing with this id")
    public ResponseEntity<CarResponse> getCar(
            @Parameter(description = CAR_ID, example = CAR_ID_EXAMPLE) @PathVariable String id,
            WebRequest request) {
        Car car = carService.getCar(id);
        String eTag = ETags.of(car.version());
        if (request.checkNotModified(eTag)) {
            return null;
        }
        return ResponseEntity.ok().eTag(eTag).body(mapper.toResponse(car));
    }

    @GetMapping
    @Operation(
            summary = "Search car listings",
            description = """
                    All filters are optional and combined with AND; only `AVAILABLE` listings are returned unless \
                    `status` is given. Results are paginated, newest first by default. Sortable fields: `id`, \
                    `price.amount`, `vehicle.year`, `history.mileageKm`. Only the first 100 pages are served, and \
                    `totalElements` is counted up to 10,000 (`totalElementsExact` tells whether it is exact).""")
    @ApiResponse(responseCode = "200", description = "One page of listings")
    @ApiResponse(responseCode = "400",
            description = "Invalid filter value, unsupported sort field, or page beyond the last available one")
    public PageResponse<CarResponse> getCars(
            @ParameterObject CarSearchParams params,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        ListingPaging.validate(pageable, searchProperties);
        return PageResponse.of(carService.getCars(params.toCriteria(), pageable),
                searchProperties.maxCountedResults(), mapper::toResponse);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Replace a listing's details",
            description = "Replaces every detail of the listing. Status, id and creation date are kept.")
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME)
    @ApiResponse(responseCode = "200", description = "The updated listing",
            headers = @Header(name = "ETag", description = "New version of the listing"))
    @ApiResponse(responseCode = "400", description = "Malformed JSON or invalid fields, listed in `errors`")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "Only the seller or an administrator can do this")
    @ApiResponse(responseCode = "404", description = "No listing with this id")
    @ApiResponse(responseCode = "409",
            description = "The listing is sold, or it was modified by another request in the meantime")
    @ApiResponse(responseCode = "412", description = "The listing changed since the ETag sent in If-Match")
    @ApiResponse(responseCode = "422", description = "Business rule violated")
    public ResponseEntity<CarResponse> updateCar(
            @Parameter(description = CAR_ID, example = CAR_ID_EXAMPLE) @PathVariable String id,
            @Parameter(description = IF_MATCH, example = "\"3\"") @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody CarRequest request,
            CurrentUser user) {
        return withETag(carService.updateCar(id, request.toDraft(), user, ETags.expectedVersion(ifMatch)));
    }

    @PatchMapping("/{id}/status")
    @Operation(
            summary = "Change a listing's status",
            description = "Allowed moves: `AVAILABLE ⇄ RESERVED`, `AVAILABLE → SOLD`, `RESERVED → SOLD`. `SOLD` is final.")
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME)
    @ApiResponse(responseCode = "200", description = "The listing with its new status",
            headers = @Header(name = "ETag", description = "New version of the listing"))
    @ApiResponse(responseCode = "400", description = "Missing or unknown status")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "Only the seller or an administrator can do this")
    @ApiResponse(responseCode = "404", description = "No listing with this id")
    @ApiResponse(responseCode = "409", description = "The move is not allowed from the current status")
    @ApiResponse(responseCode = "412", description = "The listing changed since the ETag sent in If-Match")
    public ResponseEntity<CarResponse> changeStatus(
            @Parameter(description = CAR_ID, example = CAR_ID_EXAMPLE) @PathVariable String id,
            @Parameter(description = IF_MATCH, example = "\"3\"") @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody StatusRequest request,
            CurrentUser user) {
        return withETag(carService.changeStatus(id, request.status(), user, ETags.expectedVersion(ifMatch)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a car listing")
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME)
    @ApiResponse(responseCode = "204", description = "Listing deleted")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "Only the seller or an administrator can do this")
    @ApiResponse(responseCode = "404", description = "No listing with this id")
    @ApiResponse(responseCode = "412", description = "The listing changed since the ETag sent in If-Match")
    public ResponseEntity<Void> deleteCar(
            @Parameter(description = CAR_ID, example = CAR_ID_EXAMPLE) @PathVariable String id,
            @Parameter(description = IF_MATCH, example = "\"3\"") @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            CurrentUser user) {
        carService.deleteCar(id, user, ETags.expectedVersion(ifMatch));
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<CarResponse> withETag(Car car) {
        return ResponseEntity.ok().eTag(ETags.of(car.version())).body(mapper.toResponse(car));
    }
}
