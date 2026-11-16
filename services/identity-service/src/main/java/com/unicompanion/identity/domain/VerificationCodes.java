package com.unicompanion.identity.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Six-digit codes. Only a hash of pepper + user + code is stored, so a database read is not the code itself.
 */
public final class VerificationCodes {

    public static final int LENGTH = 6;

    private VerificationCodes() {
    }

    public static String generate(SecureRandom random) {
        char[] chars = new char[LENGTH];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = (char) ('0' + random.nextInt(10));
        }
        return new String(chars);
    }

    /** Keeps digits and drops spaces or hyphens. Anything else is not a code. */
    public static String normalize(String submitted) {
        if (submitted == null) {
            return "";
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < submitted.length(); i++) {
            char c = submitted.charAt(i);
            if (c == ' ' || c == '-') {
                continue;
            }
            if (c < '0' || c > '9') {
                return "";
            }
            digits.append(c);
        }
        return digits.toString();
    }

    public static String hash(String pepper, UUID userId, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(pepper.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(userId.toString().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(code.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }

    public static boolean matches(String expectedHash, String pepper, UUID userId, String code) {
        if (expectedHash == null || code == null || code.length() != LENGTH) {
            return false;
        }
        byte[] expected = expectedHash.getBytes(StandardCharsets.US_ASCII);
        byte[] actual = hash(pepper, userId, code).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }
}
