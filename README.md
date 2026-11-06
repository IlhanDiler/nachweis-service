# Nachweis-Service

[![CI](https://github.com/IlhanDiler/nachweis-service/actions/workflows/ci.yml/badge.svg)](https://github.com/IlhanDiler/nachweis-service/actions/workflows/ci.yml)

Ein Backend-Service, der **digitale Nachweise** (z. B. eine Versicherungsbescheinigung) als
**signierte Credentials** ausstellt, prüft und widerruft. Eine Behörde stellt den Nachweis aus,
eine prüfende Stelle kann anschließend Echtheit, Gültigkeit, Aussteller und Widerrufsstatus
verifizieren. Jede Aktion wird im Audit-Log festgehalten.

> Alle Daten in diesem Projekt sind **erfunden** (Testdaten).

## Funktionsumfang

| Endpunkt | Beschreibung |
|---|---|
| `POST /api/credentials` | Stellt einen Nachweis als signiertes JWT (ES256) aus |
| `POST /api/verify` | Prüft Signatur, Algorithmus, Ablaufdatum, Aussteller und Widerruf |
| `POST /api/credentials/{id}/revoke` | Widerruft einen Nachweis (`404` unbekannt, `409` bereits widerrufen) |
| `GET /api/statistics` | Auswertung: Nachweise nach Status und Monat, Audit-Aktionen, Prüfungen nach Status |

Ein ausgestellter Nachweis enthält Name, Geburtsdatum und eine fiktive Versichertennummer
sowie die Standard-Claims `iss` (Aussteller), `iat` (ausgestellt am), `exp` (gültig bis)
und `jti` (eindeutige ID).

Wer eine Aktion ausführt, wird über den Header `X-Actor` übergeben (Standard: `anonym`).
In einem echten System käme dieser Wert aus der Anmeldung.

Fehler werden einheitlich als `ProblemDetail` (RFC 9457) zurückgegeben, z. B.:

```json
{ "status": 409, "title": "Nachweis bereits widerrufen",
  "detail": "Nachweis 85cde5f2-... ist bereits widerrufen",
  "instance": "/api/credentials/85cde5f2-.../revoke" }
```

## Architektur

```
  Behörde / Prüfstelle                 Nachweis-Service
 ──────────────────────    ──────────────────────────────────────────────
                           │                                            │
  POST /api/credentials ──►│ CredentialController ─► CredentialService ─┼─ signiert mit KeyConfig
  POST .../{id}/revoke  ──►│                          (Ausstellen,       │  (EC-Schlüssel P-256)
                           │                           Widerrufen)       │
  POST /api/verify      ──►│ VerificationController ► VerificationService│  Format, Algorithmus,
                           │                                            │  Signatur, Ablauf,
                           │                                            │  Aussteller, Widerruf
  GET /api/statistics   ──►│ StatisticsController ──► StatisticsService  │  JPQL: GROUP BY, JOIN
                           │                                            │
                           │            AuditService (gleiche Transaktion)
                           └───────────────────────┬────────────────────┘
                                                   │ JPA / Hibernate
                                  ┌────────────────┴────────────────┐
                                  │ credential          audit_log   │
                                  │ (Metadaten)  ◄─FK── (wer/wann/  │
                                  │                      was)       │
                                  └─────────────────────────────────┘
                                  PostgreSQL 17  (getestet auch: Oracle 23)
```

Code-Struktur (`src/main/java/de/diler/nachweis`):

- `credential/` – Ausstellen, Prüfen, Widerrufen
  - `KeyConfig` – erzeugt das Schlüsselpaar für die Signatur
  - `CredentialController` / `CredentialService` – Ausstellen und Widerruf
  - `VerificationController` / `VerificationService` – Prüfen
  - `CredentialRecord` / `CredentialRepository` – gespeicherte Metadaten (JPA)
  - `CredentialExceptionHandler` – Fehler als `ProblemDetail`
- `audit/` – `AuditEntry`, `AuditRepository`, `AuditService`
- `statistics/` – Auswertungen über JPQL
- `src/main/resources/db/migration/{postgresql,oracle}` – Flyway-Migrationen je Datenbank

## Datenbank

| Tabelle | Inhalt |
|---|---|
| `credential` | ID (= `jti`), Typ, Ausstellungs-/Ablaufdatum, Status, Widerrufszeitpunkt, Version |
| `audit_log` | Aktion (`ISSUED`/`VERIFIED`/`REVOKED`), Nachweis-ID (Fremdschlüssel), Akteur, Zeitpunkt, Details |

- **Migrationen:** V1 Tabelle `credential`, V2 Constraints/Indizes/Widerruf, V3 `audit_log`
- **CHECK-Constraints:** gültige Statuswerte, `expires_at > issued_at`, und „widerrufen“ genau
  dann, wenn ein Widerrufszeitpunkt gesetzt ist – die Datenbank schützt die Regeln auch dann,
  wenn jemand am Java-Code vorbei schreibt.
- **Indizes** auf Status, Ausstellungsdatum, Nachweis-ID und Zeitpunkt im Audit-Log.
- **Optimistic Locking** (`@Version`): Widerrufen zwei Sachbearbeiter gleichzeitig denselben
  Nachweis, gewinnt der erste; der zweite erhält `409 Conflict` statt einer stillen Überschreibung.
- **Transaktionen:** Aktion und Audit-Eintrag werden gemeinsam geschrieben oder gemeinsam
  zurückgerollt.

## Technik

Java 21 · Spring Boot 4 · Spring Data JPA / Hibernate · Maven · nimbus-jose-jwt ·
PostgreSQL 17 · Oracle 23 (Tests) · Flyway · JUnit 5 · Mockito · Testcontainers ·
Docker Compose · GitHub Actions

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
$h = @{ "X-Actor" = "sachbearbeiterin.mueller" }

# Nachweis ausstellen
$body = @{ name = "Max Mustermann"; geburtsdatum = "1990-05-01"; versichertennummer = "A123456789" } | ConvertTo-Json
$jwt = (Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/credentials -ContentType "application/json" -Body $body -Headers $h).credential

# Nachweis prüfen  ->  { "valid": true, "reasons": [] }
$pruefung = @{ credential = $jwt } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/verify -ContentType "application/json" -Body $pruefung

# ID (jti) aus dem JWT lesen und Nachweis widerrufen
$teil = $jwt.Split('.')[1].Replace('-','+').Replace('_','/'); while ($teil.Length % 4) { $teil += '=' }
$id = ([Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($teil)) | ConvertFrom-Json).jti
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/credentials/$id/revoke" -Headers $h

# Erneut prüfen  ->  { "valid": false, "reasons": ["Nachweis widerrufen"] }
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/verify -ContentType "application/json" -Body $pruefung

# Statistik
Invoke-RestMethod http://localhost:8080/api/statistics | ConvertTo-Json -Depth 5
```

### Tests

```powershell
.\mvnw.cmd verify
```

Docker muss laufen. Der erste Lauf lädt das Oracle-Image (ca. 1,5 GB) und dauert entsprechend.

| Testklasse | Art | Inhalt |
|---|---|---|
| `VerificationServiceTest` | Unit (Mockito) | gültig, manipuliert, abgelaufen, falscher Aussteller, fremder Schlüssel, widerrufen, kein JWT |
| `PostgresDatabaseTest` | Integration (Testcontainers) | Speicherung, Widerruf, Audit-Log, Optimistic Locking, CHECK-Constraints, Statistik |
| `OracleDatabaseTest` | Integration (Testcontainers) | **dieselben Tests** gegen Oracle 23 Free |

Beide Datenbanktests erben von `CredentialDatabaseContractTest`, sodass derselbe Code
nachweislich auf PostgreSQL und Oracle funktioniert.

Bei jedem Push führt eine GitHub Action (`.github/workflows/ci.yml`) `mvnw verify` aus.

## Entscheidungen

**Warum ein signiertes JWT?**
Ein JWT ist ein etablierter, kompakter Standard (RFC 7519). Durch die Signatur (JWS) kann
jeder mit dem öffentlichen Schlüssel prüfen, dass der Inhalt vom Aussteller stammt und nicht
verändert wurde – ohne Rückfrage beim Aussteller. Der Inhalt ist nur codiert, nicht
verschlüsselt.

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

**Warum wird der Widerruf erst nach gültiger Signatur nachgeschlagen?**
Die ID (`jti`) im Token ist nur vertrauenswürdig, wenn die Signatur stimmt. Sonst könnte ein
Angreifer eine fremde ID eintragen und z. B. Einträge im Audit-Log einem echten Nachweis
zuordnen lassen.

**Warum werden alle Prüfgründe gesammelt?**
Die prüfende Stelle erfährt so in einer Antwort alles, was nicht stimmt (z. B. abgelaufen
*und* falscher Aussteller), statt nur den ersten Fehler.

**Warum nur Metadaten in der Datenbank?**
Datensparsamkeit (Art. 5 DSGVO): Für Widerruf und Nachvollziehbarkeit reichen ID, Typ,
Zeitraum und Status. Eine Kopie der personenbezogenen Daten beim Aussteller wäre ein
unnötiges Risiko. Auch das Audit-Log enthält keine Personendaten.

**Warum Flyway statt automatischer Schema-Erzeugung durch Hibernate?**
Das Schema ist versioniert und nachvollziehbar im Repository. Hibernate prüft beim Start nur
(`ddl-auto=validate`), ob Entity und Tabelle zusammenpassen.

**Warum getrennte Migrationen für PostgreSQL und Oracle?**
Die Datentypen unterscheiden sich (`UUID` ↔ `RAW(16)`, `TIMESTAMPTZ` ↔
`TIMESTAMP WITH TIME ZONE`, `BIGINT` ↔ `NUMBER(19)`). Flyway wählt über
`db/migration/{vendor}` automatisch den passenden Ordner. Der Java-Code und die
JPQL-Abfragen bleiben für beide Datenbanken identisch.

**Warum Optimistic statt Pessimistic Locking?**
Gleichzeitige Änderungen am selben Nachweis sind selten. Optimistic Locking kommt ohne
Datenbanksperren aus und erkennt Konflikte trotzdem zuverlässig.

**Warum JPQL statt nativem SQL für die Statistik?**
Hibernate übersetzt JPQL in den Dialekt der jeweiligen Datenbank – dieselbe Abfrage mit
`GROUP BY`, `JOIN` und `EXTRACT` läuft so auf PostgreSQL und Oracle.

## Bekannte Einschränkungen und nächste Schritte

- Der Signaturschlüssel wird beim Start **im Speicher erzeugt**. Nach einem Neustart können
  ältere Nachweise nicht mehr geprüft werden. Geplant: persistenter Schlüssel (z. B. HSM oder
  Key Vault) und Schlüsselrotation über mehrere `kid`.
- Der Widerruf wird derzeit direkt in der Datenbank des Ausstellers nachgeschlagen. Für
  externe Prüfer wäre eine öffentliche, signierte Statusliste (IETF Token Status List)
  der nächste Schritt.
- `GET /.well-known/jwks.json` zur Veröffentlichung des öffentlichen Schlüssels.
- Authentifizierung und Rollen (z. B. Spring Security): Nur Sachbearbeiter dürfen ausstellen
  und widerrufen; `X-Actor` würde dann aus dem Login übernommen.
- OpenAPI/Swagger-Dokumentation.
- Selective Disclosure (SD-JWT), z. B. nur „über 18: ja“ statt Geburtsdatum.
