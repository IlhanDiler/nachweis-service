package de.diler.nachweis.credential;

import java.util.UUID;

public class CredentialNotFoundException extends RuntimeException {

    public CredentialNotFoundException(UUID id) {
        super("Nachweis " + id + " existiert nicht");
    }
}
