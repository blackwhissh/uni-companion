package com.unicompanion.identity.api;

import com.unicompanion.identity.application.AccountService;
import com.unicompanion.identity.application.AccountView;
import com.unicompanion.identity.application.TokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AccountService accounts;
    private final TokenService tokens;

    public AuthController(AccountService accounts, TokenService tokens) {
        this.accounts = accounts;
        this.tokens = tokens;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        AccountView account = accounts.register(request.email(), request.password(), request.displayName());
        return new AuthResponse(tokens.issue(account), UserResponse.from(account));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        AccountView account = accounts.login(request.email(), request.password());
        return new AuthResponse(tokens.issue(account), UserResponse.from(account));
    }
}
