package de.diler.nachweis.credential;

import jakarta.validation.constraints.NotBlank;

public record VerifyRequest(@NotBlank String credential) {
}
