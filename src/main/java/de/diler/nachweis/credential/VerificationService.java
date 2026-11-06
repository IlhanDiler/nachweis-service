package de.diler.nachweis.credential;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class VerificationService {

    private final ECKey signingKey;

    public VerificationService(ECKey signingKey) {
        this.signingKey = signingKey;
    }

    public VerificationResult verify(String credential) {
        SignedJWT jwt;
        JWTClaimsSet claims;
        try {
            jwt = SignedJWT.parse(credential);
            claims = jwt.getJWTClaimsSet();
        } catch (ParseException e) {
            return new VerificationResult(false, List.of("Format ungueltig: kein signiertes JWT"));
        }

        List<String> reasons = new ArrayList<>();

        // Nur ES256 akzeptieren, damit kein anderer (schwaecherer) Algorithmus untergeschoben werden kann.
        if (!JWSAlgorithm.ES256.equals(jwt.getHeader().getAlgorithm())) {
            reasons.add("Algorithmus nicht erlaubt");
        } else if (!isSignatureValid(jwt)) {
            reasons.add("Signatur ungueltig");
        }

        Date expiration = claims.getExpirationTime();
        if (expiration == null || expiration.before(new Date())) {
            reasons.add("Nachweis abgelaufen");
        }

        if (!CredentialService.ISSUER.equals(claims.getIssuer())) {
            reasons.add("Unbekannter Aussteller");
        }

        return new VerificationResult(reasons.isEmpty(), reasons);
    }

    private boolean isSignatureValid(SignedJWT jwt) {
        try {
            return jwt.verify(new ECDSAVerifier(signingKey.toPublicJWK()));
        } catch (JOSEException e) {
            return false;
        }
    }
}
