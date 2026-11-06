package de.diler.nachweis.credential;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

public record CredentialRequest(
        @NotBlank String name,
        @NotNull @Past LocalDate geburtsdatum,
        @NotBlank String versichertennummer
) {
}
