package com.carmarketplace.car.api;

import com.carmarketplace.car.application.CarService;
import com.carmarketplace.common.api.PageResponse;
import com.carmarketplace.common.domain.CurrentUser;
import com.carmarketplace.config.OpenApiConfig;
import com.carmarketplace.config.SearchProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.ACCOUNT_TAG)
public class MyCarsController {

    private final CarService carService;
    private final CarResponseMapper mapper;
    private final SearchProperties searchProperties;

    @GetMapping("/api/v1/me/cars")
    @Operation(summary = "List my listings", description = "Every listing of the logged-in user, whatever its status.")
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME)
    @ApiResponse(responseCode = "200", description = "One page of the user's listings")
    @ApiResponse(responseCode = "400", description = "Unsupported sort field or page beyond the last available one")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    public PageResponse<CarResponse> getMyCars(
            CurrentUser user,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        ListingPaging.validate(pageable, searchProperties);
        return PageResponse.of(carService.getCarsOf(user, pageable), searchProperties.maxCountedResults(),
                mapper::toResponse);
    }
}
