package com.jobmatching.demo.security;

import org.bouncycastle.crypto.generators.SCrypt;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Reimplements Werkzeug's generate_password_hash/check_password_hash byte-for-byte
 * (format "{method}${salt}${hex-digest}"), so password hashes created by the existing
 * Flask app keep working after the Spring Boot cutover with no forced reset, and hashes
 * this encoder creates stay verifiable by the Flask app during a phased migration.
 *
 * The API_DOCUMENTATION.md migration note ("hash with bcrypt") is wrong — app.py calls
 * generate_password_hash(password) with no method argument, so it uses whichever
 * algorithm is Werkzeug's installed-version default. As of Werkzeug 2.3+ (confirmed
 * against the 3.1.8 installed for JobSpy-main) that default is scrypt, not pbkdf2 or
 * bcrypt. See werkzeug.security._hash_internal.
 *
 * pbkdf2 verification is included for completeness (Werkzeug still supports reading it),
 * but relies on the JDK's SunJCE PBKDF2 provider, whose char[]-to-bytes conversion for
 * non-ASCII passwords is not guaranteed to match Python's UTF-8 encoding. The scrypt
 * path (the actual default, and the one this encoder writes) has no such ambiguity —
 * both sides operate on raw UTF-8 bytes.
 */
public class WerkzeugPasswordEncoder implements PasswordEncoder {

    private static final String SALT_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int SALT_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    // Matches Werkzeug 3.1.x's default scrypt parameters.
    private static final int SCRYPT_N = 32768;
    private static final int SCRYPT_R = 8;
    private static final int SCRYPT_P = 1;
    private static final int SCRYPT_DK_LEN = 64;

    private static final int PBKDF2_DEFAULT_ITERATIONS = 1_000_000;

    @Override
    public String encode(CharSequence rawPassword) {
        String salt = generateSalt();
        byte[] digest = SCrypt.generate(
                rawPassword.toString().getBytes(StandardCharsets.UTF_8),
                salt.getBytes(StandardCharsets.UTF_8),
                SCRYPT_N, SCRYPT_R, SCRYPT_P, SCRYPT_DK_LEN);
        return "scrypt:%d:%d:%d$%s$%s".formatted(SCRYPT_N, SCRYPT_R, SCRYPT_P, salt, HexFormat.of().formatHex(digest));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null) {
            return false;
        }
        String[] parts = encodedPassword.split("\\$", 3);
        if (parts.length != 3) {
            return false;
        }
        String method = parts[0];
        String salt = parts[1];
        String expectedHex = parts[2];

        byte[] passwordBytes = rawPassword.toString().getBytes(StandardCharsets.UTF_8);
        byte[] saltBytes = salt.getBytes(StandardCharsets.UTF_8);

        try {
            byte[] actual = computeDigest(method, passwordBytes, saltBytes, expectedHex.length() / 2);
            return constantTimeEquals(HexFormat.of().formatHex(actual), expectedHex);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] computeDigest(String method, byte[] passwordBytes, byte[] saltBytes, int dkLen) throws Exception {
        String[] methodParts = method.split(":");
        String algorithm = methodParts[0];

        if ("scrypt".equals(algorithm)) {
            int n = methodParts.length > 1 ? Integer.parseInt(methodParts[1]) : SCRYPT_N;
            int r = methodParts.length > 2 ? Integer.parseInt(methodParts[2]) : SCRYPT_R;
            int p = methodParts.length > 3 ? Integer.parseInt(methodParts[3]) : SCRYPT_P;
            return SCrypt.generate(passwordBytes, saltBytes, n, r, p, dkLen);
        }

        if ("pbkdf2".equals(algorithm)) {
            String hashName = methodParts.length > 1 ? methodParts[1] : "sha256";
            int iterations = methodParts.length > 2 ? Integer.parseInt(methodParts[2]) : PBKDF2_DEFAULT_ITERATIONS;
            String jceAlgorithm = switch (hashName) {
                case "sha1" -> "PBKDF2WithHmacSHA1";
                case "sha256" -> "PBKDF2WithHmacSHA256";
                case "sha512" -> "PBKDF2WithHmacSHA512";
                default -> throw new IllegalArgumentException("Unsupported pbkdf2 hash: " + hashName);
            };
            SecretKeyFactory factory = SecretKeyFactory.getInstance(jceAlgorithm);
            char[] passwordChars = new String(passwordBytes, StandardCharsets.UTF_8).toCharArray();
            PBEKeySpec spec = new PBEKeySpec(passwordChars, saltBytes, iterations, dkLen * 8);
            return factory.generateSecret(spec).getEncoded();
        }

        throw new IllegalArgumentException("Unsupported password hash method: " + method);
    }

    private static String generateSalt() {
        StringBuilder sb = new StringBuilder(SALT_LENGTH);
        for (int i = 0; i < SALT_LENGTH; i++) {
            sb.append(SALT_CHARS.charAt(RANDOM.nextInt(SALT_CHARS.length())));
        }
        return sb.toString();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
