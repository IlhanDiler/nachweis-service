package de.diler.nachweis.credential;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VerificationController {

    private final VerificationService verificationService;

    public VerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping("/api/verify")
    public VerificationResult verify(@Valid @RequestBody VerifyRequest request,
                                     @RequestHeader(value = CredentialController.ACTOR_HEADER,
                                             defaultValue = CredentialController.UNKNOWN_ACTOR) String actor) {
        return verificationService.verify(request.credential(), actor);
    }
}
