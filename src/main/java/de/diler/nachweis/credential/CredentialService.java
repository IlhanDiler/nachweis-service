package de.diler.nachweis.credential;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class CredentialService {

    static final String ISSUER = "https://nachweis.example.de";
    static final Duration VALIDITY = Duration.ofDays(365);

    private final ECKey signingKey;

    public CredentialService(ECKey signingKey) {
        this.signingKey = signingKey;
    }

    public String issue(CredentialRequest request) throws JOSEException {
        Instant now = Instant.now();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(VALIDITY)))
                .jwtID(UUID.randomUUID().toString())
                .claim("name", request.name())
                .claim("geburtsdatum", request.geburtsdatum().toString())
                .claim("versichertennummer", request.versichertennummer())
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(signingKey.getKeyID())
                .build();

        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new ECDSASigner(signingKey));
        return jwt.serialize();
    }
}
