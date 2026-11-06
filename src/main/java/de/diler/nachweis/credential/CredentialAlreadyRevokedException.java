package de.diler.nachweis.credential;

import java.util.UUID;

public class CredentialAlreadyRevokedException extends RuntimeException {

    public CredentialAlreadyRevokedException(UUID id) {
        super("Nachweis " + id + " ist bereits widerrufen");
    }
}
