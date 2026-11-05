package com.unicompanion.learning.privacy;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/me/data")
public class PersonalDataController {

    private final PersonalDataService personalData;

    public PersonalDataController(PersonalDataService personalData) {
        this.personalData = personalData;
    }

    @GetMapping
    public PersonalDataService.PersonalDataExport exportData(@AuthenticationPrincipal Jwt jwt) {
        return personalData.exportFor(UUID.fromString(jwt.getSubject()));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void purgeData(@AuthenticationPrincipal Jwt jwt) {
        personalData.purgeFor(UUID.fromString(jwt.getSubject()));
    }
}
