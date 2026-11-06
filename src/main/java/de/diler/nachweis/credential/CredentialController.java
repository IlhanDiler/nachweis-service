package de.diler.nachweis.credential;

import com.nimbusds.jose.JOSEException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/credentials")
public class CredentialController {

    // Ohne Anmeldung gibt der Aufrufer sich per Header an; in Produktion kaeme der Wert
    // aus der Authentifizierung (z. B. Benutzer aus dem JWT des Mitarbeiters).
    static final String ACTOR_HEADER = "X-Actor";
    static final String UNKNOWN_ACTOR = "anonym";

    private final CredentialService credentialService;

    public CredentialController(CredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> issue(@Valid @RequestBody CredentialRequest request,
                                     @RequestHeader(value = ACTOR_HEADER, defaultValue = UNKNOWN_ACTOR) String actor)
            throws JOSEException {
        return Map.of("credential", credentialService.issue(request, actor));
    }

    @PostMapping("/{id}/revoke")
    public RevocationResponse revoke(@PathVariable UUID id,
                                     @RequestHeader(value = ACTOR_HEADER, defaultValue = UNKNOWN_ACTOR) String actor) {
        CredentialRecord record = credentialService.revoke(id, actor);
        return new RevocationResponse(record.getId(), record.getStatus(), record.getRevokedAt());
    }

    public record RevocationResponse(UUID id, CredentialStatus status, Instant revokedAt) {
    }
}
