package com.unicompanion.identity.api;

public record AuthResponse(String token, UserResponse user) {
}
