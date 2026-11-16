package com.unicompanion.identity.application;

/**
 * Delivers a university verification code. Production without a mailer reports that it cannot send.
 */
public interface VerificationMailer {

    boolean canSend();

    /** Where the code goes: {@code log}, {@code mail}, or {@code test}. */
    String delivery();

    void sendCode(String email, String code);
}
