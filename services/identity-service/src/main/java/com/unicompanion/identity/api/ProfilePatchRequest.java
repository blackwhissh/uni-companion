package com.unicompanion.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfilePatchRequest(
        @NotBlank @Size(max = 120) String displayName,
        @Size(max = 500) String interests
) {
}
