package com.carmarketplace.common.api;

import com.carmarketplace.common.domain.CurrentUser;
import com.carmarketplace.common.domain.Role;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(CurrentUser.class);
    }

    @Override
    public CurrentUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                       NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new AuthenticationCredentialsNotFoundException("An access token is required");
        }
        Jwt jwt = token.getToken();
        return new CurrentUser(jwt.getSubject(), displayName(jwt), roles(token));
    }

    private static String displayName(Jwt jwt) {
        return Stream.of(jwt.getClaimAsString("name"), jwt.getClaimAsString("preferred_username"), jwt.getSubject())
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseThrow();
    }

    private static Set<Role> roles(JwtAuthenticationToken token) {
        Set<String> granted = token.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .collect(Collectors.toSet());
        return Arrays.stream(Role.values())
                .filter(role -> granted.contains(role.name()))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Role.class)));
    }
}
