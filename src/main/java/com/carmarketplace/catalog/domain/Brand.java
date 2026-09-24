package com.carmarketplace.catalog.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "brands")
public record Brand(@Id String id, String name) {
}
