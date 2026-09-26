package com.carmarketplace.common.api;

import com.carmarketplace.common.domain.PreconditionFailedException;

public final class ETags {

    private static final String ANY = "*";

    private ETags() {
    }

    public static String of(Long version) {
        return "\"" + version + "\"";
    }

    public static Long expectedVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank() || ifMatch.strip().equals(ANY)) {
            return null;
        }
        String value = ifMatch.strip();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        value = value.replace("\"", "");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new PreconditionFailedException("If-Match must contain an ETag returned by this API");
        }
    }
}
