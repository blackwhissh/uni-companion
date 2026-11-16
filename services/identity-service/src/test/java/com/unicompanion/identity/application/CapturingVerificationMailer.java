package com.unicompanion.identity.application;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Test delivery. The code stays in memory for the API test and is never logged.
 */
@Component
@Profile("test")
public class CapturingVerificationMailer implements VerificationMailer {

    private final ConcurrentHashMap<String, String> lastCodeByEmail = new ConcurrentHashMap<>();

    @Override
    public boolean canSend() {
        return true;
    }

    @Override
    public String delivery() {
        return "test";
    }

    @Override
    public void sendCode(String email, String code) {
        lastCodeByEmail.put(email, code);
    }

    public String lastCode(String email) {
        return lastCodeByEmail.get(email);
    }
}
