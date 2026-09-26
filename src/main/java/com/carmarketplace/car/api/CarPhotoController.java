package com.carmarketplace.car.api;

import com.carmarketplace.car.application.PhotoService;
import com.carmarketplace.car.application.PhotoUpload;
import com.carmarketplace.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/cars/{carId}/photos")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.CARS_TAG)
public class CarPhotoController {

    private static final String CAR_ID = "Id of the car listing";
    private static final String CAR_ID_EXAMPLE = "6ab5500b9fce1c0a50b91e83";

    private final PhotoService photoService;
    private final CarResponseMapper mapper;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Upload photos",
            description = """
                    Adds one or more photos, appended after the existing ones. Accepts JPEG, PNG and WebP, \
                    at most 10 MB and 40 megapixels each, and at most 10 photos per car. Each photo is \
                    re-encoded as JPEG in three sizes, and its metadata (including GPS location) is removed.""")
    @ApiResponse(responseCode = "201", description = "Photos added; returns the listing with every photo")
    @ApiResponse(responseCode = "400", description = "No file sent")
    @ApiResponse(responseCode = "404", description = "No listing with this id")
    @ApiResponse(responseCode = "409", description = "The listing is sold, or was modified by another request")
    @ApiResponse(responseCode = "413", description = "A file is larger than 10 MB")
    @ApiResponse(responseCode = "422",
            description = "Unsupported or unreadable image, too many pixels, or more than 10 photos in total")
    public CarResponse uploadPhotos(
            @Parameter(description = CAR_ID, example = CAR_ID_EXAMPLE) @PathVariable String carId,
            @Parameter(description = "Image files") @RequestPart("files") List<MultipartFile> files) {
        return mapper.toResponse(photoService.addPhotos(carId, files.stream().map(CarPhotoController::toUpload).toList()));
    }

    @PutMapping("/order")
    @Operation(summary = "Reorder photos", description = "The first photo of the new order becomes the cover.")
    @ApiResponse(responseCode = "200", description = "The listing with its photos in the new order")
    @ApiResponse(responseCode = "400", description = "Missing or empty list")
    @ApiResponse(responseCode = "404", description = "No listing with this id")
    @ApiResponse(responseCode = "409", description = "The listing is sold, or was modified by another request")
    @ApiResponse(responseCode = "422", description = "The list doesn't contain each photo of the car exactly once")
    public CarResponse reorderPhotos(
            @Parameter(description = CAR_ID, example = CAR_ID_EXAMPLE) @PathVariable String carId,
            @Valid @RequestBody PhotoOrderRequest request) {
        return mapper.toResponse(photoService.reorderPhotos(carId, request.photoIds()));
    }

    @DeleteMapping("/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a photo", description = "Removes the photo from the listing and deletes its files.")
    @ApiResponse(responseCode = "204", description = "Photo deleted")
    @ApiResponse(responseCode = "404", description = "No listing with this id, or no such photo on it")
    @ApiResponse(responseCode = "409", description = "The listing is sold, or was modified by another request")
    public void deletePhoto(
            @Parameter(description = CAR_ID, example = CAR_ID_EXAMPLE) @PathVariable String carId,
            @Parameter(description = "Id of the photo") @PathVariable String photoId) {
        photoService.removePhoto(carId, photoId);
    }

    private static PhotoUpload toUpload(MultipartFile file) {
        try {
            return new PhotoUpload(file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
