package com.carmarketplace.common.api;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(List<T> content, Metadata page) {

    public static <S, T> PageResponse<T> of(Page<S> page, long countLimit, Function<S, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                new Metadata(page.getSize(), page.getNumber(), page.getTotalElements(), page.getTotalPages(),
                        page.getTotalElements() < countLimit));
    }

    @Schema(name = "PageMetadata")
    public record Metadata(
            @Schema(description = "Requested page size", example = "20") int size,
            @Schema(description = "Zero-based page index", example = "0") int number,
            @Schema(description = "Number of matching elements, counted up to a limit", example = "137") long totalElements,
            @Schema(description = "Number of pages for totalElements", example = "7") int totalPages,
            @Schema(description = "False when there are more results than counted: show \"10,000+\"", example = "true")
            boolean totalElementsExact) {
    }
}
