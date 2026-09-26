package com.carmarketplace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.search")
public record SearchProperties(
        @DefaultValue("10000") long maxCountedResults,
        @DefaultValue("100") int maxPages) {
}
