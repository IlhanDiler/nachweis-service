# Nachweis-Service

Ein Backend-Service, der **digitale Nachweise** (z. B. eine Versicherungsbescheinigung) als
**signierte Credentials** ausstellt und prüft. Eine Behörde stellt den Nachweis aus, eine
prüfende Stelle kann anschließend Echtheit, Gültigkeit und Aussteller verifizieren, ohne
den Aussteller erneut anfragen zu müssen.

> Alle Daten in diesem Projekt sind **erfunden** (Testdaten).

## Funktionsumfang

| Endpunkt | Beschreibung |
|---|---|
| `POST /api/credentials` | Stellt einen Nachweis als signiertes JWT (ES256) aus |
| `POST /api/verify` | Prüft Signatur, Ablaufdatum und Aussteller eines Nachweises |

Ein ausgestellter Nachweis enthält Name, Geburtsdatum und eine fiktive Versichertennummer
sowie die Standard-Claims `iss` (Aussteller), `iat` (ausgestellt am), `exp` (gültig bis)
und `jti` (eindeutige ID).

## Architektur

```
  Behörde                        Nachweis-Service                         Prüfende Stelle
 ─────────                ─────────────────────────────────               ───────────────
                          │                               │
  POST /api/credentials ─►│ CredentialController          │
  { name, geburtsdatum,   │        │                      │
    versichertennummer }  │        ▼                      │
                          │ CredentialService ──signiert──┤◄── KeyConfig
  ◄── { credential: JWT } │  (Claims + ES256-Signatur)    │    (EC-Schlüsselpaar P-256)
                          │                               │         │
                          │                               │         │ öffentlicher Schlüssel
                          │ VerificationController        │         ▼
                          │        │                      │◄── POST /api/verify
                          │        ▼                      │    { credential: JWT }
                          │ VerificationService ──────────┤
                          │  1. Format   2. Algorithmus   │──► { valid: true/false,
                          │  3. Signatur 4. Ablauf        │      reasons: [...] }
                          │  5. Aussteller                │
                          └───────────────────────────────┘
                                         │
                          PostgreSQL (Docker, angebunden – Speicherung folgt)
```

Code-Struktur (`src/main/java/de/diler/nachweis/credential`):

- `KeyConfig` – erzeugt das Schlüsselpaar für die Signatur
- `CredentialController` / `CredentialService` – Ausstellen
- `VerificationController` / `VerificationService` – Prüfen
- `CredentialRequest`, `VerifyRequest`, `VerificationResult` – Ein- und Ausgabedaten

## Technik

Java 21 · Spring Boot 4 · Maven · nimbus-jose-jwt · PostgreSQL 17 · Flyway · JUnit 5 ·
Testcontainers · Docker Compose

## Starten

Voraussetzungen: **Java 21** und **Docker Desktop**.

```powershell
# 1. Datenbank starten
docker compose up -d

# 2. Anwendung starten (läuft auf http://localhost:8080)
.\mvnw.cmd spring-boot:run
```

Unter Linux/macOS statt `.\mvnw.cmd` einfach `./mvnw` verwenden.

### Ausprobieren (PowerShell, in einem zweiten Terminal)

```powershell
# Nachweis ausstellen
$body = @{ name = "Max Mustermann"; geburtsdatum = "1990-05-01"; versichertennummer = "A123456789" } | ConvertTo-Json
$jwt = (Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/credentials -ContentType "application/json" -Body $body).credential

# Nachweis prüfen
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/verify -ContentType "application/json" -Body (@{ credential = $jwt } | ConvertTo-Json)
```

Antwort bei einem gültigen Nachweis:

```json
{ "valid": true, "reasons": [] }
```

Bei einem manipulierten, abgelaufenen oder fremden Nachweis ist `valid` `false` und
`reasons` enthält alle gefundenen Gründe, z. B. `"Signatur ungueltig"`.

### Tests

```powershell
.\mvnw.cmd verify
```

Die Unit-Tests in `VerificationServiceTest` decken ab: gültiger Nachweis, manipulierter
Inhalt, abgelaufener Nachweis, falscher Aussteller, fremder Schlüssel und ungültiges Format.
Der Kontext-Test startet über Testcontainers eine echte PostgreSQL – dafür muss Docker laufen.

## Entscheidungen

**Warum ein signiertes JWT?**
Ein JWT ist ein etablierter, kompakter Standard (RFC 7519). Durch die Signatur (JWS) kann
jeder mit dem öffentlichen Schlüssel prüfen, dass der Inhalt vom Aussteller stammt und nicht
verändert wurde – ohne Rückfrage beim Aussteller.

**Warum ES256 und nicht HS256 oder RS256?**
- **HS256** nutzt ein gemeinsames Geheimnis: Wer prüfen kann, könnte auch fälschen. Für
  Nachweise, die von fremden Stellen geprüft werden, ist das ungeeignet.
- **ES256** (ECDSA mit Kurve P-256) ist asymmetrisch: Nur der Aussteller besitzt den privaten
  Schlüssel, Prüfer brauchen nur den öffentlichen.
- Gegenüber **RS256** bietet ES256 vergleichbare Sicherheit bei deutlich kürzeren Schlüsseln
  und Signaturen. ES256 wird auch im europäischen Umfeld digitaler Identitäten (EUDI-Wallet,
  SD-JWT VC) verwendet.

**Warum wird der Algorithmus explizit geprüft?**
Ein bekannter Angriff auf JWTs besteht darin, im Header einen anderen Algorithmus (z. B.
`none` oder `HS256`) anzugeben. Der Service akzeptiert deshalb ausschließlich ES256.

**Warum werden alle Prüfgründe gesammelt?**
Die prüfende Stelle erfährt so in einer Antwort alles, was nicht stimmt (z. B. abgelaufen
*und* falscher Aussteller), statt nur den ersten Fehler.

**Warum nimbus-jose-jwt?**
Weit verbreitete, gut gepflegte Bibliothek, die auch Spring Security intern nutzt.

## Bekannte Einschränkungen und nächste Schritte

- Der Signaturschlüssel wird beim Start **im Speicher erzeugt**. Nach einem Neustart können
  ältere Nachweise nicht mehr geprüft werden. Geplant: persistenter Schlüssel und
  Schlüsselrotation über mehrere `kid`.
- Speicherung der Nachweis-**Metadaten** (ID, Typ, Ausstellungs-/Ablaufdatum, Status) in
  PostgreSQL per Flyway-Migration – bewusst ohne Kopie der personenbezogenen Daten.
- Widerruf (`POST /api/credentials/{id}/revoke`) mit Statusliste.
- `GET /.well-known/jwks.json` zur Veröffentlichung des öffentlichen Schlüssels.
- Einheitliche Fehlerantworten mit `ProblemDetail`, OpenAPI/Swagger-Dokumentation.
- Audit-Log, Integrationstests gegen PostgreSQL, CI mit GitHub Actions.
- Selective Disclosure (SD-JWT), z. B. nur „über 18: ja“ statt Geburtsdatum.
