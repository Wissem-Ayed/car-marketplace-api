package com.carmarketplace.catalog.domain;

import java.time.Year;

public record Generation(String id, String name, int fromYear, Integer toYear) {

    public boolean covers(int year) {
        int lastYear = toYear != null ? toYear : Year.now().getValue() + 1;
        return year >= fromYear && year <= lastYear;
    }

    public String productionYears() {
        return fromYear + "–" + (toYear != null ? toYear : "present");
    }
}
