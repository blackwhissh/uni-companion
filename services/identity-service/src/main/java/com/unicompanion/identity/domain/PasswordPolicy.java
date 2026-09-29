package com.unicompanion.identity.domain;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    private PasswordPolicy() {
    }

    public static void check(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw IdentityException.weakPassword();
        }
    }
}
