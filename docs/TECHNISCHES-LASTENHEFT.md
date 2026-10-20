# Technisches Lastenheft: Elternstunden-Zeiterfassung

| | |
|---|---|
| Version | 0.1 (Entwurf) |
| Stand | 2026-10-20 |
| Einrichtung | Hort an der Montessorischule (Elterninitiative, Verein) |
| Zielgruppe | Entwickler:innen und KI-Coding-Agents |
| Fachliche Basis | Lastenheft v0.2 (PDF, 2 Seiten, für die Abstimmung mit Menschen). Dieses Dokument konkretisiert es technisch. |

---

## 0. Lesehinweise für KI-Agents (verbindlich)

### 0.1 Status-Tags

Jede Entscheidung und Anforderung trägt einen Status:

| Tag | Bedeutung | Umgang |
|---|---|---|
| `[DECIDED]` | Vom Auftraggeber entschieden | Exakt umsetzen. Nicht ändern, nicht "verbessern". |
| `[PROPOSED]` | Vorschlag, noch nicht bestätigt | Darf als Default umgesetzt werden. Muss änderbar bleiben (Konfiguration oder klar isolierte Stelle). Nicht als Fakt behandeln. |
| `[OPEN]` | Ungeklärt | **Nicht raten.** Nur den angegebenen Interim-Default umsetzen und im Code mit `// OPEN-xx` markieren, oder beim Auftraggeber nachfragen. Siehe Abschnitt 16. |

Bei Konflikten gilt: `[DECIDED]` vor Lastenheft v0.2 vor diesem Dokument vor eigenen Annahmen.

### 0.2 Normative Wörter

**MUSS** und **DARF NICHT** sind zwingend. **SOLL** ist die Regel, Abweichung nur mit Begründung. **KANN** ist optional.

### 0.3 ID-Schema

| Präfix | Bedeutung |
|---|---|
| `D-xx` | Entscheidung (Abschnitt 2) |
| `FS-xx` | Funktionale Spezifikation (Abschnitt 7) |
| `SEC-xx` | Sicherheitsanforderung (Abschnitt 9) |
| `DP-xx` | Datenschutz (Abschnitt 10) |
| `BAK-xx` | Backup und Restore (Abschnitt 11) |
| `OPS-xx` | Betrieb (Abschnitt 12) |
| `UI-xx` | Frontend (Abschnitt 13) |
| `NFR-xx` | Nichtfunktional (Abschnitt 14) |
| `AC-xx` | Abnahmekriterium (Abschnitt 15) |
| `OPEN-xx` | Offene Frage (Abschnitt 16) |
| `F1`..`F10`, `N1`..`N6` | Verweise auf das Lastenheft v0.2 |

### 0.4 Arbeitsregeln

1. Reihenfolge der Umsetzung: Meilensteine aus Abschnitt 17.
2. Kein Feature über diese Spezifikation hinaus. Ideen gehören in Abschnitt 16, nicht in den Code.
3. Jedes Feature gilt erst als fertig (Definition of Done), wenn: Code, Migrationen, Tests (Unit und Integration), Abnahmekriterien aus Abschnitt 15 grün, OpenAPI aktualisiert, keine neuen Warnungen der Security-Scans.
4. Sprache: UI-Texte deutsch (Du-Form, freundlich, kurz). Code, Bezeichner, Commits, Logs englisch. Datenbankbezeichner `snake_case`, englisch.
5. Zeitzone: Fachliche Daten in `Europe/Berlin`. Zeitstempel in der DB als `timestamptz` (UTC).
6. Keine personenbezogenen Daten in Logs, URLs oder Fehlermeldungen (siehe SEC-12).

---

## 1. Ziel und Abgrenzung

### 1.1 Ziel

Eine mobile Web-App (PWA), mit der Familien des Horts ihre geleisteten Elternstunden erfassen, Admins diese bestätigen und pro Schuljahr abrechnen. Jede Familie leistet **10 Stunden pro Schuljahr** `[DECIDED]`.

### 1.2 Größenordnung

Laut Website des Horts: 65 Vollzeitplätze, etwa 70 Kinder in drei Gruppen. Erwartet werden damit etwa 50 bis 70 Familien. Das System SOLL für bis zu 1.000 Familien ohne Umbau funktionieren, MUSS aber nicht darauf optimiert werden. Einfachheit und Betriebssicherheit gehen vor Skalierung.

### 1.3 Nicht im Umfang (sofern nicht anders entschieden) `[PROPOSED]`

- Zahlungsabwicklung oder Buchhaltung (Ausgleichszahlungen bei fehlenden Stunden)
- Terminplanung, Schichtplan oder Aufgabenbörse für Elternstunden
- Chat, Push-Nachrichten, Foto-Uploads
- Native Apps (die PWA genügt)
- Offline-Erfassung
- Mehrmandantenfähigkeit (nur dieser eine Hort)
- Mehrsprachigkeit (nur Deutsch, Texte aber externalisiert, siehe UI-02)

---

## 2. Entscheidungsregister

| ID | Entscheidung | Status | Quelle |
|---|---|---|---|
| D-01 | Abrechnungszeitraum ist das **Schuljahr**, nicht das Kalenderjahr. Standard-Stichtage 1.9. bis 31.8., durch Admins je Schuljahr anpassbar. | `[DECIDED]` | Auftraggeber, 2026-10-20 |
| D-02 | Soll: 10 Stunden pro **Familie** und Schuljahr, unabhängig von der Kinderzahl. | `[DECIDED]` | Auftraggeber |
| D-03 | Das **Kind** ist die zentrale Entität und Primärschlüssel für alle Zuordnungen. Eine Familie hat 1..n Kinder. Elternstunden hängen an der Familie. | `[DECIDED]` | Auftraggeber |
| D-04 | Kinder- und Familiendaten sind später für Drittsysteme per API zugänglich (Ziel). Ausgestaltung siehe FS-INT. | Ziel `[DECIDED]`, Ausgestaltung `[PROPOSED]` | Auftraggeber |
| D-05 | Selbstregistrierung ohne manuellen Admin-Aufwand. Zuordnung zur Familie über **Name + Geburtsdatum des Kindes**. Admins können die Zuordnung nachträglich korrigieren. Die Kinderliste ist nie öffentlich. | `[DECIDED]` | Auftraggeber |
| D-06 | Schutz der Registrierung gegen Bots und Ratversuche durch **Captcha** und Rate-Limits. | `[DECIDED]` | Auftraggeber |
| D-07 | Produkt für Captcha: **ALTCHA** (selbst gehostet, MIT-Lizenz). | `[PROPOSED]` | Recherche, siehe Abschnitt 18 |
| D-08 | Login mit **E-Mail + Passwort** und zusätzlich per **OIDC** (Anmeldung mit Google-Konto, "Gmail"), damit die Registrierung für Eltern einfacher wird. Weitere Anbieter siehe OPEN-15. Beide Wege führen zum selben Nutzerkonto-Modell. | E-Mail+Passwort `[DECIDED]`, OIDC mit Google `[DECIDED]` (Wunsch 2026-10-20), weitere Anbieter `[OPEN]` | Auftraggeber |
| D-09 | Stundenerfassung: Einheit 15 Minuten. Schnellauswahl (0,5 h, 1 h, 2 h, 3 h) **oder** exakt per Datums- und Uhrzeit-Picker. Kurzer Freitext zur Tätigkeit ist **Pflicht**. | `[DECIDED]` | Auftraggeber |
| D-10 | Ein Admin **bestätigt** erfasste Stunden. Nur bestätigte Stunden zählen für das Soll. Die Familie sieht bestätigte und offene Stunden **getrennt**. | `[DECIDED]` | Auftraggeber |
| D-11 | Admin-Übersicht: dauerhaft sichtbarer Hinweis, welche Familien im Schuljahr noch keine 10 h erreicht haben. Liste sortier- und durchsuchbar. | `[DECIDED]` | Auftraggeber |
| D-12 | Abrechnung als **PDF**: Zwischenstand des laufenden Schuljahres und abgeschlossene Schuljahre. Umfang (je Familie, Gesamtliste) siehe OPEN-02. | Format `[DECIDED]`, Umfang `[OPEN]` | Auftraggeber |
| D-13 | Rollen: Familienmitglied, **Admin** (an bestimmte Nutzer vergeben), **Master-Admin** (Notzugang auch ohne Admins; allein zuständig für Backup und Restore). | `[DECIDED]` | Auftraggeber |
| D-14 | **Mobile first**, schlichte moderne UI, optisch an die Hort-Website angelehnt. | `[DECIDED]` | Auftraggeber |
| D-15 | Backend: **Spring Boot** (Java oder Kotlin). Sprache siehe OPEN-08. | Framework `[DECIDED]`, Sprache `[OPEN]` | Auftraggeber |
| D-16 | Frontend: **SPA/PWA**. Konkret React mit TypeScript und Vite. | Ansatz `[DECIDED]`, React-Stack `[PROPOSED]` | Auftraggeber / Vorschlag |
| D-17 | Datenbank: **PostgreSQL** (relational, offen, API-fähig). | `[PROPOSED]` | Vorschlag, Begründung in Abschnitt 18 |
| D-18 | Betrieb als **Docker**-Container (Docker Compose) auf einem **IONOS VPS** (z. B. M+: 2 vCores, 4 GB RAM, 120 GB NVMe). | Docker `[DECIDED]`, VPS `[PROPOSED]` | Auftraggeber / Recherche |
| D-19 | Backup: täglich automatisch, verschlüsselt, auf mindestens zwei externe Ziele (IONOS Object Storage, Borg-Bucket, optional Google Drive oder Microsoft-Cloud). Restore nur durch Master-Admin. | Anforderung `[DECIDED]`, Werkzeuge `[PROPOSED]` | Auftraggeber / Vorschlag |
| D-20 | Authentifizierung per serverseitiger Session (Cookie `HttpOnly`, `Secure`, `SameSite=Lax`) mit CSRF-Schutz, **kein** JWT im Browser. | `[PROPOSED]` | Vorschlag |
| D-21 | Passwort-Hashing mit **Argon2id**. | `[PROPOSED]` | Vorschlag |
| D-22 | Der **Master-Admin** ist ein Nutzer mit Rolle `MASTER_ADMIN`, dessen Zugangsdaten **per Konfiguration** (Umgebungsvariablen) definiert und beim Start in die DB synchronisiert werden. Die UI kann ihn weder ändern noch löschen. Dadurch funktioniert der Zugang auch, wenn kein Admin mehr existiert. Er meldet sich **nur lokal** (Passwort, TOTP) an, **nie** per OIDC, damit der Notzugang nicht von einem externen Anbieter abhängt. | `[PROPOSED]` | Vorschlag |
| D-23 | OIDC-Anbindung über **Spring Security `oauth2Login`** (Authorization Code Flow mit PKCE), anbieterneutral per Konfiguration. Kein JavaScript-SDK des Anbieters im Browser, nur Redirect. Die Identität wird über `(iss, sub)` geführt, nie über die E-Mail allein. Automatisches Verknüpfen mit bestehenden Konten nur unter den Bedingungen aus FS-AUTH. | `[PROPOSED]` | Vorschlag |

---

## 3. Glossar

| Begriff | Bedeutung |
|---|---|
| Elternstunden | Von Eltern für den Hort unentgeltlich geleistete Arbeitszeit. |
| Schuljahr | Abrechnungszeitraum. Datensatz `school_year` mit Start- und Enddatum (Standard 1.9. bis 31.8.). |
| Familie (`family`) | Gruppe aus 1..n Kindern und 0..n Nutzern. Träger des Stundenkontos. |
| Kind (`child`) | Zentrale Stammdaten-Entität. Gehört zu genau einer Familie. |
| Nutzer (`app_user`) | Login-Konto einer Person (Elternteil oder Admin). |
| Familienmitglied | Nutzer mit Rolle `FAMILY_MEMBER`, der einer Familie zugeordnet ist. |
| Admin | Nutzer mit Rolle `ADMIN`. |
| Master-Admin | Notzugang, per Konfiguration definiert, hat alle Admin-Rechte plus Backup und Restore. |
| Soll | Pro Familie und Schuljahr geforderte Minuten (`school_year.required_minutes`, Standard 600). |
| Bestätigt / Offen | Status eines Eintrags: `CONFIRMED` (von Admin bestätigt, zählt) oder `OPEN` (erfasst, zählt noch nicht). |
| Zwischenstand | Abrechnung für ein noch laufendes Schuljahr. |
| Claim | Die bei der Registrierung angegebenen Kinderdaten (Name, Geburtsdatum), anhand derer die Zuordnung versucht wird. |
| Unzugeordnet | Nutzer ohne Familie. Sieht keine Familiendaten, bis ein Admin zuordnet. |
| OIDC | OpenID Connect. Anmeldung über einen externen Identitätsanbieter (z. B. Google). Liefert eine stabile Kennung `sub` und eine geprüfte E-Mail-Adresse (`email_verified`). |
| Identität (`user_identity`) | Verknüpfung eines Nutzers mit einem externen Konto, eindeutig über `(provider, subject)`. |
| Normalisierung | Vereinheitlichung von Namen für den Abgleich (Abschnitt 7.1). |

---

## 4. Architektur

### 4.1 Überblick

```
Browser (PWA, mobil)
   |  HTTPS
   v
Caddy  (TLS via Let's Encrypt, liefert SPA-Dateien, Reverse Proxy für /api)
   |  intern
   v
app    (Spring Boot, REST API unter /api, Sessions, PDF-Erzeugung)
   |  JDBC (nur internes Docker-Netz)
   v
db     (PostgreSQL, Volume)

backup (Sidecar-Container: pg_dump, Verschlüsselung, Upload) ---> Ziele (Abschnitt 11)
```

Alle Container laufen auf **einem** VPS per Docker Compose. Nur Caddy veröffentlicht Ports (80, 443). Die Datenbank ist **nicht** vom Host oder Internet erreichbar.

### 4.2 Technologie-Stack

| Bereich | Wahl | Status |
|---|---|---|
| Backend-Framework | Spring Boot (aktuelle stabile Linie zum Projektstart, Version vor Start prüfen) | `[DECIDED]` |
| Sprache | Java oder Kotlin, aktuelle LTS-JDK (21 oder neuer) | `[OPEN]` OPEN-08 |
| Build | Gradle oder Maven | `[OPEN]` OPEN-08, Interim: Gradle |
| Persistenz | Spring Data JPA (Hibernate) plus Flyway-Migrationen | `[PROPOSED]` |
| Datenbank | PostgreSQL, Major-Version fixieren (aktuelle stabile Major, vom Spring-Boot-Treiber unterstützt) | `[PROPOSED]` |
| Security | Spring Security (Session, CSRF, Argon2id), Bucket4j oder gleichwertig für Rate-Limits | `[PROPOSED]` |
| OIDC-Login | Spring Security `oauth2Login` (OpenID Connect, Authorization Code mit PKCE). Anbieter per Konfiguration: Google zuerst, weitere siehe OPEN-15 | Google `[DECIDED]`, Technik `[PROPOSED]` |
| API-Doku | springdoc-openapi (OpenAPI 3), Frontend-Client daraus generiert | `[PROPOSED]` |
| PDF | Serverseitig aus HTML/CSS (z. B. openhtmltopdf) oder PDFBox/OpenPDF. Wahl frei, solange Layout aus FS-PDF erreicht wird und Lizenz kompatibel ist. | `[PROPOSED]` |
| Frontend | React, TypeScript, Vite, PWA-Plugin | `[PROPOSED]` |
| Frontend-Bausteine | React Router, TanStack Query, React Hook Form plus Zod, Tailwind CSS mit zugänglichen Primitives (z. B. Radix/shadcn) | `[PROPOSED]` |
| Reverse Proxy | Caddy | `[PROPOSED]` |
| Captcha | ALTCHA (Widget lokal gebündelt, Verifikation serverseitig mit offizieller Java-Bibliothek) | `[PROPOSED]` |
| Backup | `pg_dump` plus restic (S3, SFTP, rclone-Backends), optional borgmatic für das Borg-Bucket | `[PROPOSED]` |
| Tests | JUnit 5, Testcontainers (PostgreSQL), Playwright (E2E, Mobile-Viewport), OWASP ZAP Baseline | `[PROPOSED]` |
| CI/CD | GitHub Actions: Build, Test, Scan, Image nach GHCR, Deployment per SSH | `[PROPOSED]` |

### 4.3 Repository-Layout `[PROPOSED]`

```
/backend     Spring Boot (Gradle/Maven), src/main, src/test, db/migration
/frontend    React + TypeScript + Vite
/ops         docker-compose.yml, caddy/Caddyfile, backup/, scripts/, runbooks/
/docs        Diese Spezifikation, ADRs, Lastenheft v0.2
```

### 4.4 Backend-Paketstruktur `[PROPOSED]`

Ein Paket pro fachlichem Modul, darin Schichten `web` (Controller, DTOs), `service`, `domain` (Entitäten), `repo`.

| Modul | Inhalt |
|---|---|
| `auth` | Registrierung, Login, Verifizierung, Passwort-Reset, Captcha, Rate-Limits, Master-Login |
| `family` | Familie, Kind, Einschreibung, Nutzer-Zuordnung, Claims |
| `schoolyear` | Schuljahre, Stichtage, Soll |
| `timeentry` | Einträge, Bestätigung, Validierung |
| `overview` | Familienübersicht, Admin-Übersicht |
| `report` | PDF-Abrechnung |
| `integration` | API für Drittsysteme, API-Schlüssel |
| `master` | Backup-Status, Restore-Anstoß, Admin-Rollenvergabe im Notfall |
| `audit` | Audit-Log |
| `common` | Fehlerformat, Zeit/Zeitzone, Normalisierung, Konfiguration |

Regeln: Controller enthalten keine Geschäftslogik. Entitäten verlassen die `web`-Schicht nie (nur DTOs). Alle Berechtigungsprüfungen zusätzlich auf Service-Ebene (`@PreAuthorize` oder explizit), nicht nur per URL-Muster.

---

## 5. Datenmodell

### 5.1 Prinzipien

- Primärschlüssel sind UUIDs (`gen_random_uuid()`, ab PostgreSQL 13 eingebaut). Das Kind hat zusätzlich `external_ref` als stabilen Anknüpfungspunkt für Drittsysteme (D-03, D-04).
- Alle Fremdschlüssel sind als Constraints in der DB definiert. **Kein** `ON DELETE CASCADE` auf fachlichen Daten (Ausnahme: Hilfstabellen `registration_claim`, `user_token`).
- Fachliche Regeln, die sich als Constraint ausdrücken lassen, stehen in der DB **und** in der Service-Schicht (doppelt, bewusst).
- Alle Beträge in **ganzen Minuten** (`integer`). Keine Fließkomma-Stunden.
- Löschen: `time_entry` per Soft-Delete (`deleted_at`). Kinder, Familien und Nutzer werden nicht physisch gelöscht, sondern über Anonymisierungs-Funktionen behandelt (siehe DP-05).
- Schema-Änderungen ausschließlich per Flyway (`V1__init.sql`, `V2__...`). Hibernate-DDL-Auto ist aus (`validate`).

### 5.2 Entitäten (Überblick)

```
school_year 1 ---- n child_enrollment n ---- 1 child n ---- 1 family 1 ---- n app_user
     |                                                          |
     +------------------- n time_entry n ----------------------+
                              (family_id, school_year_id, created_by -> app_user)

app_user 1 ---- n registration_claim      app_user 1 ---- n user_token
app_user 1 ---- n user_identity (OIDC)
audit_log (append-only)                   api_client (Integration, Abschnitt FS-INT)
```

### 5.3 DDL (PostgreSQL) `[PROPOSED]`

Dieses Schema ist der Startpunkt für `V1__init.sql`. Änderungen sind erlaubt, wenn Abschnitt 5.4 und die Abnahmekriterien erfüllt bleiben.

```sql
CREATE EXTENSION IF NOT EXISTS citext;

-- ---------------------------------------------------------------
-- Schuljahr (D-01). Standard 1.9. bis 31.8., Stichtage anpassbar.
-- ---------------------------------------------------------------
CREATE TABLE school_year (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    label            text        NOT NULL UNIQUE,            -- z. B. '2026/27'
    starts_on        date        NOT NULL,
    ends_on          date        NOT NULL,
    required_minutes integer     NOT NULL DEFAULT 600 CHECK (required_minutes >= 0),  -- D-02: 10 h
    closed_at        timestamptz,                            -- gesetzt = Jahr abgeschlossen, read-only (OPEN-06)
    created_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT school_year_range_ck CHECK (ends_on > starts_on),
    CONSTRAINT school_year_no_overlap
        EXCLUDE USING gist (daterange(starts_on, ends_on, '[]') WITH &&)
);

-- ---------------------------------------------------------------
-- Familie und Kind (D-03)
-- ---------------------------------------------------------------
CREATE TABLE family (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    label      text,                                         -- frei wählbare Bezeichnung für Admins, z. B. 'Familie Müller'
    note       text,                                         -- interne Admin-Notiz
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE child (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id       uuid NOT NULL REFERENCES family (id),
    first_name      text NOT NULL CHECK (btrim(first_name) <> ''),
    last_name       text NOT NULL CHECK (btrim(last_name) <> ''),
    birth_date      date NOT NULL,
    first_name_norm text NOT NULL,                           -- Normalisierung siehe 7.1, vom Service gesetzt
    last_name_norm  text NOT NULL,
    external_ref    text UNIQUE,                             -- Anknüpfung für Drittsysteme (D-04)
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX child_match_idx  ON child (last_name_norm, first_name_norm, birth_date);
CREATE INDEX child_family_idx ON child (family_id);

-- Welche Kinder im jeweiligen Schuljahr im Hort sind. Familie ist soll-pflichtig,
-- wenn mindestens ein Kind eingeschrieben ist. Siehe OPEN-04.
CREATE TABLE child_enrollment (
    child_id       uuid NOT NULL REFERENCES child (id),
    school_year_id uuid NOT NULL REFERENCES school_year (id),
    PRIMARY KEY (child_id, school_year_id)
);

-- ---------------------------------------------------------------
-- Nutzer (D-08, D-13)
-- ---------------------------------------------------------------
CREATE TYPE user_role   AS ENUM ('FAMILY_MEMBER', 'ADMIN', 'MASTER_ADMIN');
CREATE TYPE user_status AS ENUM ('PENDING_VERIFICATION', 'ACTIVE', 'LOCKED', 'DISABLED');

CREATE TABLE app_user (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email              citext      NOT NULL UNIQUE,
    password_hash      text,                                 -- Argon2id (D-21). NULL bei reinen OIDC-Konten (INV-11)
    display_name       text        NOT NULL CHECK (btrim(display_name) <> ''),
    role               user_role   NOT NULL DEFAULT 'FAMILY_MEMBER',
    status             user_status NOT NULL DEFAULT 'PENDING_VERIFICATION',
    family_id          uuid REFERENCES family (id),          -- NULL = unzugeordnet
    managed_by_config  boolean     NOT NULL DEFAULT false,   -- true nur für den Master-Admin (D-22)
    email_verified_at  timestamptz,
    failed_login_count integer     NOT NULL DEFAULT 0,
    locked_until       timestamptz,
    totp_secret_enc    bytea,                                -- verschlüsselt, siehe SEC-05 (OPEN-09)
    last_login_at      timestamptz,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT app_user_master_ck CHECK (managed_by_config = (role = 'MASTER_ADMIN')),
    CONSTRAINT app_user_master_pw_ck CHECK (role <> 'MASTER_ADMIN' OR password_hash IS NOT NULL)  -- D-22: Master nur lokal
);
CREATE INDEX app_user_family_idx ON app_user (family_id);

-- Externe Identitäten per OIDC (D-08, D-23). Schlüssel ist (provider, subject), nie die E-Mail.
CREATE TABLE user_identity (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    provider      text        NOT NULL,                      -- Registration-ID, z. B. 'google'
    issuer        text        NOT NULL,                      -- 'iss'-Claim, z. B. 'https://accounts.google.com'
    subject       text        NOT NULL,                      -- 'sub'-Claim, stabile Kennung beim Anbieter
    email_at_link text,                                      -- E-Mail zum Zeitpunkt der Verknüpfung (nur Anzeige)
    created_at    timestamptz NOT NULL DEFAULT now(),
    last_login_at timestamptz,
    CONSTRAINT user_identity_unique UNIQUE (issuer, subject)
);
CREATE INDEX user_identity_user_idx ON user_identity (user_id);

-- Bei der Registrierung angegebene Kinderdaten. Hilft Admins beim Korrigieren (D-05)
-- und liefert die Zähler für das Rate-Limit pro Zielkind (SEC-04).
CREATE TABLE registration_claim (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    child_first_name text NOT NULL,
    child_last_name  text NOT NULL,
    child_birth_date date NOT NULL,
    claim_key        text NOT NULL,                          -- SHA-256 hex über norm(Vorname)|norm(Nachname)|Geburtsdatum
    match_result     text NOT NULL
        CHECK (match_result IN ('MATCHED', 'NO_MATCH', 'AMBIGUOUS', 'THROTTLED')),
    matched_child_id uuid REFERENCES child (id),
    created_at       timestamptz NOT NULL DEFAULT now(),
    resolved_at      timestamptz                             -- gesetzt, sobald ein Admin zugeordnet hat
);
CREATE INDEX registration_claim_key_idx ON registration_claim (claim_key, created_at);

CREATE TYPE token_purpose AS ENUM ('EMAIL_VERIFICATION', 'PASSWORD_RESET');

CREATE TABLE user_token (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    purpose    token_purpose NOT NULL,
    token_hash text          NOT NULL UNIQUE,                -- SHA-256 hex des Zufallstokens, Klartext wird nie gespeichert
    expires_at timestamptz   NOT NULL,
    used_at    timestamptz,
    created_at timestamptz   NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------
-- Elternstunden (D-09, D-10)
-- ---------------------------------------------------------------
CREATE TYPE entry_status AS ENUM ('OPEN', 'CONFIRMED');

CREATE TABLE time_entry (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id        uuid         NOT NULL REFERENCES family (id),
    school_year_id   uuid         NOT NULL REFERENCES school_year (id),
    created_by       uuid         NOT NULL REFERENCES app_user (id),
    work_date        date         NOT NULL,
    start_time       time,                                   -- NULL bei Schnellauswahl, gesetzt bei exakter Erfassung
    duration_minutes integer      NOT NULL,
    description      text         NOT NULL,
    status           entry_status NOT NULL DEFAULT 'OPEN',
    confirmed_by     uuid REFERENCES app_user (id),
    confirmed_at     timestamptz,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    deleted_at       timestamptz,
    version          integer      NOT NULL DEFAULT 0,        -- optimistisches Locking (@Version)
    -- 15-Minuten-Raster (D-09). Obergrenze 720 min ist ein Vorschlag, siehe OPEN-10.
    CONSTRAINT time_entry_duration_ck
        CHECK (duration_minutes > 0 AND duration_minutes % 15 = 0 AND duration_minutes <= 720),
    CONSTRAINT time_entry_description_ck
        CHECK (char_length(btrim(description)) BETWEEN 3 AND 500),
    CONSTRAINT time_entry_confirm_ck
        CHECK ((status = 'CONFIRMED') = (confirmed_at IS NOT NULL AND confirmed_by IS NOT NULL))
);
CREATE INDEX time_entry_family_year_idx ON time_entry (family_id, school_year_id) WHERE deleted_at IS NULL;
CREATE INDEX time_entry_open_idx        ON time_entry (created_at)                WHERE deleted_at IS NULL AND status = 'OPEN';

-- ---------------------------------------------------------------
-- Audit-Log (append-only, siehe SEC-11)
-- ---------------------------------------------------------------
CREATE TABLE audit_log (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at   timestamptz NOT NULL DEFAULT now(),
    actor_user_id uuid REFERENCES app_user (id),
    actor_kind    text        NOT NULL CHECK (actor_kind IN ('USER', 'SYSTEM', 'INTEGRATION')),
    action        text        NOT NULL,                      -- z. B. 'ENTRY_CONFIRMED', 'USER_ASSIGNED_TO_FAMILY'
    entity_type   text        NOT NULL,
    entity_id     text,
    details       jsonb       NOT NULL DEFAULT '{}'::jsonb   -- nur IDs und technische Werte, keine Freitexte (SEC-12)
);
CREATE INDEX audit_log_entity_idx ON audit_log (entity_type, entity_id, occurred_at DESC);

-- ---------------------------------------------------------------
-- Integration (FS-INT)
-- ---------------------------------------------------------------
CREATE TABLE api_client (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name         text        NOT NULL,
    purpose      text        NOT NULL,                       -- dokumentierter Zweck der Anbindung (Datenschutz)
    key_hash     text        NOT NULL UNIQUE,                -- SHA-256 hex des Schlüssels, Klartext nur einmal bei Erzeugung sichtbar
    scopes       text[]      NOT NULL,                       -- z. B. {'children:read','families:read'}
    created_by   uuid        NOT NULL REFERENCES app_user (id),
    created_at   timestamptz NOT NULL DEFAULT now(),
    last_used_at timestamptz,
    revoked_at   timestamptz
);
```

Sessions: **Spring Session JDBC** legt seine Tabellen (`spring_session`, `spring_session_attributes`) selbst an bzw. per Flyway-Migration. Dadurch überleben Sessions einen Neustart `[PROPOSED]`.

### 5.4 Invarianten

| ID | Regel | Durchsetzung |
|---|---|---|
| INV-01 | `time_entry.school_year_id` MUSS das Schuljahr sein, dessen `[starts_on, ends_on]` das `work_date` enthält. Liegt das Datum in keinem Schuljahr, wird der Eintrag abgelehnt. | Service, Test |
| INV-02 | `time_entry.family_id` MUSS die Familie des erfassenden Nutzers sein (bei Rolle `FAMILY_MEMBER`). Admins dürfen für jede Familie erfassen oder korrigieren. | Service |
| INV-03 | `work_date` DARF NICHT in der Zukunft liegen (Zeitzone Europe/Berlin). | Service, `[PROPOSED]` OPEN-10 |
| INV-04 | Einträge in einem abgeschlossenen Schuljahr (`closed_at` gesetzt) sind unveränderlich. | Service, OPEN-06 |
| INV-05 | Ein `CONFIRMED`-Eintrag DARF von Familienmitgliedern weder geändert noch gelöscht werden. | Service |
| INV-06 | Ein `OPEN`-Eintrag DARF von Familienmitgliedern der **eigenen** Familie geändert oder (soft) gelöscht werden. | Service, `[PROPOSED]` OPEN-11 |
| INV-07 | Ändert ein Admin einen `CONFIRMED`-Eintrag inhaltlich (Dauer, Datum, Text), wird er wieder `OPEN` und muss neu bestätigt werden. Ein Audit-Eintrag entsteht. | Service, `[PROPOSED]` OPEN-11 |
| INV-08 | Es gibt höchstens einen Nutzer mit `managed_by_config = true`. | DB-Constraint plus Start-Synchronisation |
| INV-09 | Schuljahre überlappen sich nicht. Beim Ändern der Stichtage DARF kein bestehender Eintrag außerhalb des neuen Bereichs landen (sonst Ablehnung mit klarer Fehlermeldung). | DB-Constraint, Service |
| INV-10 | Nutzer ohne Familie (`family_id IS NULL`, Rolle `FAMILY_MEMBER`) sehen und erfassen **keine** Familiendaten. | Service, Test |
| INV-11 | Jedes Konto hat mindestens eine Anmeldemöglichkeit: `password_hash` gesetzt **oder** mindestens eine `user_identity`. Die letzte Anmeldemöglichkeit darf nicht entfernt werden. | Service, Test |
| INV-12 | Ein `MASTER_ADMIN` hat **keine** `user_identity` (kein OIDC-Login). | Service, Test |

### 5.5 Schuljahr-Logik

1. Standard-Stichtage: 1.9. bis 31.8. (D-01). Label z. B. `2026/27`.
2. Ein Schuljahr wird einem Datum per `starts_on <= date <= ends_on` zugeordnet.
3. Ein System-Job (täglich, `[PROPOSED]`) legt das **nächste** Schuljahr mit Standard-Stichtagen an, sobald es 90 Tage vor Beginn noch fehlt. Admins können Stichtage und `required_minutes` anpassen, solange INV-09 erfüllt ist.
4. Das **aktuelle** Schuljahr ist das, das heute (Europe/Berlin) enthält. Liegt heute in einer Lücke zwischen zwei Schuljahren, gilt das zuletzt beendete als "aktuell" für die Anzeige, und Erfassung ist nicht möglich (Hinweis in der UI).
5. Das Soll steht pro Schuljahr (`required_minutes`, Standard 600), nicht hart im Code (D-02).

### 5.6 Referenzabfrage: Admin-Übersicht (D-11)

Wichtig: Die Einschreibungs-Prüfung MUSS über `EXISTS` laufen, nicht über einen Join auf `child`, sonst werden die Minuten bei Geschwisterkindern vervielfacht.

```sql
-- $1 = school_year_id
SELECT f.id                                   AS family_id,
       f.label                                AS family_label,
       sy.required_minutes,
       COALESCE(te.confirmed_minutes, 0)      AS confirmed_minutes,
       COALESCE(te.open_minutes, 0)           AS open_minutes,
       GREATEST(sy.required_minutes - COALESCE(te.confirmed_minutes, 0), 0) AS missing_minutes
FROM school_year sy
JOIN family f
  ON EXISTS (SELECT 1
             FROM child c
             JOIN child_enrollment ce ON ce.child_id = c.id
             WHERE c.family_id = f.id
               AND ce.school_year_id = sy.id)
LEFT JOIN (
    SELECT family_id,
           SUM(duration_minutes) FILTER (WHERE status = 'CONFIRMED') AS confirmed_minutes,
           SUM(duration_minutes) FILTER (WHERE status = 'OPEN')      AS open_minutes
    FROM time_entry
    WHERE school_year_id = $1
      AND deleted_at IS NULL
    GROUP BY family_id
) te ON te.family_id = f.id
WHERE sy.id = $1;
```

Suche, Sortierung, Filter und Paging werden serverseitig auf dieser Abfrage aufgesetzt (FS-ADMOV).

---

## 6. Rollen und Rechte

Rollen: `FAMILY_MEMBER` (zugeordnet oder unzugeordnet), `ADMIN`, `MASTER_ADMIN`. `MASTER_ADMIN` hat **alle** Rechte von `ADMIN`.

| Aktion | Unzugeordnet | FAMILY_MEMBER | ADMIN | MASTER_ADMIN |
|---|---|---|---|---|
| Registrieren, Login, Passwort ändern/zurücksetzen | ja | ja | ja | ja |
| Eigene Familienübersicht, Einträge und Mitgliederstatus sehen | nein (nur Hinweis "Zuordnung ausstehend") | ja | ja (falls selbst Familie zugeordnet) | ja |
| Einträge für **eigene** Familie erfassen | nein | ja | ja | ja |
| Eigene `OPEN`-Einträge ändern oder löschen | nein | ja | ja | ja |
| `CONFIRMED`-Einträge ändern oder löschen | nein | nein | ja (INV-07) | ja |
| Alle Familien und Kinder sehen, suchen, sortieren | nein | nein | ja | ja |
| Kinder, Familien, Einschreibungen anlegen und ändern | nein | nein | ja | ja |
| Nutzer einer Familie zuordnen, Zuordnung korrigieren (F5) | nein | nein | ja | ja |
| Einträge bestätigen (einzeln und gesammelt) | nein | nein | ja | ja |
| Schuljahre verwalten (Stichtage, Soll, abschließen) | nein | nein | ja | ja |
| Abrechnungs-PDF erzeugen | nein | nein | ja | ja |
| Admin-Rolle vergeben oder entziehen | nein | nein | ja `[PROPOSED]` | ja |
| Audit-Log lesen | nein | nein | ja `[PROPOSED]` | ja |
| API-Clients für Drittsysteme verwalten | nein | nein | nein | ja `[PROPOSED]` |
| Backup-Status sehen, Backup anstoßen, Restore durchführen | nein | nein | nein | ja `[DECIDED]` |
| Master-Admin-Konto ändern oder löschen | nein | nein | nein | nein (nur per Konfiguration) |

Regeln:

- Jede Endpunkt-Methode MUSS ihre Rolle **explizit** deklarieren. Standard ist "verweigert" (Deny by default).
- Datenzugriffe von `FAMILY_MEMBER` MÜSSEN immer auf `family_id` des angemeldeten Nutzers gefiltert werden. Eine ID aus dem Request darf nie allein zur Autorisierung genügen (Schutz vor IDOR, siehe SEC-08).

---

## 7. Funktionale Spezifikation

### 7.0 Zuordnung zum Lastenheft v0.2

| Lastenheft | Thema | Spezifikation |
|---|---|---|
| F1 Kinderliste | Stammdaten Kinder, Familien | FS-ADM (7.7) |
| F2 Schuljahr | Schuljahre, Stichtage, Soll | 5.5, FS-ADM (7.7) |
| F3 Registrierung | Selbstregistrierung (lokal und OIDC), Zuordnung, Bot-Schutz | FS-REG (7.1), FS-AUTH (7.2) |
| F4 Familienstatus | Hinweis auf fehlenden Partner | FS-FAM (7.3) |
| F5 Korrektur | Admin korrigiert Zuordnung | FS-ADM (7.7), FS-REG (7.1) |
| F6 Stunden erfassen | Schnellauswahl, exakt, Freitext | FS-ENTRY (7.4) |
| F7 Bestätigung | Admin bestätigt | FS-CONF (7.5) |
| F8 Familienübersicht | Landing Page nach Login | FS-FAM (7.3) |
| F9 Admin-Übersicht | Hinweis, Sortieren, Suchen | FS-ADMOV (7.6) |
| F10 Abrechnung | PDF | FS-PDF (7.8) |
| N1..N6 | UI, Daten, Datenschutz, Betrieb, Backup, Bot-Schutz | Abschnitte 9 bis 14 |

---

### 7.1 FS-REG: Registrierung und Familienzuordnung (F3, F4, F5)

#### 7.1.1 Ziel

Eltern registrieren sich selbst, ohne dass ein Admin vorab Codes verteilt oder Konten freigibt (D-05). Die Zuordnung zur Familie erfolgt über die **Angaben zum Kind** (Vorname, Nachname, Geburtsdatum). Die Kinderliste wird dabei **nie** sichtbar. Schlägt die Zuordnung fehl (Tippfehler), korrigiert ein Admin nachträglich.

Es gibt zwei Registrierungswege, die dasselbe Zuordnungsverfahren nutzen:

- **Weg A, lokal:** E-Mail + Passwort, mit Captcha und E-Mail-Bestätigung.
- **Weg B, OIDC:** Anmeldung mit Google (weitere Anbieter siehe OPEN-15). Die E-Mail-Adresse ist beim Anbieter bereits bestätigt.

#### 7.1.2 Eingaben für die Zuordnung (Claim)

| Feld | Regel |
|---|---|
| `child.firstName` | Pflicht, 1 bis 80 Zeichen |
| `child.lastName` | Pflicht, 1 bis 80 Zeichen |
| `child.birthDate` | Pflicht, gültiges Datum, nicht in der Zukunft, nicht älter als 25 Jahre |

Zusätzlich bei Weg A: `displayName` (1 bis 80), `email` (gültig, höchstens 254 Zeichen), `password` (SEC-03), Captcha-Nachweis (SEC-04), Bestätigung der Datenschutzhinweise (DP-03).
Bei Weg B: `displayName` wird aus dem `name`-Claim vorbelegt und ist editierbar. Kein Passwort.

#### 7.1.3 Normalisierung für den Abgleich

Funktion `normalizeName(String): String`, deterministisch und ohne Locale-Abhängigkeit `[PROPOSED]`:

1. Unicode-Normalform NFKC.
2. Führende und folgende Leerzeichen entfernen, innere Leerraum-Folgen zu einem Leerzeichen.
3. Bindestriche und Apostrophe (`-`, `‐`, `–`, `'`, `’`, `` ` ``) durch ein Leerzeichen ersetzen.
4. In Kleinbuchstaben umwandeln (Case Folding, `ß` wird `ss`).
5. `ä`, `ö`, `ü` zu `ae`, `oe`, `ue` umschreiben.
6. Übrige Diakritika entfernen (NFD, kombinierende Zeichen löschen).

Testvektoren (MÜSSEN als Unit-Test existieren):

| Eingabe | Ergebnis |
|---|---|
| `Müller` | `mueller` |
| `  MÜLLER ` | `mueller` |
| `Mueller` | `mueller` |
| `Anna-Lena` | `anna lena` |
| `Straße` | `strasse` |
| `O'Brien` | `o brien` |
| `Çağla` | `cagla` |

Der Abgleich ist **exakte Gleichheit** auf `(first_name_norm, last_name_norm, birth_date)`. Teilübereinstimmungen (z. B. nur ein Vorname von zwei) führen **nicht** zu einem Treffer, um Fehlzuordnungen zu vermeiden. Admins korrigieren solche Fälle.

`claim_key = SHA-256-hex( first_norm + "|" + last_norm + "|" + birth_date(ISO) )`.

#### 7.1.4 Ablauf Weg A (lokal)

1. Client holt eine Captcha-Challenge (`GET /api/v1/auth/captcha`), löst sie im Browser und sendet das Formular mit Nachweis (`POST /api/v1/auth/register`).
2. Server prüft Captcha, Eingabevalidierung und Rate-Limits (SEC-04). Bei Überschreitung: `429` mit neutraler Meldung.
3. Server antwortet **immer identisch** mit `202 Accepted` und der Meldung "Wir haben dir eine E-Mail geschickt. Bitte bestätige deine Adresse.", unabhängig davon, ob die E-Mail schon existiert oder ob ein Kind passt (kein Orakel).
   - E-Mail ist **neu**: Konto anlegen (`PENDING_VERIFICATION`, Rolle `FAMILY_MEMBER`, `family_id` NULL), `registration_claim` speichern, Bestätigungs-Mail senden.
   - E-Mail existiert **bereits**: kein neues Konto. Stattdessen Hinweis-Mail an die bestehende Adresse ("Jemand hat sich mit deiner Adresse registriert. Du warst das? Dann melde dich an oder setze dein Passwort zurück.").
4. Beim Anlegen des Claims wird der Abgleich berechnet:
   - Zähle `registration_claim` mit gleichem `claim_key` in den letzten 24 Stunden. Sind es mehr als `max-claims-per-target` (Standard 5, konfigurierbar), ist das Ergebnis `THROTTLED`, **ohne** Kind-Lookup.
   - Sonst: Kinder mit gleichem Namen (normalisiert) und Geburtsdatum suchen. Genau eine Familie: `MATCHED` (mit `matched_child_id`). Keine: `NO_MATCH`. Mehrere verschiedene Familien: `AMBIGUOUS`.
5. Der Nutzer bestätigt die E-Mail (`POST /api/v1/auth/verify-email`, Token aus der Mail). Status wird `ACTIVE`, `email_verified_at` gesetzt.
6. **Erst jetzt** wird bei `MATCHED` die Familie zugeordnet (`app_user.family_id`), `claim.resolved_at` gesetzt, Audit `USER_ASSIGNED_TO_FAMILY` (`method=AUTO_MATCH`) geschrieben und die Benachrichtigung aus 7.1.6 versendet.

#### 7.1.5 Ablauf Weg B (OIDC)

1. Nutzer wählt "Mit Google anmelden". Der Browser wird per Redirect zum Anbieter geleitet (Authorization Code mit PKCE, `state`, `nonce`).
2. Callback: Der Server prüft Signatur, `iss`, `aud`, `exp`, `nonce` und verlangt `email_verified = true` (SEC-06). Fehlt die Bestätigung: Abbruch mit Meldung "Dein Google-Konto hat keine bestätigte E-Mail-Adresse."
3. Entscheidung nach Fall:

| Fall | Verhalten |
|---|---|
| Identität `(iss, sub)` existiert | Anmelden (Status `ACTIVE`). Fertig. |
| Kein Konto mit dieser E-Mail | Pending-Zustand in der Server-Session halten (`iss`, `sub`, E-Mail, Name). **Noch kein** DB-Konto. Weiter mit Schritt 4. |
| Konto mit dieser E-Mail existiert, `email_verified_at` gesetzt, Rolle nicht `MASTER_ADMIN` | Identität verknüpfen, anmelden. Audit `IDENTITY_LINKED`. |
| Konto mit dieser E-Mail existiert, **nicht** bestätigt (`PENDING_VERIFICATION`) | Identität verknüpfen, E-Mail als bestätigt markieren **und `password_hash` auf NULL setzen** (Schutz vor Vorab-Übernahme: Wer die Adresse nur eingetippt hat, verliert das Passwort). Anmelden. |
| Konto ist `MASTER_ADMIN` | Ablehnen: "Dieses Konto kann nur lokal angemeldet werden." (INV-12) |
| Konto ist `LOCKED` oder `DISABLED` | Ablehnen mit neutraler Meldung. |

4. "Noch ein Schritt": Das Kind-Formular (7.1.2) wird angezeigt. Nach dem Absenden legt der Server Konto (`ACTIVE`, `email_verified_at` gesetzt, `password_hash` NULL), `user_identity` und `registration_claim` an und berechnet den Abgleich wie in 7.1.4 Schritt 4. Bei `MATCHED` wird sofort zugeordnet (7.1.4 Schritt 6).
5. Captcha: Auf Weg B **kein** Captcha `[PROPOSED]`, weil der Anbieter-Login bereits eine Hürde ist. Die Rate-Limits pro Zielkind (SEC-04) gelten unverändert.

#### 7.1.6 Benachrichtigung bei Zuordnung

Wird ein Nutzer automatisch einer Familie zugeordnet, die schon aktive Mitglieder hat, erhalten diese eine E-Mail: "{displayName} wurde deiner Familie zugeordnet. Falls das nicht stimmt, wende dich bitte an das Hort-Team." Zusätzlich zeigt die Admin-Übersicht (FS-ADMOV) ein Widget "Neue automatische Zuordnungen" (letzte 14 Tage). Eine Selbstkorrektur durch Familienmitglieder gibt es zunächst nicht (OPEN-12).

#### 7.1.7 Unzugeordnete Nutzer

- Nutzer ohne Familie sehen nach dem Login nur den Hinweis "Zuordnung ausstehend. Wir konnten deine Angaben keinem Kind zuordnen. Ein Admin prüft das." sowie ihre eingegebenen Kinderdaten.
- Sie KÖNNEN die Kinderdaten **korrigieren**, höchstens 3-mal pro Konto `[PROPOSED]`. Jede Korrektur erzeugt einen neuen `registration_claim` und unterliegt dem Rate-Limit pro Zielkind.
- Admins sehen alle unzugeordneten Nutzer mit Claim-Daten (FS-ADMOV Widget "Unzugeordnete Nutzer") und erhalten **Kandidatenvorschläge**: Kinder mit gleichem Geburtsdatum oder ähnlichem Namen (Levenshtein-Distanz der normalisierten Nachnamen höchstens 2, per Erweiterung `fuzzystrmatch` in eigener Migration). Zuordnung per Klick (F5). Danach `resolved_at` setzen und Audit `USER_ASSIGNED_TO_FAMILY` (`method=ADMIN`).

#### 7.1.8 Partnerhinweis (F4)

Siehe FS-FAM. Für die Zuordnung des zweiten Elternteils gilt derselbe Ablauf: Er gibt dieselben Kinderdaten an und landet in derselben Familie.

---

### 7.2 FS-AUTH: Anmeldung, Sitzung, Passwort, OIDC

#### Lokales Login (D-08)

- `POST /api/v1/auth/login` mit E-Mail und Passwort (und TOTP-Code, falls für das Konto aktiv).
- Fehlermeldung immer neutral: "E-Mail oder Passwort stimmt nicht." Auch bei unbestätigtem, gesperrtem oder unbekanntem Konto. Bei unbekannter E-Mail wird ein Dummy-Hash geprüft (konstante Antwortzeit).
- Nur `ACTIVE`-Konten können sich anmelden. `PENDING_VERIFICATION`-Nutzer können eine neue Bestätigungs-Mail anfordern (rate-limitiert, neutrale Antwort).
- Konto-Sperre nach Fehlversuchen: ab 5 Fehlversuchen `locked_until` mit steigender Dauer (1, 5, 15, 60 Minuten), Zähler wird bei Erfolg zurückgesetzt. Zusätzlich IP-Rate-Limit (SEC-04).
- Konten, die **nur** über OIDC existieren (`password_hash` NULL), können sich lokal erst anmelden, wenn sie per "Passwort festlegen" (Reset-Mail) ein Passwort gesetzt haben `[PROPOSED]`.

#### OIDC-Login (D-08, D-23)

- Einstieg: `GET /oauth2/authorization/{registrationId}` (Spring Security Standard). Callback: `/login/oauth2/code/{registrationId}`.
- Ablauf und Fallunterscheidung: 7.1.5.
- **Anbieterkonfiguration** (Beispiel Google, Werte aus Umgebung, siehe 12.4):

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: openid,profile,email
        provider:
          google:
            issuer-uri: https://accounts.google.com
```

- Weitere Anbieter werden durch **zusätzliche Konfigurationsblöcke** ergänzt (generischer `issuer-uri`), ohne Codeänderung. Welche Anbieter aktiv sind: OPEN-15. Interim: nur Google.
- Der Server MUSS `iss`, `aud`, Signatur, `exp` und `nonce` validieren (Spring tut das für OIDC; Test erforderlich) und `email_verified = true` verlangen.
- **Kein JavaScript-SDK** des Anbieters im Frontend. Der Button ist ein normaler Link auf den Einstiegs-Endpunkt (DP-04). Der Button MUSS die Branding-Vorgaben des Anbieters erfüllen (UI-06).
- Die Anmeldung wird **nach** dem Callback zu einer normalen Server-Session (Cookie), identisch zum lokalen Login.
- **Profil:** Eingeloggte Nutzer KÖNNEN weitere Anbieter verknüpfen und Verknüpfungen entfernen, sofern INV-11 eingehalten wird. Verknüpfen verlangt eine erneute Anmeldung (frisches Login nicht älter als 5 Minuten).
- `MASTER_ADMIN` kann OIDC **nicht** nutzen (D-22, INV-12).
- ADMIN-Konten dürfen sich per OIDC anmelden. Die zweite Faktor-Frage ist in OPEN-09 offen.

#### Sitzung (D-20)

| Rolle | Leerlauf-Timeout | Absolute Dauer |
|---|---|---|
| `FAMILY_MEMBER` | 30 Tage (gleitend, mobile Bequemlichkeit) | 90 Tage |
| `ADMIN` | 30 Minuten | 8 Stunden |
| `MASTER_ADMIN` | 15 Minuten | 4 Stunden |

Alle Werte `[PROPOSED]` und konfigurierbar. Sessions in Spring Session JDBC. Bei Passwortänderung oder -reset werden alle anderen Sessions des Nutzers beendet.

#### Passwort zurücksetzen

- `POST /api/v1/auth/password-reset/request` (Captcha, Rate-Limit), Antwort immer `202` mit neutraler Meldung.
- Token: 32 Byte Zufall (CSPRNG), base64url, gültig 1 Stunde, einmalig. In der DB nur der SHA-256-Hash (`user_token`).
- `POST /api/v1/auth/password-reset/confirm` mit Token und neuem Passwort (SEC-03). Beendet alle Sessions.

#### E-Mail-Bestätigung

- Token wie oben, gültig 24 Stunden. Link: `https://{domain}/verify-email?token=...`.

---

### 7.3 FS-FAM: Familienübersicht und Familienstatus (F4, F8)

Die Familienübersicht ist die **Startseite nach dem Login** für zugeordnete Familienmitglieder.

#### Datenvertrag `GET /api/v1/me/family/overview?schoolYearId={optional}`

```json
{
  "schoolYear": { "id": "…", "label": "2026/27", "startsOn": "2026-09-01", "endsOn": "2027-08-31", "closed": false },
  "requiredMinutes": 600,
  "confirmedMinutes": 270,
  "openMinutes": 60,
  "remainingMinutes": 330,
  "fulfilled": false,
  "children": [ { "firstName": "…" } ],
  "members": [ { "displayName": "…", "isCurrentUser": true } ],
  "partnerHint": true,
  "entries": [
    {
      "id": "…", "workDate": "2026-10-14", "startTime": null, "durationMinutes": 120,
      "description": "…", "status": "CONFIRMED", "createdByDisplayName": "…",
      "canEdit": false, "canDelete": false
    }
  ]
}
```

#### Regeln

1. `remainingMinutes = max(requiredMinutes - confirmedMinutes, 0)`. `fulfilled = confirmedMinutes >= requiredMinutes`. **Nur bestätigte** Minuten zählen (D-10). Offene Minuten werden **getrennt** ausgewiesen.
2. Ohne `schoolYearId` wird das aktuelle Schuljahr (5.5) geliefert. Frühere Schuljahre sind abrufbar, schreibgeschützt.
3. Einträge: neueste zuerst (`work_date` absteigend, dann `created_at`). Gelöschte (Soft-Delete) Einträge erscheinen nicht.
4. Dauer-Darstellung im Client: `45 min`, `1 h`, `2 h 15 min`. Nie Dezimalstunden.
5. `canEdit` und `canDelete` sind nur bei `OPEN` und eigener Familie wahr (INV-05, INV-06) und im offenen Schuljahr (INV-04).
6. **Partnerhinweis (F4):** `partnerHint = true`, wenn die Familie genau **ein** aktives Mitglied hat. Der Client zeigt: "Dein Partner oder deine Partnerin ist noch nicht verknüpft. Bitte registriere dich mit denselben Angaben zu {Vorname des Kindes}." Der Hinweis ist dauerhaft ausblendbar (Einstellung im Browser-Speicher, `[PROPOSED]`), z. B. für Alleinerziehende.
7. Familiendaten, die der Nutzer sieht: Vornamen der Kinder (keine Nachnamen, kein Geburtsdatum), Anzeigenamen der Mitglieder (keine E-Mail-Adressen).
8. Nutzer ohne Familie erhalten `403` mit Code `FAMILY_ASSIGNMENT_PENDING`; der Client zeigt den Wartescreen (7.1.7).

---

### 7.4 FS-ENTRY: Stunden erfassen (F6)

#### Eingabemodi (D-09)

| Modus | Bedienung | Gespeichert |
|---|---|---|
| **Schnell** | Chips `30 min`, `1 h`, `2 h`, `3 h`. Datum standardmäßig heute, änderbar. | `duration_minutes` = 30/60/120/180, `start_time` NULL |
| **Exakt** | Datum (Picker) + Startzeit (Picker) + Dauer per Stepper in 15-Minuten-Schritten (mindestens 15, höchstens 720). | `start_time` gesetzt |

Gemeinsam: **Freitext "Was hast du gemacht?"** ist Pflicht, 3 bis 500 Zeichen nach Trim (D-09). Ein Chip "Andere Dauer" wechselt in den exakten Modus.

#### Serverseitige Regeln (alle MÜSSEN erzwungen werden, nie nur im Client)

1. `duration_minutes % 15 == 0`, `15 <= duration <= 720` (OPEN-10 für Obergrenze).
2. `description` getrimmt, Länge 3 bis 500. HTML wird nicht interpretiert, Ausgabe stets escaped.
3. `work_date` in einem existierenden, nicht abgeschlossenen Schuljahr (INV-01, INV-04), nicht in der Zukunft (INV-03).
4. Familie des Nutzers (INV-02). `created_by` ist der angemeldete Nutzer.
5. **Duplikatschutz:** Ein inhaltlich identischer Eintrag (gleiche Familie, Datum, Startzeit, Dauer, Beschreibung) innerhalb von 2 Minuten wird mit `409 DUPLICATE_ENTRY` abgelehnt `[PROPOSED]`.
6. Rate-Limit: höchstens 60 Einträge pro Nutzer und Stunde `[PROPOSED]`.
7. Neuer Eintrag hat Status `OPEN`.

#### Ändern und Löschen

- Familienmitglieder: nur eigene Familie, nur `OPEN` (INV-05, INV-06). Löschen ist Soft-Delete.
- Admins: alle Einträge, siehe INV-07.
- Jede Änderung und Löschung erzeugt einen Audit-Eintrag.
- Änderungen nutzen optimistisches Locking (`version`). Konflikt: `409 CONCURRENT_MODIFICATION`.

#### UX-Ziel

Ein Standardeintrag ("1 h, heute") MUSS in höchstens **4 Interaktionen** möglich sein (Öffnen, Chip, Text, Absenden). Siehe UI-04.

---

### 7.5 FS-CONF: Bestätigung durch Admins (F7)

1. Admin-Ansicht "Zu bestätigen": alle `OPEN`-Einträge, gruppierbar nach Familie, filterbar nach Familie und Zeitraum, sortiert nach Erfassungsdatum (älteste zuerst).
2. Aktionen: einzeln bestätigen, alle offenen Einträge einer Familie bestätigen, Mehrfachauswahl bestätigen.
3. Bestätigen setzt `status = CONFIRMED`, `confirmed_by`, `confirmed_at` und schreibt Audit `ENTRY_CONFIRMED`. Die Operation ist **idempotent** (bereits bestätigte Einträge bleiben unverändert, kein Fehler).
4. Sammelbestätigung als ein SQL-Update: `UPDATE time_entry SET … WHERE id = ANY(:ids) AND status = 'OPEN' AND deleted_at IS NULL`.
5. Admins können Einträge vor der Bestätigung **bearbeiten** oder **löschen** (z. B. falsche Dauer). Ein Ablehnen mit Begründung und Rückfrage an die Familie gibt es zunächst nicht (OPEN-11).
6. Zähler offener Einträge erscheint als Badge in der Admin-Navigation.
7. In abgeschlossenen Schuljahren ist Bestätigen gesperrt (INV-04).

---

### 7.6 FS-ADMOV: Admin-Übersicht (F9, D-11)

#### Hinweisleiste

- Auf **allen** Admin-Seiten dauerhaft sichtbar: "{n} von {m} Familien haben im Schuljahr {label} noch keine {Soll} bestätigt." Klick öffnet die gefilterte Liste (`onlyMissing=true`).
- Schuljahr: standardmäßig das aktuelle, umschaltbar auf andere (auch abgeschlossene).
- `m` sind die soll-pflichtigen Familien (5.6), `n` die mit `missing_minutes > 0`.

#### Liste `GET /api/v1/admin/overview`

Query-Parameter:

| Parameter | Bedeutung |
|---|---|
| `schoolYearId` | Schuljahr (Standard: aktuelles) |
| `q` | Suche, mindestens 2 Zeichen |
| `onlyMissing` | `true` = nur Familien mit fehlenden Stunden |
| `sort` | `field,direction`, erlaubte Felder siehe unten |
| `page`, `size` | Standard `0`, `50`. Höchstwert `size` = 200 |

Spalten und erlaubte `sort`-Felder (**Whitelist**, niemals Roh-SQL aus dem Request, SEC-07): `familyLabel`, `children`, `memberCount`, `confirmedMinutes`, `openMinutes`, `missingMinutes`.
Standardsortierung: `missingMinutes,desc`, dann `familyLabel,asc`.

**Suche (`q`):** Groß-/Kleinschreibung und Diakritika egal (gleiche Normalisierung wie 7.1.3). Durchsucht Familienbezeichnung, Vor- und Nachnamen der Kinder, Anzeigenamen und E-Mail-Adressen der Mitglieder.

**Zeile:** Familie (Bezeichnung oder abgeleitet aus Kindernachnamen), Kinder (Namen), Mitgliederzahl, bestätigt, offen, fehlt, Statuschip: `Erfüllt` oder `Fehlt noch {Dauer}` (zusätzlich Hinweis "{offen} warten auf Bestätigung", falls `openMinutes > 0`).

Ein Klick auf eine Zeile öffnet die Familiendetailansicht (Kinder, Mitglieder, Einträge mit Bestätigungs- und Bearbeitungsaktionen).

#### Widgets auf der Admin-Startseite

1. **Zu bestätigen** (Anzahl offener Einträge, Link zu FS-CONF).
2. **Unzugeordnete Nutzer** (Anzahl, Liste mit Claim-Daten und Kandidaten, 7.1.7).
3. **Neue automatische Zuordnungen** (letzte 14 Tage, 7.1.6).

---

### 7.7 FS-ADM: Stammdatenverwaltung (F1, F2, F5)

Alle Funktionen nur für `ADMIN` und `MASTER_ADMIN`, jede Änderung mit Audit-Eintrag.

| Bereich | Funktionen |
|---|---|
| Kinder | Anlegen (Vorname, Nachname, Geburtsdatum, Familie oder neue Familie, optional `external_ref`), bearbeiten, Familie wechseln. **Kein physisches Löschen** (DP-05). `*_norm`-Felder werden bei jeder Änderung neu berechnet. |
| Einschreibung | Pro Schuljahr festlegen, welche Kinder eingeschrieben sind. Funktion "Einschreibung aus Vorjahr übernehmen" `[PROPOSED]`. Siehe OPEN-04. |
| Familien | Anlegen, Bezeichnung und Notiz bearbeiten. Zusammenführen zweier Familien (Kinder, Nutzer, Einträge verschieben) ist **Phase 2** `[PROPOSED]`. |
| Nutzer | Suchen, Zuordnung zu Familie setzen oder entfernen (F5), Rolle setzen (D-13), deaktivieren, Passwort-Reset-Mail auslösen, alle Sessions beenden. |
| Schuljahre | Anlegen, Label, Stichtage und `required_minutes` ändern (INV-09), abschließen und wieder öffnen (OPEN-06). |
| Import | CSV-Import für Kinder `[PROPOSED]`, siehe unten. Quelle der Kinderliste: OPEN-03. |

#### CSV-Import Kinder `[PROPOSED]`

- Spalten: `first_name;last_name;birth_date;family_key;external_ref` (Trennzeichen `;`, UTF-8, Datum `YYYY-MM-DD` oder `DD.MM.YYYY`). Kinder mit gleichem `family_key` bilden eine Familie.
- **Vorschau (Dry-Run)** vor dem Schreiben: Anzahl neu, geändert, unverändert, fehlerhafte Zeilen mit Zeilennummer.
- Idempotent: Zuordnung bestehender Kinder über `external_ref`, sonst über normalisierten Namen plus Geburtsdatum.
- Der Import läuft in **einer Transaktion**. Bei Fehlern: alles oder nichts.
- Datei wird nicht gespeichert. Größenlimit 1 MB.

---

### 7.8 FS-PDF: Abrechnung (F10, D-12)

#### Arten

| Art | Bedingung | Kennzeichnung im PDF |
|---|---|---|
| `INTERIM` (Zwischenstand) | Schuljahr läuft noch | "Zwischenstand, Stand {Datum Uhrzeit}" |
| `FINAL` (Abschluss) | Schuljahr beendet (`ends_on < heute`) oder `closed_at` gesetzt | "Abschluss Schuljahr {Label}" |

#### Umfang (OPEN-02, bis zur Klärung **beides** anbieten)

| Scope | Inhalt |
|---|---|
| `ALL` | Gesamtübersicht aller soll-pflichtigen Familien: Familie, Kinder, bestätigte Stunden, Soll, fehlend, Status. Summenzeile (Anzahl Familien, davon erfüllt). |
| `FAMILY` | Einzelabrechnung einer Familie: Kinder, alle **bestätigten** Einträge (Datum, Zeit, Dauer, Tätigkeit, bestätigt am), Summe, Soll, Ergebnis. Separat darunter: "Noch nicht bestätigt (zählt nicht)" mit den offenen Einträgen. |

#### Endpunkt

`GET /api/v1/admin/reports/billing?schoolYearId=…&scope=ALL|FAMILY&familyId=…` liefert `application/pdf` (Attachment). Dateiname: `elternstunden_{label}_{scope}_{yyyy-MM-dd}.pdf` (Label mit `/` zu `-`).

#### Regeln

1. Layout: A4 hochkant, Kopfzeile mit Name des Horts und Schuljahr, Fußzeile "Seite x/y" und Erstellzeitpunkt (Europe/Berlin). Schriften eingebettet. Optisch an die Hort-Website angelehnt (Design-Tokens aus UI-01).
2. Datenminimierung: **keine** Geburtsdaten, **keine** E-Mail-Adressen im PDF. Kinder mit Vor- und Nachnamen, sonst nichts.
3. Dauer als `x h y min`. Summe ebenso.
4. Generierung synchron, Ziel höchstens 5 Sekunden bei 1.000 Familien (NFR-03).
5. PDFs werden **nicht** gespeichert, sondern bei Bedarf erzeugt. Jede Erzeugung schreibt einen Audit-Eintrag (`REPORT_GENERATED`, Parameter, keine Inhalte).
6. Ein PDF für `FINAL` in einem noch nicht abgeschlossenen Schuljahr ist nicht möglich (Fehler `SCHOOL_YEAR_NOT_FINISHED`).

---

### 7.9 FS-MASTER: Master-Admin (D-13, D-22)

#### Bootstrap

Umgebungsvariablen:

| Variable | Bedeutung |
|---|---|
| `MASTER_ADMIN_EMAIL` | E-Mail des Master-Admins |
| `MASTER_ADMIN_PASSWORD_HASH` | Argon2id-Hash (im Spring-Security-Format). **Nie** Klartext in der Konfiguration. |
| `MASTER_ADMIN_DISPLAY_NAME` | Optional, Standard "Master-Admin" |
| `MASTER_ADMIN_TOTP_SECRET` | TOTP-Secret (Base32). In Produktion Pflicht `[PROPOSED]` |

Beim Start (Profil `prod`):

1. Fehlen `MASTER_ADMIN_EMAIL` oder `MASTER_ADMIN_PASSWORD_HASH`, bricht der Start mit klarer Fehlermeldung ab (**Fail Fast**).
2. Existiert ein Nutzer mit `managed_by_config = true`, wird er aktualisiert (E-Mail, Hash, Name). Sonst wird er angelegt (`role = MASTER_ADMIN`, `status = ACTIVE`, `email_verified_at = now()`, `family_id` NULL).
3. Ein Nutzer mit gleicher E-Mail, der **nicht** der verwaltete ist, führt zum Startabbruch mit Hinweis (Konflikt manuell auflösen).
4. Konfiguration ist die Quelle der Wahrheit: Hash und TOTP aus der Umgebung überschreiben bei jedem Start die DB-Werte.

#### Verhalten

- Anmeldung über das normale lokale Login (Passwort plus TOTP), mit strengeren Limits (SEC-04). **Kein OIDC.**
- Hat alle Admin-Rechte und zusätzlich: Admin-Rollen vergeben, API-Clients verwalten, Backup-Bereich (11.6).
- Zugriff auf das Master-Konto kann **nicht** über die UI verloren gehen (kann dort weder geändert noch gelöscht werden). Ist der Zugang verloren (Passwort oder TOTP weg), stellt man ihn per Serverzugriff wieder her: Umgebungsvariablen ändern, Container neu starten (Runbook OPS-08).

#### Backup-Bereich (nur Master)

Siehe Abschnitt 11. Phase 1: Status anzeigen (letztes erfolgreiches Backup, Größe, Zielstatus), Backup jetzt auslösen. Restore: Phase 1 per Kommandozeile (Runbook), UI-Restore siehe OPEN-05.

---

### 7.10 FS-INT: Schnittstelle für Drittsysteme (D-04) `[PROPOSED]`

Ziel: Die zentrale Kinder- und Familiendatenbank ist später für weitere Systeme nutzbar, **ohne** die Datenbank selbst zu öffnen. Die DB wird nie direkt exponiert, nur diese API.

- Basis: `/api/integration/v1/…`, getrennt von der UI-API, eigene OpenAPI-Gruppe.
- Authentifizierung: `Authorization: Bearer {api-key}`. Schlüssel: 32 Byte Zufall, Präfix `eh_`. In der DB nur der SHA-256-Hash (`api_client.key_hash`). Klartext wird **einmal** bei Erzeugung angezeigt.
- Verwaltung: nur `MASTER_ADMIN`. Jeder Client hat `name`, **`purpose`** (dokumentierter Zweck, Datenschutz), Scopes, `revoked_at`.
- Scopes: `children:read` (id, externalRef, Vorname, Nachname, familyId), `children:birthdate` (ergänzt `birthDate`), `families:read` (id, label, childIds).
- Endpunkte (nur lesend, Phase 1):
  - `GET /children?updatedSince={ISO-8601}&page=&size=`
  - `GET /children/{id}`
  - `GET /families/{id}`
- Rate-Limit 60 Anfragen pro Minute und Client. Optionale IP-Allow-List je Client. Audit-Eintrag je Zugriff (`actor_kind = INTEGRATION`), ohne Nutzdaten.
- Schreibende Schnittstellen sind **nicht** im Umfang (OPEN-14).
- Das Setzen von `external_ref` erfolgt in der Kinderverwaltung (7.7) oder per CSV-Import.

---

### 7.11 FS-MAIL: E-Mail-Versand

Transaktionale Mails (Deutsch, Du-Form, Klartext plus HTML, keine Tracking-Pixel):

| Mail | Auslöser | Inhalt |
|---|---|---|
| Bestätigung | Registrierung Weg A | Link zur E-Mail-Bestätigung (24 h gültig) |
| Registrierungshinweis | Registrierung mit bereits bekannter Adresse | Hinweis, Anmelden oder Passwort zurücksetzen |
| Passwort zurücksetzen | Reset-Anfrage | Link (1 h gültig) |
| Passwort festlegen | OIDC-Konto möchte lokales Passwort | wie Reset |
| Neue Zuordnung | Automatische Zuordnung zu Familie mit bestehenden Mitgliedern (7.1.6) | Name des neuen Mitglieds, Hinweis an Hort-Team |

Technik: Spring Mail über SMTP. Versand **asynchron** mit bis zu 3 Wiederholungen. Ein Versandfehler wird geloggt (ohne Adresse im Klartext) und führt **nicht** zu einer anderen Antwort an den Client (kein Orakel). Absenderadresse und SMTP-Dienst: OPEN-07. Zugangsdaten nur per Umgebungsvariable oder Docker Secret.

---

## 8. API-Konventionen und Übersicht

### 8.1 Konventionen

| Thema | Regel |
|---|---|
| Basis | `/api/v1`, JSON (UTF-8). Integration getrennt unter `/api/integration/v1` (FS-INT). |
| Zeit | Datum `YYYY-MM-DD`, Uhrzeit `HH:mm`, Zeitstempel ISO-8601 in UTC (`Z`). Fachliche Datumswerte sind Europe/Berlin-Kalendertage. |
| Dauer | Immer ganze **Minuten** (`durationMinutes`). |
| IDs | UUID als String. |
| Fehlerformat | `application/problem+json` (RFC 9457) mit zusätzlichen Feldern `code` (stabiler, maschinenlesbarer Fehlercode), `traceId` und bei Validierungsfehlern `errors: [{field, code, message}]`. Keine Stacktraces. |
| Statuscodes | `200/201/204` Erfolg, `202` angenommen (neutrale Antworten), `400` Validierung, `401` nicht angemeldet, `403` keine Berechtigung, `404` nicht gefunden (auch bei fremden IDs, kein Existenz-Orakel), `409` Konflikt, `429` Rate-Limit (mit `Retry-After`). |
| Paging | `page` (ab 0), `size`. Antwort: `{content: [...], page, size, totalElements}`. |
| CSRF | Alle zustandsändernden Requests (`POST/PUT/PATCH/DELETE`) tragen den CSRF-Token (Cookie `XSRF-TOKEN`, Header `X-XSRF-TOKEN`). |
| CORS | **Aus.** Same-Origin-Betrieb (SPA und API über dieselbe Domain). |
| Dokumentation | OpenAPI 3 aus dem Code (springdoc). In Produktion nicht öffentlich erreichbar. Der TypeScript-Client wird daraus generiert, nicht von Hand gepflegt. |
| Idempotenz | `DELETE` und Bestätigung sind idempotent. Doppelte Einträge: siehe FS-ENTRY. |

### 8.2 Endpunktübersicht `[PROPOSED]`

Rollen: **A** = anonym, **U** = angemeldet (jede Rolle), **F** = `FAMILY_MEMBER` mit Familie, **AD** = `ADMIN` oder `MASTER_ADMIN`, **M** = `MASTER_ADMIN`.

| Methode und Pfad | Rolle | Spezifikation |
|---|---|---|
| `GET /auth/captcha` | A | SEC-04, FS-REG |
| `POST /auth/register` | A | FS-REG Weg A |
| `POST /auth/verify-email` | A | FS-AUTH |
| `POST /auth/verify-email/resend` | A | FS-AUTH |
| `POST /auth/login` | A | FS-AUTH |
| `POST /auth/logout` | U | FS-AUTH |
| `POST /auth/password-reset/request` | A | FS-AUTH |
| `POST /auth/password-reset/confirm` | A | FS-AUTH |
| `GET /oauth2/authorization/{id}`, `GET /login/oauth2/code/{id}` | A | FS-AUTH OIDC (Spring-Standardpfade, außerhalb `/api/v1`) |
| `GET /auth/oidc/pending` | A (Pending-Session) | Daten für Schritt "Noch ein Schritt" (FS-REG 7.1.5) |
| `POST /auth/oidc/complete` | A (Pending-Session) | Kinderdaten absenden, Konto anlegen |
| `GET /me` | U | Profil, Rolle, Familienstatus, Verknüpfungen |
| `PATCH /me` | U | Anzeigename ändern |
| `POST /me/password` | U | Passwort ändern/setzen |
| `POST /me/identities/{provider}/link`, `DELETE /me/identities/{id}` | U | Verknüpfungen (INV-11) |
| `PUT /me/claim` | U (unzugeordnet) | Kinderdaten korrigieren (max. 3) |
| `GET /me/family/overview` | F | FS-FAM |
| `GET /me/family/entries` | F | Liste, filterbar nach Schuljahr |
| `POST /me/family/entries` | F | FS-ENTRY |
| `PATCH /me/family/entries/{id}` | F | nur `OPEN` |
| `DELETE /me/family/entries/{id}` | F | nur `OPEN`, Soft-Delete |
| `GET /admin/overview` | AD | FS-ADMOV |
| `GET /admin/dashboard` | AD | Zähler und Widgets |
| `GET /admin/families`, `GET/PATCH /admin/families/{id}` | AD | FS-ADM |
| `GET/POST /admin/children`, `GET/PATCH /admin/children/{id}` | AD | FS-ADM |
| `PUT /admin/children/{id}/enrollments/{schoolYearId}`, `DELETE …` | AD | FS-ADM |
| `POST /admin/children/import` (`?dryRun=true`) | AD | CSV-Import |
| `GET /admin/users`, `GET /admin/users/{id}` | AD | FS-ADM |
| `PUT /admin/users/{id}/family`, `DELETE /admin/users/{id}/family` | AD | Zuordnung (F5) |
| `PUT /admin/users/{id}/role` | AD | Rolle (`FAMILY_MEMBER` oder `ADMIN`) |
| `POST /admin/users/{id}/disable`, `…/enable`, `…/send-password-reset`, `…/terminate-sessions` | AD | FS-ADM |
| `GET /admin/entries?status=OPEN` | AD | FS-CONF |
| `POST /admin/entries/{id}/confirm`, `POST /admin/entries/confirm` (Bulk) | AD | FS-CONF |
| `POST /admin/families/{id}/entries`, `PATCH/DELETE /admin/entries/{id}` | AD | Korrekturen (INV-07) |
| `GET/POST /admin/school-years`, `PATCH /admin/school-years/{id}`, `POST …/close`, `POST …/reopen` | AD | FS-ADM, 5.5 |
| `GET /admin/reports/billing` | AD | FS-PDF |
| `GET /admin/audit` | AD | SEC-11 |
| `GET /master/backups/status`, `POST /master/backups/run` | M | Abschnitt 11 |
| `GET/POST/DELETE /master/api-clients` | M | FS-INT |
| `GET /actuator/health/**` | intern | nur im Docker-Netz, nicht über Caddy (OPS-07) |

---

## 9. Sicherheit

Grundhaltung: Die App verarbeitet Daten von Minderjährigen. Sicherheit hat Vorrang vor Komfort, wo beides kollidiert. Alle Werte "Standard" sind `[PROPOSED]` und per Konfiguration änderbar.

| ID | Anforderung |
|---|---|
| SEC-01 | **Deny by default.** Jede Endpunkt-Methode deklariert ihre Rolle explizit (Abschnitt 6). Ein nicht deklarierter Endpunkt ist gesperrt. Ein Test MUSS alle Endpunkte per Reflection oder OpenAPI prüfen, dass eine Rollenregel existiert. |
| SEC-02 | **Transport.** Nur HTTPS. HTTP leitet auf HTTPS um. HSTS (`max-age=31536000`, `includeSubDomains` erst nach stabiler Domain-Einrichtung). TLS-Zertifikate automatisch über Caddy. |
| SEC-03 | **Passwörter.** Argon2id (Spring `Argon2PasswordEncoder`, Parameter so wählen, dass ein Hash auf dem Ziel-VPS etwa 250 bis 500 ms dauert). Länge 10 bis 128 Zeichen, **keine** Zusammensetzungsregeln. Ablehnen: Passwort gleich E-Mail oder Name, und Einträge einer mitgelieferten Liste häufiger Passwörter. Bei Passwortänderung Mail an den Nutzer. |
| SEC-04 | **Bot-Schutz und Rate-Limits** (siehe 9.1). |
| SEC-05 | **Zweiter Faktor (TOTP, RFC 6238).** `MASTER_ADMIN`: Pflicht in Produktion. `ADMIN`: optional aktivierbar, Pflicht siehe OPEN-09. Secret verschlüsselt gespeichert (AES-GCM, Schlüssel `APP_ENCRYPTION_KEY`), 10 Einmal-Wiederherstellungscodes (nur als Hash gespeichert). |
| SEC-06 | **OIDC-Härtung.** Authorization Code mit PKCE, `state` und `nonce` Pflicht. Prüfen: Signatur, `iss`, `aud`, `exp`, `nonce`. `email_verified = true` Pflicht. Identitätsschlüssel ist `(iss, sub)`. Feste, in der Konfiguration hinterlegte Redirect-URIs. Scopes nur `openid profile email`. Access- und Refresh-Tokens werden **nicht** gespeichert. Verknüpfungsregeln: FS-AUTH und 7.1.5. Logout beendet nur die lokale Session. |
| SEC-07 | **Injection.** Ausschließlich parametrisierte Abfragen. Sortierfelder und Filter aus Requests nur über Whitelist. Eingaben per Bean Validation. Im Frontend kein `dangerouslySetInnerHTML`, Ausgaben sind escaped. |
| SEC-08 | **Zugriffskontrolle gegen IDOR.** `family_id` für `FAMILY_MEMBER` kommt **immer aus der Session**, nie aus Pfad oder Body. Zugriffe auf fremde Ressourcen liefern `404`. Pro Endpunkt MUSS es einen Test "Nutzer aus Familie A greift auf Daten von Familie B zu" geben. |
| SEC-09 | **Session und CSRF.** Cookie `HttpOnly`, `Secure`, `SameSite=Lax`, Präfix `__Host-` wo möglich. Session-Fixation-Schutz (neue Session-ID nach Login). CSRF-Token für alle zustandsändernden Requests. Logout invalidiert serverseitig. |
| SEC-10 | **HTTP-Header** (Caddy und Spring): `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`. Zusätzlich `X-Content-Type-Options: nosniff`, `Referrer-Policy: same-origin`, `Permissions-Policy` ohne Kamera/Mikrofon/Geolocation, HSTS. Keine Inline-Skripte, keine externen Ressourcen (Schriften, Skripte, Bilder) von Dritten. |
| SEC-11 | **Audit-Log.** Protokolliert werden mindestens: Login-Erfolg und -Fehlschlag (ohne Passwort), Rollenänderungen, Zuordnungen (auto und manuell), Bestätigungen, Änderungen und Löschungen von Einträgen, Stammdatenänderungen, Schuljahr-Änderungen, PDF-Erzeugung, API-Client-Verwaltung, Backup-/Restore-Aktionen, Verknüpfen von OIDC-Identitäten. Die Tabelle ist **append-only**: Der Datenbank-Nutzer der App erhält auf `audit_log` nur `INSERT` und `SELECT` `[PROPOSED]`. Aufbewahrungsfrist: OPEN-16. |
| SEC-12 | **Logging ohne personenbezogene Daten.** Keine Passwörter, Tokens, E-Mail-Adressen, Kindernamen, Geburtsdaten oder Freitexte in Logs und Fehlermeldungen. Stattdessen IDs und `traceId`. JSON-Logs mit Korrelations-ID. |
| SEC-13 | **Geheimnisse.** Nur per Umgebungsvariable oder Docker Secret, nie im Repository oder Image. CI prüft mit gitleaks. Rotation dokumentiert (Runbook OPS-08). |
| SEC-14 | **Lieferkette und Container.** Abhängigkeits-Scan (OWASP Dependency-Check oder Trivy, `npm audit`), SBOM (CycloneDX), Image-Scan in CI. Container: Nicht-Root-Nutzer, `read_only` wo möglich, `cap_drop: [ALL]`, `no-new-privileges`, Basis-Images fixiert (Digest), automatische Update-PRs (Renovate oder Dependabot). |
| SEC-15 | **Server-Härtung** (VPS): SSH nur mit Schlüssel, Root-Login aus, Firewall nur 22/80/443, fail2ban, automatische Sicherheitsupdates. Befehle in OPS-06. |
| SEC-16 | **Fehlerbehandlung.** Auth-Fehler immer neutral formuliert. Keine Unterscheidung zwischen "Nutzer unbekannt" und "Passwort falsch". Keine Stacktraces nach außen. |

### 9.1 SEC-04 im Detail: Bot-Schutz und Rate-Limits

**Captcha: ALTCHA** `[PROPOSED]` (D-07). Ablauf:

1. `GET /api/v1/auth/captcha` liefert eine signierte Challenge (HMAC-SHA-256 mit `ALTCHA_HMAC_KEY`, mindestens 32 zufällige Byte, nur serverseitig).
2. Das Widget im Browser löst die Proof-of-Work-Aufgabe (ohne Nutzerinteraktion) und liefert einen Nachweis (`payload`).
3. Der Server verifiziert **jeden** Nachweis mit der offiziellen ALTCHA-Java-Bibliothek: Signatur gültig, nicht abgelaufen (Gültigkeit 5 Minuten), **nicht wiederverwendet** (Replay-Schutz: bereits eingelöste Challenges bis zum Ablauf im Speicher oder in der DB vermerken).
4. Schwierigkeit (`maxNumber`) so einstellen, dass das Lösen auf einem durchschnittlichen Smartphone etwa 1 bis 3 Sekunden dauert. Wert konfigurierbar.
5. Das Widget wird **lokal gebündelt** (kein CDN, SEC-10).
6. Pflicht bei: Registrierung Weg A, Passwort-Reset-Anfrage, Bestätigungs-Mail erneut senden.

**Rate-Limits** (Standardwerte, konfigurierbar; Antwort `429` mit `Retry-After`):

| Ziel | Schlüssel | Standard |
|---|---|---|
| Registrierung Weg A | IP | 5 pro Stunde, 20 pro Tag |
| Registrierung gesamt | global | 200 pro Stunde, bei Überschreitung Alarm an Master |
| **Zuordnungsversuche pro Zielkind** | `claim_key` | **5 pro 24 Stunden**, über alle Wege und IPs (aus `registration_claim`, überlebt Neustarts) |
| Login lokal | IP | 10 pro 15 Minuten |
| Login lokal | Konto | Sperre nach 5 Fehlversuchen, steigend 1, 5, 15, 60 Minuten |
| Passwort-Reset anfordern | E-Mail (gehasht) und IP | 3 pro Stunde je E-Mail, 10 pro Stunde je IP |
| Bestätigungs-Mail erneut | E-Mail | 3 pro Stunde |
| OIDC-Callback | IP | 30 pro Minute |
| Master-Login | IP | 5 pro 15 Minuten, danach Sperre |
| Eintrag erfassen | Nutzer | 60 pro Stunde |
| Integration API | Client | 60 pro Minute |

Umsetzung: Bucket4j (oder gleichwertig) im Speicher für IP- und Nutzerlimits. Die **IP** MUSS aus dem vom Caddy gesetzten Header kommen. Spring MUSS so konfiguriert sein, dass nur dem Caddy-Container vertraut wird (`server.forward-headers-strategy` und Trusted-Proxy-Einstellung), sonst ist das Limit per Header-Fälschung umgehbar. Das Limit pro Zielkind ist bewusst **datenbankbasiert**.

**Bekannte Restschwäche (bewusst akzeptiert, D-05):** Name plus Geburtsdatum eines Kindes sind für Dritte (z. B. andere Eltern) teils bekannt oder erratbar. Gegenmaßnahmen: Captcha und Limits (oben), E-Mail-Bestätigung vor Zuordnung, Benachrichtigung der Familie und Admin-Widget bei automatischer Zuordnung (7.1.6), Familien sehen nur Vornamen der Kinder und keine Kontaktdaten. Verschärfungen (z. B. Admin-Freigabe neuer Mitglieder) sind in OPEN-12 offen.

---

## 10. Datenschutz

Hinweis: Dieser Abschnitt beschreibt technische und organisatorische Anforderungen. Er ersetzt **keine Rechtsberatung**. Rechtsgrundlagen, Texte und Fristen sind mit dem Verein und dessen Datenschutzbeauftragten zu klären (OPEN-16).

| ID | Anforderung |
|---|---|
| DP-01 | **Datenkategorien:** Kinder (Vorname, Nachname, Geburtsdatum), Eltern (Name, E-Mail, ggf. OIDC-Kennung `sub`), Elternstunden (Datum, Dauer, Freitext), Audit-Log, Sessions. Keine besonderen Kategorien nach Art. 9 DSGVO vorgesehen. In der UI steht beim Freitext der Hinweis "Bitte keine sensiblen Daten eintragen." |
| DP-02 | **Datensparsamkeit und Need-to-know.** Familien sehen nur Vornamen ihrer Kinder und Anzeigenamen der Mitglieder. PDFs enthalten keine Geburtsdaten und keine E-Mail-Adressen. Integrations-Scopes trennen Geburtsdatum von übrigen Kinderdaten. |
| DP-03 | **Transparenz.** Seiten "Datenschutz" und "Impressum" sind aus jedem Screen erreichbar, auch vor dem Login. Texte liefert der Verein (Platzhalter bis dahin). Bei Registrierung Weg A: Pflicht-Checkbox "Ich habe die Datenschutzhinweise gelesen." Bei Weg B: Hinweis vor dem Redirect, welche Daten der Anbieter erhält und welche zurückkommen (Name, E-Mail). |
| DP-04 | **OIDC (Google) datenschutzfreundlich.** Freiwillig, der lokale Login bleibt gleichwertig verfügbar. **Kein** JavaScript-SDK und keine Ressourcen des Anbieters auf Seiten vor dem Klick. Erst der Klick auf den Button leitet zum Anbieter weiter (Datenübermittlung an einen Anbieter möglicherweise außerhalb der EU). Angeforderte Daten: nur `openid`, `profile`, `email`. Rechtliche Bewertung: OPEN-16. |
| DP-05 | **Löschung und Anonymisierung.** Es gibt Admin-Funktionen: (a) Nutzer anonymisieren (E-Mail wird `deleted-{id}@invalid`, Name "Gelöschter Nutzer", `password_hash` NULL, Identitäten und Tokens gelöscht, Status `DISABLED`; Einträge bleiben erhalten, `created_by` zeigt auf den anonymisierten Nutzer). (b) Kind anonymisieren (Namen auf Initialen, Geburtsdatum auf das Jahr) nach Austritt und Ablauf der Aufbewahrungsfrist. (c) Familie löschen nur, wenn keine Daten in Aufbewahrungsfrist hängen. Fristen: OPEN-16. In Backups wirkt Löschung mit Ablauf der Backup-Aufbewahrung (BAK-02), das ist zu dokumentieren. |
| DP-06 | **Betroffenenrechte.** Berichtigung durch Admins (FS-ADM). Auskunft und Export: Admin-Funktion "Daten einer Familie exportieren" (JSON und PDF) `[PROPOSED]`, Phase 2. |
| DP-07 | **Auftragsverarbeitung.** Mit dem Hoster (IONOS) und jedem Cloud-Backup-Anbieter (Google Drive oder Microsoft, falls genutzt) ist ein Auftragsverarbeitungsvertrag zu schließen. Hosting in Deutschland oder der EU. Backups werden **clientseitig verschlüsselt** (restic, rclone crypt), Cloud-Anbieter sehen nur Chiffretext. |
| DP-08 | **Dokumentation.** Verzeichnis der Verarbeitungstätigkeiten und TOM-Beschreibung führt der Verein. Die Abschnitte 9, 11 und 12 dieses Dokuments sind dafür die technische Grundlage. |
| DP-09 | **Keine Tracker.** Keine Analytics, keine externen Schriften, keine CDNs, keine Social-Plugins. Cookies: nur technisch notwendige (Session, CSRF). Ob dadurch kein Cookie-Banner nötig ist, bestätigt der Datenschutzbeauftragte des Vereins. |

---

## 11. Backup und Restore (N5, D-19)

### 11.1 Anforderungen

| ID | Anforderung |
|---|---|
| BAK-01 | **Umfang:** PostgreSQL-Datenbank als logischer Dump (`pg_dump --format=custom`). Konfigurationsdateien (Compose, Caddyfile) liegen im Git-Repository. **Geheimnisse** (Passwörter, Schlüssel, `.env`) liegen **nicht** im Repository, sondern im Passwortmanager des Betreibers. Weitere persistente Daten gibt es nicht (keine Uploads). Sessions werden nicht gesichert. |
| BAK-02 | **Frequenz und Aufbewahrung** `[PROPOSED]`, OPEN-17: täglich um 02:30 Uhr (Europe/Berlin). Aufbewahrung 14 tägliche, 8 wöchentliche, 12 monatliche Stände. Zusätzlich manuelles Backup **vor jedem Deployment mit Datenbankmigration**. |
| BAK-03 | **Zwei externe Ziele mindestens** (D-19), clientseitig verschlüsselt: **Ziel 1** IONOS Object Storage (S3, Frankfurt) per restic. **Ziel 2** Borg-Bucket des Auftraggebers per borgmatic (PostgreSQL-Hook). **Optional Ziel 3** Google Drive oder Microsoft-Cloud über das rclone-Backend von restic. Welche Cloud: OPEN-17. |
| BAK-04 | **Verschlüsselung:** restic verschlüsselt jedes Repository mit eigenem Passwort. Die Passwörter liegen im Passwortmanager **und** als verschlossene Offline-Kopie. Ohne Passwort ist ein Backup **unbrauchbar**. Das ist in der Übergabedokumentation hervorzuheben. |
| BAK-05 | **Überwachung:** Jeder Lauf schreibt `last_backup.json` (Zeitpunkte, Größe, Erfolg je Ziel). Die App liest die Datei und zeigt sie dem Master-Admin (11.6). Bei Fehler oder wenn länger als 36 Stunden kein Erfolg vorliegt, geht eine Mail an den Master und optional ein Ping an einen externen Uptime-/Heartbeat-Dienst (z. B. Healthchecks oder Uptime Kuma). |
| BAK-06 | **Prüfung:** Wöchentlich `restic check --read-data-subset=10%` je Repository. **Monatlich automatischer Restore-Test** per Host-Cron (`ops/scripts/restore-test.sh`): temporären PostgreSQL-Container starten, neuesten Dump einspielen, Plausibilitätsabfragen ausführen (Tabellen vorhanden, `school_year`-Anzahl größer 0, neuester `time_entry.created_at` plausibel), Ergebnis per Mail melden. Ein Backup gilt erst als gültig, wenn der Restore-Test besteht. |
| BAK-07 | **Restore** ist **einfach** und **nur durch den Master-Admin** möglich (D-13). Phase 1: Skript auf dem Server (11.4). Phase 2: UI-Auslöser, siehe OPEN-05. |
| BAK-08 | **Manipulationsschutz:** Object Lock (WORM) bei IONOS Object Storage wird laut Produktseite angeboten. Ob und wie es mit `restic forget --prune` zusammenspielt, MUSS vor Aktivierung getestet werden (BAK-06). Bis dahin ohne Object Lock, dafür getrennte Zugangsdaten pro Bucket. |
| BAK-09 | **Ziele:** RPO höchstens 24 Stunden, RTO höchstens 4 Stunden `[PROPOSED]`. Point-in-Time-Recovery per WAL-Archivierung ist **nicht** im Umfang (Datenmenge und Änderungsrate rechtfertigen es nicht). |
| BAK-10 | **Zusatzschicht:** Optionales Backup oder Image des VPS durch IONOS (laut Bestellseite optional ab 6 Cent pro GB und Monat) für den Fall eines Komplettverlusts des Servers. Es **ersetzt** BAK-03 nicht. |

### 11.2 Komponenten

```
backup-Container (Alpine, enthält pg_dump, restic, rclone, supercronic)
  02:30  backup.sh      -> pg_dump -> restic (Repo S3) [+ restic (Repo rclone)]
  Sonntag 04:00  restic check je Repo
  alle 30 s  prüft /trigger/run-now (vom Master-Admin ausgelöst), startet backup.sh
  schreibt /state/last_backup.json

borgmatic (separater Lauf, z. B. im selben Container oder Host-Cron)
  02:45  Dump der DB über borgmatic-PostgreSQL-Hook -> Borg-Bucket
```

Volumes: `backup_state` (App liest nur), `backup_trigger` (App darf schreiben, Sidecar liest und löscht). Die App braucht **keinen** Zugriff auf den Docker-Socket.

### 11.3 Skript `ops/backup/backup.sh` `[PROPOSED]`

```bash
#!/usr/bin/env bash
# Täglicher Backup-Lauf: pg_dump -> restic (je Ziel). Läuft im backup-Container.
set -Eeuo pipefail

STATE_DIR=/state
DUMP="$(mktemp /tmp/elternstunden.XXXXXX.dump)"
trap 'rm -f "$DUMP"' EXIT

PGPASSWORD="$(cat /run/secrets/db_password)"
export PGPASSWORD
started="$(date -u +%FT%TZ)"

pg_dump --host=db --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" \
        --format=custom --file="$DUMP"
pg_restore --list "$DUMP" > /dev/null          # Dump muss lesbar sein

results=()
backup_to() {   # $1 = Name, $2 = restic-Repository, $3 = Datei mit restic-Passwort
  local name="$1" repo="$2" pwfile="$3"
  if RESTIC_PASSWORD_FILE="$pwfile" restic --repo "$repo" backup \
         --tag daily --stdin --stdin-filename elternstunden.dump < "$DUMP" \
     && RESTIC_PASSWORD_FILE="$pwfile" restic --repo "$repo" forget \
         --keep-daily 14 --keep-weekly 8 --keep-monthly 12 --prune; then
    results+=("{\"target\":\"$name\",\"ok\":true}")
  else
    results+=("{\"target\":\"$name\",\"ok\":false}")
  fi
}

backup_to s3 "$RESTIC_REPO_S3" /run/secrets/restic_password_s3
if [[ -n "${RESTIC_REPO_RCLONE:-}" ]]; then
  backup_to rclone "$RESTIC_REPO_RCLONE" /run/secrets/restic_password_rclone
fi

finished="$(date -u +%FT%TZ)"
joined="$(IFS=,; echo "${results[*]}")"
printf '{"started":"%s","finished":"%s","dumpBytes":%s,"targets":[%s]}\n' \
  "$started" "$finished" "$(stat -c %s "$DUMP")" "$joined" > "$STATE_DIR/last_backup.json"

if grep -q '"ok":false' "$STATE_DIR/last_backup.json"; then
  exit 1
fi
```

Umgebung des Containers: `RESTIC_REPO_S3=s3:https://<IONOS_S3_ENDPOINT>/<bucket>`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` (nur für diesen Bucket berechtigt). Der **Endpunkt** steht in der IONOS-Dokumentation unter "Endpoints" und ist vor Einrichtung nachzuschlagen (Platzhalter, nicht geraten). Optional `RESTIC_REPO_RCLONE=rclone:<remote>:<pfad>` mit `rclone.conf` (Typ `crypt` über `drive` bzw. `onedrive`) als Read-only-Mount.

### 11.4 Restore (Phase 1, nur Master-Admin) `[PROPOSED]`

Skript `ops/scripts/restore.sh <repo> <passwortdatei> [snapshot]` auf dem Server, im Projektverzeichnis ausgeführt:

```bash
#!/usr/bin/env bash
# Stellt die Datenbank aus einem restic-Snapshot wieder her. ÜBERSCHREIBT die aktuelle Datenbank.
# Aufruf: ops/scripts/restore.sh <repo> <passwortdatei> [snapshot=latest]
set -Eeuo pipefail

REPO="${1:?Repository fehlt}"
PWFILE="${2:?Passwortdatei fehlt}"
SNAP="${3:-latest}"
: "${POSTGRES_USER:?POSTGRES_USER fehlt}" "${POSTGRES_DB:?POSTGRES_DB fehlt}"
SAFETY_DIR="${RESTORE_SAFETY_DIR:-/var/backups/elternstunden}"

read -r -p "Die Datenbank '${POSTGRES_DB}' wird UEBERSCHRIEBEN. Zur Bestaetigung Datenbanknamen eingeben: " answer
[[ "$answer" == "$POSTGRES_DB" ]] || { echo "Abgebrochen."; exit 1; }

TMP="$(mktemp /tmp/restore.XXXXXX.dump)"
trap 'rm -f "$TMP"' EXIT

echo ">> App stoppen"
docker compose stop app

echo ">> Sicherheitskopie des aktuellen Stands"
mkdir -p "$SAFETY_DIR"
docker compose exec -T db pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB" \
  > "$SAFETY_DIR/pre-restore-$(date +%F-%H%M%S).dump" \
  || echo "WARNUNG: Sicherheitskopie nicht moeglich, es wird trotzdem fortgefahren."

echo ">> Snapshot '$SNAP' holen"
RESTIC_PASSWORD_FILE="$PWFILE" restic --repo "$REPO" dump "$SNAP" /elternstunden.dump > "$TMP"
pg_restore --list "$TMP" > /dev/null

echo ">> Datenbank neu anlegen und einspielen"
docker compose exec -T db dropdb   -U "$POSTGRES_USER" --if-exists "$POSTGRES_DB"
docker compose exec -T db createdb -U "$POSTGRES_USER" "$POSTGRES_DB"
docker compose exec -T db pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner < "$TMP"

echo ">> App starten"
docker compose up -d app
echo "Fertig. Bitte Anmeldung und Familienuebersicht pruefen und den Restore im Betriebsprotokoll vermerken."
```

Anforderungen an das Verhalten:

1. Der Restore MUSS vor dem Überschreiben eine Sicherheitskopie versuchen und den Datenbanknamen als Bestätigung verlangen.
2. Die Flyway-Historie steckt im Dump. Nach dem Restore startet die App und prüft den Schemastand. Ist der Dump **älter** als die App-Version, migriert Flyway vorwärts. Ist er **neuer**, bricht der Start ab (Fail Fast).
3. Der Audit-Log wird mit zurückgesetzt. Der Restore wird daher zusätzlich im Betriebsprotokoll außerhalb der DB notiert.
4. Restore-Dauer bei erwarteter Datenmenge: wenige Minuten.

### 11.5 Wiederherstellung bei Totalverlust des Servers

1. Neuen VPS bestellen und härten (OPS-06). Docker installieren.
2. Repository klonen, Secrets aus dem Passwortmanager einspielen, `docker compose up -d db`.
3. Restore wie 11.4 (die DB ist leer, `dropdb --if-exists` ist unkritisch).
4. `docker compose up -d`, DNS auf die neue IP umstellen, Zertifikate holt Caddy selbst.

### 11.6 Master-Bereich "Backup" (Phase 1)

- Anzeige: letzter erfolgreicher Lauf je Ziel, Größe, Alter. Warnfarbe, wenn älter als 36 Stunden.
- Schaltfläche "Backup jetzt ausführen" (schreibt `/trigger/run-now`).
- Hinweis mit Link auf das Restore-Runbook (Phase 1 per Server-Skript).
- Audit-Einträge für alle Master-Aktionen (SEC-11).

---

## 12. Betrieb

### 12.1 Umgebungen

| ID | Anforderung |
|---|---|
| OPS-01 | **dev** (lokal): `docker compose` mit PostgreSQL, **Mailpit** (Mail-Fänger) und einem lokalen OIDC-Testanbieter (z. B. Dex oder Keycloak) statt Google. **prod:** ein IONOS VPS (D-18). Eine Staging-Umgebung ist optional (OPEN-13). |
| OPS-02 | Produktion läuft als Docker Compose mit vier Diensten: `caddy`, `app`, `db`, `backup`. Nur `caddy` veröffentlicht Ports (80, 443). Die DB ist nur im internen Netz `data` erreichbar. |

### 12.2 `docker-compose.yml` (Produktion), Skelett `[PROPOSED]`

```yaml
name: elternstunden

x-logging: &logging
  driver: json-file
  options:
    max-size: "10m"
    max-file: "5"

services:
  caddy:                                  # enthält zusätzlich den SPA-Build unter /srv/spa
    image: ghcr.io/OWNER/elternstunden-web:${APP_VERSION}
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    environment:
      APP_DOMAIN: ${APP_DOMAIN}
    volumes:
      - caddy_data:/data
      - caddy_config:/config
    networks: [edge]
    depends_on:
      app:
        condition: service_healthy
    cap_drop: [ALL]
    cap_add: [NET_BIND_SERVICE]
    security_opt: ["no-new-privileges:true"]
    mem_limit: 128m
    logging: *logging

  app:
    image: ghcr.io/OWNER/elternstunden-app:${APP_VERSION}
    restart: unless-stopped
    environment:
      SPRING_PROFILES_ACTIVE: prod
      TZ: Europe/Berlin
      SPRING_CONFIG_IMPORT: "optional:configtree:/run/secrets/"
      APP_BASE_URL: https://${APP_DOMAIN}
      DB_HOST: db
      DB_NAME: ${POSTGRES_DB}
      DB_USER: ${POSTGRES_USER}
      MAIL_FROM: ${MAIL_FROM}
      SMTP_HOST: ${SMTP_HOST}
      SMTP_PORT: ${SMTP_PORT}
      JAVA_TOOL_OPTIONS: "-XX:MaxRAMPercentage=60"
    secrets:
      - db_password
      - app_encryption_key
      - altcha_hmac_key
      - master_admin_password_hash
      - master_admin_totp_secret
      - smtp_password
      - google_client_secret
    volumes:
      - backup_state:/state:ro
      - backup_trigger:/trigger
    networks: [edge, data]
    expose: ["8080"]
    depends_on:
      db:
        condition: service_healthy
    read_only: true
    tmpfs: [/tmp]
    cap_drop: [ALL]
    security_opt: ["no-new-privileges:true"]
    mem_limit: 1g
    healthcheck:
      test: ["CMD", "/app/healthcheck"]   # kleines Binary oder Skript im Image, kein wget/curl nötig
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 60s
    logging: *logging

  db:
    image: postgres:MAJOR                 # Major-Version fixieren, siehe 4.2
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD_FILE: /run/secrets/db_password
      TZ: Europe/Berlin
    secrets: [db_password]
    volumes:
      - pgdata:/var/lib/postgresql/data
    networks: [data]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 10s
      timeout: 5s
      retries: 5
    mem_limit: 768m
    logging: *logging

  backup:
    image: ghcr.io/OWNER/elternstunden-backup:${APP_VERSION}
    restart: unless-stopped
    environment:
      TZ: Europe/Berlin
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      RESTIC_REPO_S3: ${RESTIC_REPO_S3}
      RESTIC_REPO_RCLONE: ${RESTIC_REPO_RCLONE:-}
      AWS_ACCESS_KEY_ID: ${BACKUP_S3_ACCESS_KEY}
      AWS_SECRET_ACCESS_KEY: ${BACKUP_S3_SECRET_KEY}
    secrets:
      - db_password
      - restic_password_s3
      - restic_password_rclone
    volumes:
      - backup_state:/state
      - backup_trigger:/trigger
      - ./secrets/rclone.conf:/root/.config/rclone/rclone.conf:ro
    networks: [data, egress]
    cap_drop: [ALL]
    security_opt: ["no-new-privileges:true"]
    mem_limit: 256m
    logging: *logging

networks:
  edge: {}
  egress: {}
  data:
    internal: true

volumes:
  pgdata:
  caddy_data:
  caddy_config:
  backup_state:
  backup_trigger:

secrets:
  db_password:                { file: ./secrets/db_password }
  app_encryption_key:         { file: ./secrets/app_encryption_key }
  altcha_hmac_key:            { file: ./secrets/altcha_hmac_key }
  master_admin_password_hash: { file: ./secrets/master_admin_password_hash }
  master_admin_totp_secret:   { file: ./secrets/master_admin_totp_secret }
  smtp_password:              { file: ./secrets/smtp_password }
  google_client_secret:       { file: ./secrets/google_client_secret }
  restic_password_s3:         { file: ./secrets/restic_password_s3 }
  restic_password_rclone:     { file: ./secrets/restic_password_rclone }
```

Hinweise:

- Spring liest die Secrets über `configtree:/run/secrets/`: Der Dateiname entspricht dem Property-Namen (Mapping in der `application-prod.yml`, z. B. `spring.datasource.password` aus `db_password`). Die genaue Zuordnung legt die Implementierung fest, Secrets DÜRFEN NICHT als normale Umgebungsvariablen im Compose stehen.
- Der Backup-Container braucht Internet (`egress`), die App ebenfalls (OIDC-Anbieter, SMTP) über `edge`. Die DB hat **keinen** Internetzugang.
- Fehlt kein Secret für einen optionalen Weg (z. B. kein rclone-Ziel), liefert der Betreiber eine leere Datei. Die Skripte behandeln "leer" als "nicht konfiguriert".

### 12.3 `Caddyfile` `[PROPOSED]`

```caddyfile
{$APP_DOMAIN} {
	encode zstd gzip

	header {
		Strict-Transport-Security "max-age=31536000"
		X-Content-Type-Options "nosniff"
		Referrer-Policy "same-origin"
		Permissions-Policy "camera=(), microphone=(), geolocation=()"
		Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"
		-Server
	}

	@backend path /api/* /oauth2/* /login/oauth2/*
	handle @backend {
		reverse_proxy app:8080
	}

	handle {
		root * /srv/spa
		try_files {path} /index.html
		file_server
	}
}
```

Hinweise: `/actuator/*` wird **nicht** durchgereicht (SEC-15, OPS-07). `style-src 'self'` verbietet `style="…"`-Attribute. Falls das Frontend sie braucht, ist `style-src-attr 'unsafe-inline'` eine **bewusste** Ausnahme, die im ADR festzuhalten ist. Der Caddy MUSS die Client-IP als `X-Forwarded-For` an die App geben (Standardverhalten von `reverse_proxy`).

### 12.4 Konfiguration (Umgebung und Secrets)

| Name | Art | Zweck |
|---|---|---|
| `APP_DOMAIN` | Env | Öffentlicher Hostname (OPEN-13) |
| `APP_VERSION` | Env | Image-Tag |
| `POSTGRES_DB`, `POSTGRES_USER` | Env | DB-Zugang |
| `db_password` | **Secret** | DB-Passwort |
| `app_encryption_key` | **Secret** | AES-GCM-Schlüssel für TOTP-Secrets (32 Byte, Base64) |
| `altcha_hmac_key` | **Secret** | HMAC-Schlüssel der Captcha-Challenges (mindestens 32 Byte) |
| `master_admin_password_hash` | **Secret** | Argon2id-Hash des Master-Passworts |
| `master_admin_totp_secret` | **Secret** | Base32-Secret für TOTP |
| `MASTER_ADMIN_EMAIL`, `MASTER_ADMIN_DISPLAY_NAME` | Env | Master-Konto (FS-MASTER) |
| `SMTP_HOST`, `SMTP_PORT`, `MAIL_FROM` | Env | Mailversand (OPEN-07) |
| `smtp_password` | **Secret** | SMTP-Zugang |
| `GOOGLE_CLIENT_ID` | Env | OIDC-Client (Google) |
| `google_client_secret` | **Secret** | OIDC-Client-Secret |
| `RESTIC_REPO_S3`, `RESTIC_REPO_RCLONE` | Env | Backup-Ziele (11.3) |
| `BACKUP_S3_ACCESS_KEY`, `BACKUP_S3_SECRET_KEY` | Env (`.env`, Rechte 600) | S3-Zugang für Backup |
| `restic_password_s3`, `restic_password_rclone` | **Secret** | Repository-Passwörter |
| `TZ` | Env | `Europe/Berlin` |

**OIDC-Voraussetzung:** Für Google muss im Google-Cloud-Projekt ein OAuth-Client samt Zustimmungsbildschirm angelegt werden (Datenschutz-URL und Domain erforderlich, Details bei der Einrichtung prüfen). Die Redirect-URI lautet `https://{APP_DOMAIN}/login/oauth2/code/google`. Sie setzt eine **fertige Domain mit TLS** voraus (OPEN-13). Für die Entwicklung wird ein getrennter Client oder ein lokaler Testanbieter genutzt. Damit der Redirect-URI-Host und das Schema hinter dem Proxy stimmen, MUSS `server.forward-headers-strategy` passend gesetzt sein.

### 12.5 CI/CD (GitHub Actions) `[PROPOSED]`

| Job | Inhalt |
|---|---|
| `backend` | Build, Unit- und Integrationstests (Testcontainers mit PostgreSQL), Coverage-Report |
| `frontend` | Lint, TypeScript strict, Unit-Tests, Build |
| `e2e` | Playwright gegen `docker compose` (Mobile-Viewport 390×844 und Desktop), inkl. Abnahmekriterien Abschnitt 15 |
| `security` | gitleaks, Dependency-Check/Trivy (Abhängigkeiten und Images), `npm audit`, SBOM, optional ZAP Baseline gegen die Test-Umgebung |
| `build-push` | Images `app`, `web`, `backup` nach GHCR, Tags `sha-<commit>` und `vX.Y.Z` |
| `deploy` | Manuelle Freigabe, dann per SSH: `docker compose pull && docker compose up -d`, danach Health-Check. Rollback: vorherigen Tag setzen und `up -d`. |

Regeln: Flyway-Migrationen sind **rückwärtskompatibel** (Expand/Contract), damit ein App-Rollback ohne DB-Rollback möglich ist. Vor jedem Deployment mit Migration läuft ein manuelles Backup (BAK-02).

### 12.6 Server-Einrichtung und -Härtung (SEC-15, OPS-06)

Annahme: Debian oder Ubuntu auf dem VPS. Reihenfolge einhalten: **erst** SSH-Schlüssel des neuen Nutzers prüfen, **dann** Passwort-Login abschalten.

```bash
# als root, einmalig
adduser --disabled-password --gecos "" deploy
install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
cp ~/.ssh/authorized_keys /home/deploy/.ssh/ && chown deploy:deploy /home/deploy/.ssh/authorized_keys

# Test in zweitem Terminal: ssh deploy@<server> funktioniert? Erst dann weiter.
sed -i 's/^#\?PermitRootLogin.*/PermitRootLogin no/; s/^#\?PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
systemctl reload ssh

apt-get update && apt-get install -y ufw fail2ban unattended-upgrades
ufw default deny incoming && ufw default allow outgoing
ufw allow 22/tcp && ufw allow 80/tcp && ufw allow 443/tcp
ufw --force enable
dpkg-reconfigure -plow unattended-upgrades
timedatectl set-timezone Europe/Berlin

# Docker nach offizieller Anleitung installieren, danach:
usermod -aG docker deploy      # Achtung: Mitgliedschaft in 'docker' entspricht Root-Rechten
```

Wichtig: **Docker umgeht `ufw`** für veröffentlichte Ports. Deshalb veröffentlicht nur `caddy` Ports (80, 443). Die Datenbank wird **nie** per `ports:` veröffentlicht.

### 12.7 Weitere Betriebsanforderungen

| ID | Anforderung |
|---|---|
| OPS-07 | **Monitoring:** `/actuator/health/liveness` und `/readiness` nur im Docker-Netz. Externer Uptime-Check auf eine öffentliche, einfache Prüfseite (z. B. `/api/v1/status` ohne Details). Alarm per Mail an den Master. Plattenplatz und Container-Neustarts überwachen. Metriken (Micrometer) nur intern. |
| OPS-08 | **Runbooks** (in `/docs/runbooks`, jeweils Schritt für Schritt): Deployment, Rollback, Restore (11.4), Totalverlust (11.5), **Master-Zugang verloren** (siehe unten), Secrets rotieren, Admin-Rolle vergeben, OS- und Image-Updates, Zertifikatsproblem. |
| OPS-09 | **Domain und TLS:** A/AAAA-Eintrag auf den VPS, Ports 80/443 erreichbar, Caddy holt Zertifikate selbst. Die Domain ist Voraussetzung für OIDC und Mailversand (SPF/DKIM). Offen: OPEN-13. |
| OPS-10 | **Ressourcen** (VPS M+ mit 4 GB): `app` 1 GB, `db` 768 MB, `caddy` 128 MB, `backup` 256 MB, Rest für Betriebssystem. JVM mit `-XX:MaxRAMPercentage=60`. |
| OPS-11 | **Updates:** Renovate oder Dependabot erzeugt PRs. Monatliches Wartungsfenster für Basis-Images und OS. Sicherheitsupdates des OS automatisch (`unattended-upgrades`). |
| OPS-12 | Das Projekt liefert `ops/scripts/hash-password.sh`, das einen Argon2id-Hash im von der App erwarteten Format ausgibt (für `master_admin_password_hash`). |

**Runbook "Master-Zugang verloren":**

1. Per SSH als `deploy` auf den Server, ins Projektverzeichnis.
2. `./ops/scripts/hash-password.sh` ausführen, neues Passwort eingeben, Hash ausgeben lassen.
3. Hash in `secrets/master_admin_password_hash` schreiben (Rechte 600). Bei verlorenem TOTP zusätzlich ein neues Secret in `secrets/master_admin_totp_secret` hinterlegen.
4. `docker compose up -d --force-recreate app`.
5. Anmelden. Beim Start schreibt die App einen Audit-Eintrag `MASTER_CREDENTIALS_SYNCED` (Akteur `SYSTEM`).

---

## 13. Frontend (D-14, D-16)

| ID | Anforderung |
|---|---|
| UI-01 | **Design-Tokens.** Alle Farben, Abstände, Radien, Schriften als CSS-Variablen an einer Stelle. Die aktuelle Palette ist ein **Platzhalter**, abgeleitet vom gelben Akzent der Hort-Website, bis die echten Werte vorliegen (OPEN-19): Akzent `#F5C400`, Akzentfläche `#FFF6D1`, Text `#2E2E2E`, Grau `#6E6E6E`, Linie `#E2E2E2`. Gelb darf nie als Textfarbe auf Weiß stehen (Kontrast). Kontrast mindestens WCAG AA. Systemschrift oder lokal gebündelte Schrift (keine externen Fonts, SEC-10). |
| UI-02 | **Texte externalisiert.** Alle sichtbaren Texte in einer Ressourcendatei (`de`), keine hartcodierten Strings in Komponenten. Formate über `Intl` (de-DE). Anrede: Du, freundlich, kurz. |
| UI-03 | **Mobile first.** Basis 360 px Breite, einspaltig. Tippflächen mindestens 44×44 px. Wichtige Aktionen im Daumenbereich (unten). Keine Hover-Abhängigkeit. Safe-Area-Insets beachten. Keine horizontale Scrollleiste ab 320 px. Ab 768 px: Admin-Listen als Tabelle, darunter als Karten. Zielgeräte: aktuelle und vorherige Version von iOS Safari und Android Chrome, dazu aktuelle Desktop-Browser. |
| UI-04 | **Erfassung in höchstens 4 Interaktionen** für den Standardfall (Öffnen, Dauer-Chip, Text, Absenden). Primäre Schaltfläche "Stunden eintragen" ist auf der Familienübersicht dauerhaft erreichbar. Erfassung als Bottom-Sheet. Datum standardmäßig heute. Nach dem Absenden: Hinweis "Eingetragen. Wartet auf Bestätigung." und aktualisierte Fortschrittsanzeige. |
| UI-05 | **Screens** (Tabelle unten). |
| UI-06 | **Login und Registrierung.** Oben "Mit Google anmelden" (Link auf `/oauth2/authorization/google`), dann Trenner "oder", dann E-Mail/Passwort. Der Google-Button MUSS die Branding-Vorgaben des Anbieters einhalten (Prüfung vor Go-live). Keine Anbieter-Skripte (DP-04). |
| UI-07 | **Fortschrittsanzeige.** Balken oder Ring: bestätigt als Vollfläche, offen als schraffiert oder heller. Text: "4 h 30 min von 10 h bestätigt", darunter "plus 1 h wartet auf Bestätigung". Bei Erfüllung: "Geschafft! Eure 10 Stunden sind erfüllt." |
| UI-08 | **Zustände.** Lade-Skelette, freundliche Leerzustände ("Noch keine Stunden eingetragen. Tippe auf 'Stunden eintragen'."), Fehlermeldungen verständlich und ohne technische Details, Inline-Validierung mit denselben Regeln wie serverseitig (Zod). Offline: Banner "Keine Verbindung". Es gibt **kein** Offline-Schreiben. |
| UI-09 | **Barrierefreiheit WCAG 2.2 AA.** Semantisches HTML, beschriftete Felder, sichtbarer Fokus, `aria-live` für Rückmeldungen, `aria-sort` in Tabellenköpfen, Bedienung per Tastatur, Skalierung bis 200 %, `prefers-reduced-motion` beachten. |
| UI-10 | **PWA.** Manifest (Name, Icons, Theme-Farbe), installierbar. Service Worker cacht nur die App-Hülle, **nie** API-Antworten. Update-Hinweis bei neuer Version. Kein Push. |
| UI-11 | **Admin-Listen.** Suchfeld oben (Debounce 250 ms, mindestens 2 Zeichen), sortierbare Spaltenköpfe, Filter und Suche in der URL (teilbar, Zurück-Button funktioniert), Sammelaktionen mit Mehrfachauswahl. Hinweisleiste (FS-ADMOV) auf allen Admin-Seiten. |
| UI-12 | **Datum und Uhrzeit.** Native Eingabefelder (`type="date"`, `type="time"`) für zuverlässige mobile Picker. Anzeige de-DE. Datumsgrenzen (`min`, `max`) aus dem Schuljahr und "nicht in der Zukunft". |
| UI-13 | **Datenschutz und Impressum** aus jedem Screen erreichbar (Fußzeile), auch ohne Login. |

### 13.1 Screens (UI-05)

| Route | Rolle | Inhalt |
|---|---|---|
| `/login` | anonym | Google-Button, E-Mail/Passwort, Links "Passwort vergessen", "Registrieren" |
| `/register` | anonym | Weg A: Name, E-Mail, Passwort, Kinderdaten, Captcha, Datenschutz-Checkbox. Daneben "Mit Google registrieren" |
| `/register/claim` | OIDC-Pending | "Noch ein Schritt": Kinderdaten, Anzeigename |
| `/verify-email` | anonym | Token einlösen, Ergebnis anzeigen |
| `/forgot-password`, `/reset-password` | anonym | Reset-Ablauf |
| `/pending` | unzugeordnet | Wartescreen mit Kinderdaten-Korrektur (max. 3) |
| `/` | Familie | Familienübersicht (FS-FAM): Fortschritt, Schaltfläche "Stunden eintragen", Einträge, Partnerhinweis |
| `/entries/new` (als Sheet über `/`) | Familie | Erfassen (FS-ENTRY) |
| `/family` | Familie | Mitglieder, Kinder (Vornamen), Partnerhinweis |
| `/profile` | angemeldet | Anzeigename, Passwort, Verknüpfungen (Google), Abmelden, ggf. TOTP |
| `/admin` | Admin | Dashboard: Hinweisleiste, Widgets (Zu bestätigen, Unzugeordnete, Neue Zuordnungen) |
| `/admin/families`, `/admin/families/:id` | Admin | Übersicht (FS-ADMOV), Familiendetail |
| `/admin/entries` | Admin | Zu bestätigen (FS-CONF) |
| `/admin/children` | Admin | Kinder, Einschreibungen, CSV-Import |
| `/admin/users` | Admin | Nutzer, Zuordnung, Rollen |
| `/admin/school-years` | Admin | Schuljahre |
| `/admin/reports` | Admin | PDF-Abrechnung (FS-PDF) |
| `/admin/audit` | Admin | Audit-Log |
| `/master/backup` | Master | Backup-Status (11.6) |
| `/master/api-clients` | Master | API-Clients (FS-INT) |
| `/legal/privacy`, `/legal/imprint` | alle | Datenschutz, Impressum |

### 13.2 Technische Vorgaben Frontend `[PROPOSED]`

- React, TypeScript (`strict`), Vite. Datenzugriff mit TanStack Query, generiert aus der OpenAPI-Spezifikation. Formulare mit React Hook Form und Zod.
- Zugängliche UI-Primitives (Radix oder Äquivalent) mit Tailwind CSS und den Tokens aus UI-01.
- Keine globale Zustandsbibliothek ohne Notwendigkeit. Serverzustand gehört in TanStack Query.
- Anfangs-JavaScript höchstens 200 KB gzip (NFR-02). Routen werden per Code-Splitting geladen, Admin-Bereich separat.
- ALTCHA-Widget lokal gebündelt (SEC-04).
- Tests: Komponententests (Vitest, Testing Library), E2E mit Playwright (Abschnitt 15).

---

## 14. Nichtfunktionale Anforderungen

| ID | Anforderung |
|---|---|
| NFR-01 | **Verfügbarkeit:** Ziel 99 % pro Monat, geplante Wartungsfenster erlaubt. Kein Hochverfügbarkeitsbetrieb, ein Server genügt. |
| NFR-02 | **Performance:** API-Antworten (ohne PDF) bei bis zu 20 gleichzeitigen Nutzern im 95. Perzentil unter 300 ms. Familienübersicht erster Aufruf auf 4G in unter 2,5 s (LCP). Anfangs-JavaScript höchstens 200 KB gzip. |
| NFR-03 | **PDF:** Gesamtübersicht für 1.000 Familien in höchstens 5 Sekunden. |
| NFR-04 | **Qualität:** Kritische Pfade (Normalisierung und Abgleich, Zeitregeln und Schuljahr-Logik, Rechteprüfung, Registrierungs-Fallunterscheidung OIDC) MÜSSEN vollständig durch Tests abgedeckt sein. Service-Schicht mindestens 80 % Zeilenabdeckung. Statische Analyse: TypeScript `strict`, ESLint, im Backend Compiler-Warnungen als Fehler plus ein Linter-Werkzeug. |
| NFR-05 | **Zeit:** Fachliche Zeitzone Europe/Berlin. Tests für Sommer-/Winterzeit-Umstellung und Schuljahreswechsel (31.8. auf 1.9.). Reine Datumswerte (`work_date`) sind DST-unabhängig. |
| NFR-06 | **Beobachtbarkeit:** JSON-Logs mit `traceId`, Micrometer-Metriken intern, Health-Endpunkte intern (OPS-07). |
| NFR-07 | **Datenmenge:** wenige MB pro Jahr. Keine Optimierung nötig, Indizes laut Abschnitt 5 genügen. |
| NFR-08 | **Dokumentation:** README mit Schnellstart (`docker compose up`), ADRs für jede `[DECIDED]`- und `[PROPOSED]`-Abweichung, OpenAPI, Runbooks (OPS-08). Im Repository liegt eine `AGENTS.md`, die auf dieses Dokument verweist und Abschnitt 0.4 wiederholt. |
| NFR-09 | **Lizenzen:** Nur Abhängigkeiten mit Lizenzen, die für den Betrieb durch einen Verein unkritisch sind (z. B. MIT, Apache-2.0, BSD, LGPL bei dynamischem Einsatz). Stark copyleft-lizenzierte Bibliotheken (z. B. AGPL) nur nach Rückfrage. |
| NFR-10 | **Barrierefreiheit:** siehe UI-09. |

---

## 15. Abnahmekriterien

Notation: Gegeben / Wenn / Dann. Jedes Kriterium MUSS als automatisierter Test existieren (Unit, Integration oder E2E), sofern nicht "manuell" vermerkt.

### 15.1 Registrierung und Zuordnung (FS-REG)

| ID | Kriterium |
|---|---|
| AC-REG-01 | **Treffer.** Gegeben Kind "Anna Müller", geboren 2017-05-03, in Familie F. Wenn sich ein Elternteil mit gültigem Captcha mit "mueller", "anna", 03.05.2017 registriert und die E-Mail bestätigt, dann ist `family_id` = F, es gibt einen Audit-Eintrag `USER_ASSIGNED_TO_FAMILY` mit `method=AUTO_MATCH`. |
| AC-REG-02 | **Kein Orakel.** Statuscode, Body und Antwort-Header sind identisch für: Treffer, kein Treffer, bereits registrierte E-Mail, `THROTTLED`. Ein Test vergleicht die Antworten. |
| AC-REG-03 | **Zweiter Elternteil.** Registriert sich ein zweiter Elternteil mit denselben Kinderdaten, landet er in Familie F. Für das erste Mitglied wechselt `partnerHint` auf `false` und es erhält die Mail "Neue Zuordnung". |
| AC-REG-04 | **Tippfehler.** Bei "Mueler" (falsch) bleibt der Nutzer nach Bestätigung unzugeordnet (`NO_MATCH`). Er sieht den Wartescreen. Der Admin sieht ihn im Widget mit Kandidat "Anna Müller". Nach Zuordnung durch den Admin sieht der Nutzer die Familienübersicht. |
| AC-REG-05 | **Limit pro Zielkind.** Bei 6 Registrierungen mit identischen Kinderdaten innerhalb 24 Stunden von verschiedenen IPs ist ab der 6. das Ergebnis `THROTTLED` ohne Kind-Lookup. Das Limit überlebt einen Neustart der App. |
| AC-REG-06 | **Captcha.** Fehlender, ungültiger, abgelaufener oder wiederverwendeter Nachweis führt zu `400 CAPTCHA_INVALID`. |
| AC-REG-07 | **Geschwister.** Hat Familie F zwei Kinder, genügt die Angabe eines der beiden. |
| AC-REG-08 | **Mehrdeutig.** Passen Name und Geburtsdatum auf Kinder in **verschiedenen** Familien, ist das Ergebnis `AMBIGUOUS` und der Nutzer bleibt unzugeordnet. |
| AC-REG-09 | **Normalisierung.** Alle Testvektoren aus 7.1.3 ergeben die genannten Werte (Unit-Test). |
| AC-REG-10 | **Zuordnung erst nach E-Mail-Bestätigung.** Vor der Bestätigung ist `family_id` NULL und kein Login möglich. |
| AC-REG-11 | **Korrektur durch Nutzer.** Ein unzugeordneter Nutzer kann die Kinderdaten höchstens 3-mal korrigieren, danach `409`. Jede Korrektur zählt für das Limit pro Zielkind. |

### 15.2 OIDC (FS-AUTH)

| ID | Kriterium |
|---|---|
| AC-OIDC-01 | **Neuer Nutzer.** Callback mit gültigem Token und `email_verified=true`, unbekannte E-Mail: Es entsteht noch **kein** DB-Konto. Nach Absenden der Kinderdaten existieren Konto (`ACTIVE`), `user_identity` und `registration_claim`. Bei Treffer ist die Familie sofort zugeordnet. |
| AC-OIDC-02 | **E-Mail nicht bestätigt.** `email_verified=false` führt zu Ablehnung ohne Konto. |
| AC-OIDC-03 | **Verknüpfen.** Existiert ein bestätigtes lokales Konto mit gleicher E-Mail, wird die Identität verknüpft und der Nutzer angemeldet. |
| AC-OIDC-04 | **Vorab-Übernahme verhindert.** Existiert ein **unbestätigtes** lokales Konto mit gleicher E-Mail, wird verknüpft, die E-Mail als bestätigt markiert und `password_hash` auf NULL gesetzt. Das ursprünglich gesetzte Passwort funktioniert danach nicht mehr. |
| AC-OIDC-05 | **Master.** Ein OIDC-Login mit der E-Mail des Master-Admins wird abgelehnt. |
| AC-OIDC-06 | **Manipulation.** Falscher `state`, falscher `nonce`, falsches `aud`, falscher `iss`, abgelaufenes Token oder ungültige Signatur führen zur Ablehnung (Test mit gemocktem Anbieter). |
| AC-OIDC-07 | **Letzte Anmeldemöglichkeit.** Das Entfernen der letzten Anmeldemöglichkeit (INV-11) wird mit `409` abgelehnt. |
| AC-OIDC-08 | **Keine Tokenspeicherung.** Access- und Refresh-Token des Anbieters liegen weder in DB noch in der Session (Test auf Persistenz). |

### 15.3 Anmeldung (FS-AUTH)

| ID | Kriterium |
|---|---|
| AC-AUTH-01 | Login-Fehlerantworten sind für unbekannte E-Mail, falsches Passwort und unbestätigtes Konto **identisch**. |
| AC-AUTH-02 | Nach 5 Fehlversuchen ist das Konto 1 Minute gesperrt. Das richtige Passwort wird in der Sperre abgelehnt. Die Dauer steigt bei weiteren Serien. |
| AC-AUTH-03 | Passwort-Reset beendet alle Sessions des Nutzers. Ein Reset-Token ist nur einmal und nur 1 Stunde gültig. |
| AC-AUTH-04 | Eine Admin-Session ohne Aktivität für 30 Minuten wird mit `401` beendet. |
| AC-AUTH-05 | Passwörter unter 10 Zeichen, gleich der E-Mail oder aus der Liste häufiger Passwörter werden abgelehnt. |
| AC-AUTH-06 | `POST` ohne gültigen CSRF-Token wird mit `403` abgelehnt. |

### 15.4 Stunden erfassen und bestätigen (FS-ENTRY, FS-CONF)

| ID | Kriterium |
|---|---|
| AC-ENTRY-01 | **Schnellerfassung.** "1 h", heute, Text "Garten aufgeräumt": Eintrag `OPEN`, Familienübersicht zeigt `openMinutes` +60, `confirmedMinutes` unverändert. |
| AC-ENTRY-02 | **Raster.** Dauer 20 min, 0 min oder 735 min wird abgelehnt. 15 min und 720 min werden akzeptiert. |
| AC-ENTRY-03 | **Freitext Pflicht.** Leer, nur Leerzeichen oder 2 Zeichen: `400`. 3 Zeichen: ok. 501 Zeichen: `400`. |
| AC-ENTRY-04 | **Datum.** Zukunft: `400`. Datum in keinem Schuljahr: `400`. Datum in abgeschlossenem Schuljahr: abgelehnt. |
| AC-ENTRY-05 | **Exakt.** 2026-10-14, 15:30, 2 h 15 min wird mit `start_time=15:30` und `duration_minutes=135` gespeichert. |
| AC-ENTRY-06 | **Duplikat.** Identischer Eintrag innerhalb 2 Minuten: `409 DUPLICATE_ENTRY`. |
| AC-ENTRY-07 | **Fremde Familie.** Ändern oder Löschen eines Eintrags einer anderen Familie: `404`. |
| AC-ENTRY-08 | **Bestätigt ist gesperrt.** Familienmitglied ändert oder löscht einen `CONFIRMED`-Eintrag: abgelehnt. |
| AC-ENTRY-09 | **Eigene offene Einträge** dürfen geändert und (soft) gelöscht werden. Gelöschte Einträge zählen nicht und erscheinen nicht. |
| AC-ENTRY-10 | **Nebenläufigkeit.** Zwei gleichzeitige Änderungen am selben Eintrag: eine liefert `409 CONCURRENT_MODIFICATION`. |
| AC-CONF-01 | **Bestätigen.** Admin bestätigt: Status `CONFIRMED`, `confirmed_by` und `confirmed_at` gesetzt, Minuten zählen für das Soll. Zweites Bestätigen ändert nichts und liefert Erfolg. |
| AC-CONF-02 | **Sammelbestätigung.** Bei 3 ausgewählten Einträgen werden alle bestätigt. Bereits bestätigte bleiben unverändert. |
| AC-CONF-03 | **Änderung nach Bestätigung.** Ändert ein Admin Dauer, Datum oder Text eines bestätigten Eintrags, wird er wieder `OPEN`, die bestätigte Summe sinkt, ein Audit-Eintrag entsteht. |
| AC-CONF-04 | Ein Familienmitglied, das einen Eintrag bestätigen will, erhält `403`. |
| AC-CONF-05 | In einem abgeschlossenen Schuljahr ist Bestätigen gesperrt. |

### 15.5 Schuljahr (5.5)

| ID | Kriterium |
|---|---|
| AC-SY-01 | Bei Schuljahren 2026/27 (1.9.2026 bis 31.8.2027) und 2027/28: Eintrag vom 2027-08-31 gehört zu 2026/27, 2027-09-01 zu 2027/28. |
| AC-SY-02 | Stichtage so ändern, dass vorhandene Einträge außerhalb lägen: abgelehnt mit klarer Fehlermeldung. |
| AC-SY-03 | Überlappende Schuljahre werden abgelehnt (DB-Constraint und Service). |
| AC-SY-04 | Der System-Job legt das nächste Schuljahr mit Standard-Stichtagen an, wenn es 90 Tage vor Beginn fehlt. Er legt kein Duplikat an. |

### 15.6 Übersichten (FS-FAM, FS-ADMOV)

| ID | Kriterium |
|---|---|
| AC-FAM-01 | `partnerHint` ist `true` bei genau einem Mitglied, bei zwei Mitgliedern `false`. |
| AC-FAM-02 | Ein unzugeordneter Nutzer erhält bei `GET /me/family/overview` `403 FAMILY_ASSIGNMENT_PENDING`. |
| AC-FAM-03 | Eine Familie sieht keine Nachnamen, Geburtsdaten oder E-Mail-Adressen von Kindern oder Mitgliedern. |
| AC-OV-01 | **Hinweisleiste.** "n von m" stimmt mit den Daten überein. 600 bestätigte Minuten gelten als erfüllt, 599 nicht. Offene Minuten zählen nicht. |
| AC-OV-02 | **Keine Verdopplung.** Eine Familie mit 2 eingeschriebenen Kindern erscheint einmal, ihre Minuten werden nicht verdoppelt (Regressionstest zu 5.6). |
| AC-OV-03 | **Sortieren.** Standard `missingMinutes` absteigend. Jede erlaubte Spalte sortiert auf- und absteigend. Ein unbekanntes Sortierfeld liefert `400`. |
| AC-OV-04 | **Suche.** "mueller" findet "Müller". Suche findet über Familienbezeichnung, Kindernamen, Mitgliedsnamen und E-Mail. Weniger als 2 Zeichen: `400`. |
| AC-OV-05 | `size` über 200 wird abgelehnt oder auf 200 begrenzt (dokumentiert). |
| AC-OV-06 | Die Hinweisleiste ist auf **jeder** Admin-Seite sichtbar (E2E). |

### 15.7 PDF (FS-PDF)

| ID | Kriterium |
|---|---|
| AC-PDF-01 | `ALL` im laufenden Schuljahr trägt "Zwischenstand" und Stand-Zeitpunkt, listet alle soll-pflichtigen Familien, enthält **keine** E-Mail-Adressen und Geburtsdaten (Textextraktion im Test). |
| AC-PDF-02 | `FAMILY` listet nur bestätigte Einträge in der Haupttabelle. Offene Einträge stehen getrennt als "zählt nicht". |
| AC-PDF-03 | `FINAL` für ein noch laufendes Schuljahr: `409 SCHOOL_YEAR_NOT_FINISHED`. |
| AC-PDF-04 | Gesamtübersicht für 1.000 Familien in höchstens 5 Sekunden (Performance-Test). |
| AC-PDF-05 | Jede Erzeugung schreibt einen Audit-Eintrag `REPORT_GENERATED`. |

### 15.8 Master-Admin (FS-MASTER)

| ID | Kriterium |
|---|---|
| AC-MASTER-01 | Start im Profil `prod` ohne `MASTER_ADMIN_EMAIL` oder Passwort-Hash bricht mit klarer Meldung ab. |
| AC-MASTER-02 | Bei **null** Admins kann sich der Master anmelden und einen Nutzer zum Admin machen. |
| AC-MASTER-03 | Das Master-Konto lässt sich über UI und API weder ändern noch löschen. OIDC-Login ist abgelehnt. |
| AC-MASTER-04 | Wird Hash oder TOTP in der Konfiguration geändert, gilt nach Neustart der neue Wert, das alte Passwort funktioniert nicht mehr. Ein `SYSTEM`-Audit-Eintrag entsteht. |
| AC-MASTER-05 | Ein `ADMIN` erhält auf `/master/*` und `/api/v1/master/*` `403`. |

### 15.9 Sicherheit (Abschnitt 9)

| ID | Kriterium |
|---|---|
| AC-SEC-01 | Ein automatischer Test stellt sicher, dass **jeder** Endpunkt eine explizite Rollenregel hat (SEC-01). |
| AC-SEC-02 | IDOR-Suite: für jeden Endpunkt mit Ressourcen-ID greift ein Nutzer aus Familie A auf Daten von Familie B zu und erhält `404`. |
| AC-SEC-03 | Antworten auf `/` und `/api/v1/me` enthalten die Header aus SEC-10. |
| AC-SEC-04 | Ein Log-Scan nach einem E2E-Durchlauf findet keine E-Mail-Adressen, Kindernamen, Freitexte oder Tokens (SEC-12). |
| AC-SEC-05 | Rate-Limits liefern `429` mit `Retry-After`. Die IP stammt nur aus dem vertrauten Proxy-Header, ein vom Client gesetzter `X-Forwarded-For` ändert das Limit nicht. |
| AC-SEC-06 | `audit_log` ist für den App-Datenbanknutzer nicht änderbar und nicht löschbar (Test mit `UPDATE`/`DELETE`). |
| AC-SEC-07 | Container laufen als Nicht-Root, mit `cap_drop: ALL`. Der Image-Scan meldet keine kritischen Schwachstellen. |
| AC-SEC-08 | Ein ZAP-Baseline-Scan gegen die Test-Umgebung liefert keine Befunde ab "Medium" (manuell bewertet). |

### 15.10 Backup und Restore (Abschnitt 11)

| ID | Kriterium |
|---|---|
| AC-BAK-01 | Ein Lauf erzeugt in **jedem** konfigurierten Ziel einen neuen Snapshot. `last_backup.json` zeigt Erfolg je Ziel. |
| AC-BAK-02 | **Restore-Test.** Der neueste Dump wird in eine leere Datenbank eingespielt, die App startet, Anmeldung und Familienübersicht funktionieren, Zeilenzahlen stimmen mit der Quelle überein. |
| AC-BAK-03 | Ist ein Ziel nicht erreichbar, meldet der Lauf Fehler, die übrigen Ziele werden trotzdem gesichert, der Master erhält eine Mail. |
| AC-BAK-04 | Ein Repository lässt sich ohne Passwort nicht lesen (Verschlüsselung, manueller Test). |
| AC-BAK-05 | Die Retention (14/8/12) entfernt ältere Snapshots, ohne die jüngsten zu berühren (Test mit simulierten Zeitstempeln). |
| AC-BAK-06 | Der Master sieht im Backup-Bereich den Status und kann ein Backup anstoßen. Ein Admin kann das nicht. |
| AC-BAK-07 | `restore.sh` verlangt die Eingabe des Datenbanknamens und legt vor dem Überschreiben eine Sicherheitskopie an. |
| AC-BAK-08 | Bleibt der Erfolg länger als 36 Stunden aus, löst das System den Alarm aus (BAK-05). |

### 15.11 Integration (FS-INT)

| ID | Kriterium |
|---|---|
| AC-INT-01 | Ohne Schlüssel oder mit widerrufenem Schlüssel: `401`. |
| AC-INT-02 | Scope `children:read` liefert kein `birthDate`. Mit `children:birthdate` ist es enthalten. |
| AC-INT-03 | `updatedSince` filtert korrekt. Das Limit 60 pro Minute greift (`429`). |
| AC-INT-04 | Der Klartext-Schlüssel wird nur bei Erzeugung angezeigt und ist nirgends gespeichert (nur Hash). |

### 15.12 Frontend (Abschnitt 13)

| ID | Kriterium |
|---|---|
| AC-UI-01 | Im Viewport 390×844 ist der Standardeintrag in höchstens 4 Interaktionen möglich (E2E zählt Taps). |
| AC-UI-02 | Bei 320 px Breite gibt es auf keinem Screen eine horizontale Scrollleiste. |
| AC-UI-03 | Alle interaktiven Elemente sind mindestens 44×44 px groß (automatische Prüfung). |
| AC-UI-04 | axe-core meldet auf den Hauptscreens keine Verstöße gegen WCAG 2.2 AA. |
| AC-UI-05 | Die PWA ist installierbar (Lighthouse-Prüfung). Der Service Worker cacht keine API-Antworten. |
| AC-UI-06 | Auf Login- und Registrierungsseite wird **keine** Ressource eines Drittanbieters geladen, bevor der Nutzer den OIDC-Button klickt (Netzwerk-Test). |
| AC-UI-07 | Admin-Listen: Filter und Suche stehen in der URL, ein Neuladen stellt den Zustand her. |

---

## 16. Offene Fragen (Register)

Regel für KI-Agents: Bei jedem Punkt gilt der **Interim-Default**, bis der Auftraggeber entscheidet. Im Code mit `// OPEN-xx` markieren, damit er nach der Entscheidung gefunden und angepasst wird. Nicht raten, nichts darüber hinaus bauen.

| ID | Frage | Interim-Default | Betrifft |
|---|---|---|---|
| OPEN-01 | Welche Folgen hat es bei weniger als 10 h (Ausgleich, Frist)? Wird Überschuss ins nächste Jahr übertragen? | Keine automatischen Folgen. Admins sehen den Hinweis (D-11). Überschuss verfällt am Jahresende. | FS-ADMOV, FS-PDF |
| OPEN-02 | Abrechnungs-PDF: je Familie, als Gesamtliste oder beides? | Beide Varianten (`ALL`, `FAMILY`) implementieren. | FS-PDF |
| OPEN-03 | Woher stammt die Kinderliste und wie wird sie initial befüllt? | Manuelles Anlegen plus CSV-Import (7.7). | FS-ADM |
| OPEN-04 | Welche Familien sind in einem Schuljahr soll-pflichtig? Wie ist es bei Einstieg im laufenden Jahr (anteilig?) oder bei Befreiung? | Einschreibung pro Kind und Schuljahr (`child_enrollment`), volles Soll von 10 h, keine Befreiungen, kein Anteilig. | 5.3, FS-ADM, 5.6 |
| OPEN-05 | Restore per Server-Skript (Phase 1) oder zusätzlich per Knopf in der Master-UI? | Nur Skript. UI zeigt Status und löst Backups aus. | Abschnitt 11 |
| OPEN-06 | Wie verhält sich ein abgeschlossenes Schuljahr (Nachträge, Korrekturen durch Admins)? | Abgeschlossen = schreibgeschützt für alle, Admin kann wieder öffnen. | INV-04 |
| OPEN-07 | Welcher SMTP-Dienst und welche Absenderadresse? | SMTP per Konfiguration. Dev mit Mailpit. | FS-MAIL |
| OPEN-08 | Java oder Kotlin? Gradle oder Maven? | Java, Gradle. | 4.2 |
| OPEN-09 | Zweiter Faktor (TOTP) für Admins? Auch bei OIDC-Admins? | Pflicht nur für Master, für Admins optional. | SEC-05 |
| OPEN-10 | Obergrenze pro Eintrag (720 min)? Dürfen Einträge in der Zukunft liegen? | 720 min, keine Zukunftsdaten. | INV-03, DDL |
| OPEN-11 | Admin-Workflow bei Abweichungen: Ablehnen mit Begründung oder Rückfrage? Darf die Familie eigene offene Einträge ändern? Was passiert bei Änderung bestätigter Einträge? | Kein Ablehnen. Familie darf offene ändern. Admin-Änderung setzt auf `OPEN` zurück (INV-06, INV-07). | FS-CONF |
| OPEN-12 | Selbstkorrektur bei falscher automatischer Zuordnung (z. B. Familienmitglied meldet oder entfernt)? Admin-Freigabe neuer Mitglieder als Verschärfung? | Nur Mail an Mitglieder und Admin-Widget. | FS-REG 7.1.6 |
| OPEN-13 | Hosting: VPS-Standort Deutschland bestätigen, Tarif, wer bucht? Domain und DNS? Staging-Umgebung? | Entwicklung lokal per Compose. Ziel-VPS M+. Domain offen. | Abschnitt 12 |
| OPEN-14 | Soll die Integration später schreibend sein? Brauchen Admins einen CSV-Export? | Nur lesende API. Kein CSV-Export. | FS-INT |
| OPEN-15 | Welche OIDC-Anbieter über Google hinaus (Microsoft, Apple, andere)? | Nur Google. Weitere nur per Konfiguration ergänzbar. | FS-AUTH |
| OPEN-16 | Datenschutz und Recht: Rechtsgrundlage, Hinweise, Google-Login (Drittlandübermittlung), Löschfristen, AV-Verträge, Audit-Log-Aufbewahrung, Cookie-Hinweis. | Technisch datensparsam, Texte Platzhalter. Keine Löschautomatik. | Abschnitt 10 |
| OPEN-17 | Backup: Welche Cloud (Google Drive oder Microsoft)? Welcher Anbieter hinter dem Borg-Bucket? Aufbewahrung 14/8/12 passend? | restic nach IONOS S3 plus Borg-Bucket. Cloud-Ziel aus. Aufbewahrung 14/8/12. | Abschnitt 11 |
| OPEN-18 | Dürfen Nutzer mehreren Familien angehören (Patchwork)? Gibt es eine Obergrenze für Mitglieder je Familie? | Genau eine Familie je Nutzer, keine Obergrenze. | 5.3 |
| OPEN-19 | Echte Design-Werte des Horts (Farben, Schrift, Logo). Dafür wird ein Screenshot oder die Logo-Datei benötigt. | Platzhalter-Palette (UI-01). | UI-01, FS-PDF |

---

## 17. Meilensteine (Reihenfolge für die Umsetzung) `[PROPOSED]`

Jeder Meilenstein ist erst fertig, wenn die zugehörigen Abnahmekriterien (Abschnitt 15) grün sind und die Pipeline durchläuft.

| Nr. | Meilenstein | Inhalt | Abnahme |
|---|---|---|---|
| M0 | Fundament | Repository-Layout, CI, `docker compose` für dev (DB, Mailpit, OIDC-Testanbieter), Flyway `V1`, Health-Endpunkte, Testcontainers, Fehlerformat, `AGENTS.md` | Pipeline grün, Start per Compose |
| M1 | Lokale Anmeldung | Registrierung Weg A (ohne Zuordnung), Verifizierung, Login, Logout, Reset, Sessions, CSRF, Rate-Limits, ALTCHA, Mailversand, Master-Bootstrap, Security-Header | AC-AUTH, AC-REG-02, 06, 10, AC-MASTER, AC-SEC-01, 03, 05, 06 |
| M2 | Stammdaten und Zuordnung | Schuljahre, Familien, Kinder, Einschreibung, Normalisierung, Abgleich, Claims, Zuordnung (auto und manuell), Admin-Widgets für Unzugeordnete, CSV-Import | AC-REG, AC-SY, AC-SEC-02 |
| M3 | OIDC | Google-Login, Verknüpfungsregeln, "Noch ein Schritt", Profil-Verknüpfung | AC-OIDC |
| M4 | Erfassen und Bestätigen | Einträge, Familienübersicht, Partnerhinweis, Admin-Bestätigung | AC-ENTRY, AC-CONF, AC-FAM |
| M5 | Admin-Übersicht | Hinweisleiste, Liste mit Suche, Sortierung, Paging, Familiendetail, Dashboard | AC-OV |
| M6 | Abrechnung | PDF `ALL` und `FAMILY`, Audit | AC-PDF |
| M7 | Betrieb und Backup | Dockerfiles, Prod-Compose, Caddy, Backup-Sidecar, Restore-Skript, Restore-Test, Server-Härtung, Deploy-Pipeline, Monitoring, Runbooks | AC-BAK, AC-SEC-07 |
| M8 | Integration | API-Clients, Integrations-API | AC-INT |
| M9 | Härtung und Abnahme | ZAP, Barrierefreiheits-Audit, Lasttest, Datenschutzdokumente mit dem Verein, Pilot mit einer Testgruppe, Go-live | AC-SEC-08, AC-UI, manuelle Abnahme |

---

## 18. Recherchegrundlage (Stand 2026-10-20)

Alle Angaben stammen von den genannten Seiten und können sich ändern. Preise vor einer Bestellung prüfen. Bewertungen in der Spalte "Folgerung" sind Einschätzungen.

### 18.1 Hosting bei IONOS

| Option | Befund | Folgerung |
|---|---|---|
| Webhosting | Kein Root-Zugang laut FAQ der Seite. Docker wird nicht genannt. Datenbanken: MariaDB (je 2 GB). Genannt werden PHP, Python, Perl, SSH, Git. Preise inkl. MwSt.: Standard 3 € (6 Monate) danach 6 €, Plus 1 € (12 Monate) danach 11 €, Premium 8 € danach 16 €, Ultimate 14 € danach 28 €, Einrichtung 10 €. | Ungeeignet für Spring Boot, Docker und PostgreSQL. |
| Deploy Now | Hosting für Jamstack-Anwendungen per GitHub, ab 4,03 € pro Monat. Docker und Datenbank nicht belegt. | Ungeeignet. |
| VPS | Tarife S+ (1 vCore, 2 GB RAM, 60 GB NVMe) 2 € pro Monat für 3 Monate, M+ (2 vCores, 4 GB, 120 GB) 4 €, L+ (4, 8 GB, 240 GB) 7 €, XL+ (8, 16 GB, 480 GB) 12 €, XXL+ (12, 24 GB, 720 GB) 17 €. Aktionspreise inkl. MwSt., Einrichtung 10 €. Unbegrenzter Traffic bis 1 Gbit/s. Voller Root-Zugriff. Linux: AlmaLinux, Debian, Rocky, Ubuntu. Optionales Backup ab 6 Cent pro GB und Monat. 30 Tage Testphase. Standorte auf der Seite: EU, USA, Großbritannien (Deutschland nicht ausdrücklich). | **Empfohlen.** Preis nach Aktionszeitraum und Standort Deutschland vor Bestellung klären (OPEN-13). |
| VPS (Testbericht) | Testbericht von experte.com (Stand 7.1.2026, USD, ältere Tarifnamen) nennt Deutschland als wählbar, geteilte CPU-Kerne, kein Rescue-System, Upgrade per Tarifwechsel, Downgrade laut IONOS nicht möglich, Images (Snapshots) kostenpflichtig. | Mit Vorsicht lesen, Abweichungen zu den aktuellen Tarifen möglich. Konsequenz: eher größer starten (M+) als später nicht verkleinern können. |
| Cloud Cube / vCPU-Server | Basic Cube XS: 1 vCPU, 2 GB RAM, 60 GB, 0,008 $ pro Stunde (ca. 5,76 $ pro 30 Tage). Ausgehender Traffic: erste 2 TB frei. Preise auf der `.com`-Seite in USD. | Alternative mit stündlicher Abrechnung und mehr Funktionen. Teurer als der VPS. |
| Managed PostgreSQL | Preise je Stunde: Dedicated Core 0,072 $, RAM 0,0084 $ pro GB, SSD Standard 0,08 $ pro GB und 30 Tage. Point-in-Time-Recovery für 7 Tage, 99,95 % SLA, TLS und verschlüsselte Backups. Standorte nicht genannt. Eigene Rechnung: 1 Core, 2 GB RAM, 10 GB SSD ergibt etwa 65 $ pro 30 Tage. | Für diese Größe überdimensioniert. PostgreSQL im Container plus eigenes Backup reicht (D-17). |

### 18.2 Datenbank und Backup

| Thema | Befund | Folgerung |
|---|---|---|
| IONOS Object Storage | S3-kompatibel. Regionen: Frankfurt (`de`, `eu-central-4`), Berlin (`eu-central-2`, `eu-central-3`), außerdem Logroño (Spanien) und Lenexa (USA). Preis: 0,00663 $ pro GB und 30 Tage laut Produktseite, 0,00487 $ laut Preisseite (**Abweichung**, vor Bestellung prüfen). Anfragen laut Produktseite kostenlos, Traffic laut Produktseite separat, laut Preisseite erste 2 TB frei. Object Lock (WORM) und Versionierung genannt. | Geeignetes Backup-Ziel 1 (BAK-03). Endpunkt-URL in der IONOS-Dokumentation nachschlagen. |
| restic und Borg | Quelle: Vergleich auf dataswamp.org (2021, älter). restic: Backends SFTP, S3/MinIO, weitere über rclone, mehrere Systeme pro Repository möglich. Borg: Zugriff per SSH, Borg muss auf dem Server installiert sein, ein Repository je System, Kompression standardmäßig lz4, Begleitwerkzeug borgmatic. Im Test war restic beim Erstbackup schneller, das Borg-Repository kleiner. | restic für S3 und Cloud-Ziele. Borg/borgmatic für das vorhandene Borg-Bucket (BAK-03). |
| Nicht in dieser Recherche belegt | Zusammenspiel von restic `forget --prune` mit Object Lock, rclone-Unterstützung für Google Drive und OneDrive, borgmatic-PostgreSQL-Hook, Endpunkt-URL von IONOS S3. Diese Punkte sind Allgemeinwissen oder offen und **vor Einsatz zu prüfen** (BAK-06, BAK-08). | Als Prüfaufgaben in M7 aufnehmen. |

### 18.3 Captcha und Bot-Schutz

| Lösung | Befund (Anbieteraussagen, keine Rechtsbewertung) | Folgerung |
|---|---|---|
| **ALTCHA** | Open-Source-Widget (MIT), selbst hostbar, stille Proof-of-Work-Prüfung ohne Rätsel. Laut Anbieter DSGVO-konform, keine Cookies, kein Fingerprinting, WCAG 2.2 AA. Bibliotheken für JS/TS, Java, Python und weitere. | **Empfohlen** (D-07): keine Daten an Dritte, keine Rätsel für Eltern, passt zu Spring (Java-Bibliothek). |
| Cap | Apache-2.0, selbst hostbar als Docker-Container mit Valkey, keine Cookies. | Alternative, aber zusätzlicher Dienst. |
| mCaptcha | AGPL-3.0, eigener Server, Version laut Quelle unter 1.0. | Nicht empfohlen (Lizenz, Reife). |
| Friendly Captcha | EU-Hosting, kein Self-Hosting, laut Quelle ab 9 € pro Monat für 1.000 Anfragen. | Alternative, falls Self-Hosting unerwünscht und Kosten akzeptabel. |
| Cloudflare Turnstile | Kein Self-Hosting. Laut Quelle Fingerprinting und Netzwerksignale, der Client kontaktiert bei jedem Laden `challenges.cloudflare.com`. | Nicht empfohlen (Drittanbieter-Kontakt, DP-09). |
| hCaptcha | Kein Self-Hosting, Bildrätsel, laut Quelle Abbruchraten von 5 bis 15 %. | Nicht empfohlen. |

Vorbehalt: Die Vergleichstabelle stammt aus der Dokumentation von "Cap", einem Anbieter mit Eigeninteresse. ALTCHA-Aussagen stammen von altcha.org. Die Barrierefreiheit ist vor Go-live selbst zu prüfen (AC-UI-04).

### 18.4 Quellen

- IONOS VPS: https://www.ionos.de/server/vps
- IONOS Webhosting: https://www.ionos.de/hosting/webhosting
- IONOS Deploy Now (Überblick): https://european-alternatives.eu/de/produkt/ionos-deploy-now
- IONOS Cloud Preise: https://cloud.ionos.com/prices
- IONOS Managed PostgreSQL: https://cloud.ionos.com/managed/dbaas/postgresql
- IONOS Object Storage (Produktseite): https://cloud.ionos.ca/storage/object-storage
- IONOS Object Storage (Dokumentation): https://docs.ionos.com/cloud/backup-and-storage/ionos-object-storage/overview.md
- IONOS Cloud Server vs. VPS: https://ionos.com/help/server-cloud-infrastructure/allgemeine-informationen-vps/differences-between-cloud-server-and-vps
- IONOS VPS Testbericht: https://www.experte.com/server/ionos
- IONOS Docker-Compose-Anleitung (Titel gefunden, Inhalt nicht geprüft): https://www.ionos.com/de-ch/digitalguide/server/konfiguration/docker-compose-installieren-unter-ubuntu/
- ALTCHA: https://altcha.org
- Vergleich Captcha-Alternativen: https://capjs.js.org/guide/best-captcha-alternatives.html
- restic und Borg: https://dataswamp.org/~solene/2021-05-21-borg-vs-restic.html
- Hort-Website (Größenordnung, Name, Trägerform): https://hort-an-der-montessorischule.de/

### 18.5 Nicht recherchiert

IONOS-Listenpreis des VPS nach dem Aktionszeitraum, tatsächlicher VPS-Standort Deutschland, IONOS-Mailangebot für SMTP, IONOS Managed Kubernetes, Verifizierungsanforderungen des Google-OAuth-Zustimmungsbildschirms, rechtliche Bewertung des Google-Logins (OPEN-16), Details zu Microsoft- und Apple-Anmeldung (OPEN-15).
