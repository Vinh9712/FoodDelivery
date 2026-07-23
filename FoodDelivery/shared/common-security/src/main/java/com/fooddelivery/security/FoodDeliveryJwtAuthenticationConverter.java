package com.fooddelivery.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class FoodDeliveryJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> scopeAuthorities = scopeConverter.convert(jwt);
        Set<GrantedAuthority> authorities = new LinkedHashSet<>(
                scopeAuthorities == null ? List.of() : scopeAuthorities);
        roles(jwt).forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + normalizeRole(role))));
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private Collection<String> roles(Jwt jwt) {
        Object roles = jwt.getClaims().get("roles");
        if (roles instanceof Collection<?> collection) {
            return collection.stream().map(Object::toString).toList();
        }
        String role = jwt.getClaimAsString("role");
        return role == null || role.isBlank() ? List.of() : List.of(role);
    }

    private String normalizeRole(String role) {
        String normalized = role.trim().toUpperCase();
        return normalized.startsWith("ROLE_") ? normalized.substring(5) : normalized;
    }
}
