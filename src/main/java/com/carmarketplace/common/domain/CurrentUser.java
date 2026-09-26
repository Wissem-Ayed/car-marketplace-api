package com.carmarketplace.common.domain;

import java.util.Objects;
import java.util.Set;

public record CurrentUser(String id, String name, Set<Role> roles) {

    public CurrentUser {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public boolean isAdmin() {
        return roles.contains(Role.ADMIN);
    }
}
