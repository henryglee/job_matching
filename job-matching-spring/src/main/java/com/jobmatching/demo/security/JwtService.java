package com.jobmatching.demo.security;

import com.jobmatching.demo.entity.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;

/**
 * Issues/parses the JWT that replaces the Flask session cookie. Payload shape follows
 * API_DOCUMENTATION.md's Authentication Model note: userId (subject), activeRole,
 * candidateProfileId (nullable), exp.
 */
@Component
public class JwtService {

    public static final String PASSWORD_VERSION_CLAIM = "pwv";

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${jwt.secret}") String secret,
                       @Value("${jwt.expiration-ms:86400000}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(UserAccount user, String activeRole) {
        Instant now = Instant.now();
        JwtBuilder builder = Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("username", user.getUsername())
                .claim("activeRole", activeRole)
                .claim(PASSWORD_VERSION_CLAIM, passwordVersion(user.getPasswordHash()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key);
        if (user.getCandidateProfile() != null) {
            builder.claim("candidateProfileId", user.getCandidateProfile().getId().toString());
        }
        return builder.compact();
    }

    /**
     * A short fingerprint of the stored password hash. Carried in the token so it stops working
     * once the password changes (the hash, salt included, is new on every reset), with no
     * schema change. Not reversible to the hash in any useful way.
     */
    public static String passwordVersion(String passwordHash) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((passwordHash == null ? "" : passwordHash).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(digest, 12));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
