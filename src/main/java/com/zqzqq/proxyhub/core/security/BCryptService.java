package com.zqzqq.proxyhub.core.security;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Wrapper around Spring Security's BCryptPasswordEncoder.
 * Provides password hashing and matching, plus random password generation.
 */
@Service
public class BCryptService {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Hash a plain-text password using BCrypt.
     */
    public String hash(String plainPassword) {
        if (plainPassword == null || plainPassword.isBlank()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        return ENCODER.encode(plainPassword);
    }

    /**
     * Check a plain-text password against a BCrypt hash.
     */
    public boolean matches(String plainPassword, String hash) {
        if (plainPassword == null || hash == null) {
            return false;
        }
        return ENCODER.matches(plainPassword, hash);
    }

    /**
     * Generate a random 16-character alphanumeric password.
     */
    public static String generateRandomPassword() {
        byte[] bytes = new byte[12];
        SECURE_RANDOM.nextBytes(bytes);
        String base64 = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return base64.substring(0, 16);
    }

    // Static convenience methods for use outside DI context
    public static String hashStatic(String plain) {
        return ENCODER.encode(plain);
    }

    public static boolean matchesStatic(String plain, String hash) {
        return ENCODER.matches(plain, hash);
    }
}
