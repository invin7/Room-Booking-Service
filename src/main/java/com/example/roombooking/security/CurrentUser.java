package com.example.roombooking.security;

import org.springframework.security.oauth2.jwt.Jwt;

/** The caller, as asserted by the verified JWT. */
public record CurrentUser(Long id, boolean admin) {
    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(Long.valueOf(jwt.getSubject()), "ADMIN".equals(jwt.getClaimAsString("role")));
    }
}
