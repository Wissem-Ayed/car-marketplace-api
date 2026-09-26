package com.carmarketplace;

import com.carmarketplace.common.domain.CurrentUser;
import com.carmarketplace.common.domain.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

public final class TestUsers {

    public static final CurrentUser SELLER_1 =
            new CurrentUser("5f0c1a2e-0000-4000-8000-000000000001", "Yasmine Ben Ali", Set.of(Role.USER));
    public static final CurrentUser SELLER_2 =
            new CurrentUser("5f0c1a2e-0000-4000-8000-000000000002", "Karim Haddad", Set.of(Role.USER));
    public static final CurrentUser ADMIN =
            new CurrentUser("5f0c1a2e-0000-4000-8000-000000000003", "Marketplace Moderator", Set.of(Role.USER, Role.ADMIN));

    private TestUsers() {
    }

    public static RequestPostProcessor loggedInAs(CurrentUser user) {
        return jwt()
                .jwt(token -> token.subject(user.id()).claim("name", user.name()))
                .authorities(user.roles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toArray(GrantedAuthority[]::new));
    }
}
