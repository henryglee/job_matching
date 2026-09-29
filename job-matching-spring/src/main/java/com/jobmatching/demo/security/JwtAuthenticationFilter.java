package com.jobmatching.demo.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    // Absent only in web-layer test slices, which don't component-scan services; the app
    // always has it. Without it the token's own claims are trusted, as before.
    private final AccountStatusChecker accountStatusChecker;

    public JwtAuthenticationFilter(JwtService jwtService, ObjectProvider<AccountStatusChecker> accountStatusChecker) {
        this.jwtService = jwtService;
        this.accountStatusChecker = accountStatusChecker.getIfAvailable();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                authenticate(header.substring(BEARER_PREFIX.length()), request);
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        Claims claims = jwtService.parseClaims(token);
        UUID userId = UUID.fromString(claims.getSubject());
        String activeRole = claims.get("activeRole", String.class);
        String username = claims.get("username", String.class);
        String email = claims.get("email", String.class);
        String candidateProfileIdClaim = claims.get("candidateProfileId", String.class);
        UUID candidateProfileId = candidateProfileIdClaim != null ? UUID.fromString(candidateProfileIdClaim) : null;

        // The claims are a login-time snapshot: check them against the account as it is now.
        if (accountStatusChecker != null) {
            var status = accountStatusChecker.lookup(userId).orElse(null);
            if (status == null
                    || (activeRole != null && !status.roles().contains(activeRole))
                    || !status.passwordVersion().equals(claims.get(JwtService.PASSWORD_VERSION_CLAIM, String.class))) {
                // Deleted account, role since removed, or password changed (tokens from before
                // the change, or issued without the claim, are refused): leave unauthenticated (401).
                return;
            }
            candidateProfileId = status.candidateProfileId();
        }

        JwtUserPrincipal principal = new JwtUserPrincipal(userId, username, email, activeRole, candidateProfileId);
        List<GrantedAuthority> authorities = activeRole != null
                ? List.of(new SimpleGrantedAuthority("ROLE_" + activeRole))
                : List.of();

        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
