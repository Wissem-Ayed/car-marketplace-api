package com.carmarketplace.car.api;

import com.carmarketplace.common.api.InvalidRequestException;
import com.carmarketplace.config.SearchProperties;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Set;

final class ListingPaging {

    static final List<String> SORTABLE_FIELDS = List.of("id", "price.amount", "vehicle.year", "history.mileageKm");
    private static final Set<String> SORTABLE = Set.copyOf(SORTABLE_FIELDS);

    private ListingPaging() {
    }

    static void validate(Pageable pageable, SearchProperties properties) {
        if (pageable.getPageNumber() >= properties.maxPages()) {
            throw new InvalidRequestException("Only the first %d pages are available; narrow the search with filters"
                    .formatted(properties.maxPages()));
        }
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new InvalidRequestException("Cannot sort by '%s'; sortable fields are %s"
                        .formatted(order.getProperty(), String.join(", ", SORTABLE_FIELDS)));
            }
        }
    }
}
