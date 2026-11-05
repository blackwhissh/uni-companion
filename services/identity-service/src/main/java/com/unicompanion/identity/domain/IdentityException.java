package com.unicompanion.identity.domain;

public final class IdentityException extends RuntimeException {

    public enum Code {
        WEAK_PASSWORD,
        EMAIL_TAKEN,
        INVALID_CREDENTIALS,
        UNAUTHENTICATED,
        FORBIDDEN,
        NOT_FOUND,
        ADMIN_ACCOUNT_PROTECTED
    }

    private final Code code;

    private IdentityException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public static IdentityException weakPassword() {
        return new IdentityException(Code.WEAK_PASSWORD, "Password must be at least 8 characters.");
    }

    public static IdentityException emailTaken() {
        return new IdentityException(Code.EMAIL_TAKEN, "An account with this email already exists.");
    }

    public static IdentityException invalidCredentials() {
        return new IdentityException(Code.INVALID_CREDENTIALS, "Invalid username or password.");
    }

    public static IdentityException unauthenticated() {
        return new IdentityException(Code.UNAUTHENTICATED, "Authentication is required.");
    }

    public static IdentityException forbidden() {
        return new IdentityException(Code.FORBIDDEN, "Only a platform admin can do that.");
    }

    public static IdentityException notFound() {
        return new IdentityException(Code.NOT_FOUND, "That account does not exist.");
    }

    public static IdentityException adminAccountProtected() {
        return new IdentityException(Code.ADMIN_ACCOUNT_PROTECTED, "Platform admin accounts cannot be deleted.");
    }
}
