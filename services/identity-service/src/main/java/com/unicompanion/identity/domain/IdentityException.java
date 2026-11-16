package com.unicompanion.identity.domain;

public final class IdentityException extends RuntimeException {

    public enum Code {
        WEAK_PASSWORD,
        EMAIL_TAKEN,
        INVALID_CREDENTIALS,
        UNAUTHENTICATED,
        FORBIDDEN,
        NOT_FOUND,
        ADMIN_ACCOUNT_PROTECTED,
        NOT_UNIVERSITY_EMAIL,
        VERIFICATION_UNAVAILABLE,
        VERIFICATION_TOO_SOON,
        VERIFICATION_CODE_INVALID,
        VERIFICATION_EXHAUSTED,
        VERIFICATION_EXPIRED
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

    public static IdentityException notUniversityEmail() {
        return new IdentityException(
                Code.NOT_UNIVERSITY_EMAIL,
                "Verification is for a university address, such as name@uni-siegen.de."
        );
    }

    public static IdentityException verificationUnavailable() {
        return new IdentityException(
                Code.VERIFICATION_UNAVAILABLE,
                "Email delivery is not configured, so a code cannot be sent yet."
        );
    }

    public static IdentityException verificationTooSoon() {
        return new IdentityException(
                Code.VERIFICATION_TOO_SOON,
                "A code was just sent. Wait a minute before asking for another."
        );
    }

    public static IdentityException verificationCodeInvalid() {
        return new IdentityException(Code.VERIFICATION_CODE_INVALID, "That code does not match.");
    }

    public static IdentityException verificationExhausted() {
        return new IdentityException(Code.VERIFICATION_EXHAUSTED, "Too many tries. Request a new code.");
    }

    public static IdentityException verificationExpired() {
        return new IdentityException(Code.VERIFICATION_EXPIRED, "That code expired. Request a new one.");
    }
}
