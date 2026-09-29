package com.jobmatching.demo.security;

import java.util.UUID;

/**
 * The authenticated principal for a request, built entirely from JWT claims (stateless —
 * no DB lookup per request). {@code activeRole} mirrors app.py's `current_user.active_role`:
 * a user can hold multiple roles but only the one role.name chosen at login is authoritative
 * for that token, even if the account has others.
 */
public record JwtUserPrincipal(
        UUID userId,
        String username,
        String email,
        String activeRole,
        UUID candidateProfileId
) {
}
