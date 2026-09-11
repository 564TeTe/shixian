package com.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;

/** Passwords for newly initialized teaching accounts, never stored as plaintext. */
public final class TeachingPasswords {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(10);

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@";

    private TeachingPasswords() {}

    public static String hash(String password) {
        if (password == null
                || password.length() < 8
                || password.length() > 64
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("密码长度须为8到64位");
        }
        return ENCODER.encode(password);
    }

    public static boolean matches(String password, String encoded) {
        return password != null
                && encoded != null
                && encoded.startsWith("$2")
                && ENCODER.matches(password, encoded);
    }

    public static String newPassword() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 14; i++) {
            result.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return result.toString();
    }
}
