# Technisches Lastenheft: Elternstunden-Zeiterfassung

| | |
|---|---|
| Version | 0.4.8 (Entwurf) |
| Stand | 2026-10-24 |
| Einrichtung | Hort an der Montessorischule (Elterninitiative, Verein) |
| Zielgruppe | Entwickler:innen und KI-Coding-Agents |
| Fachliche Basis | Lastenheft v0.4 (`docs/lastenheft-elternstunden-v0.4.pdf`, 2 Seiten, für die Abstimmung mit Menschen). Dieses Dokument konkretisiert es technisch. Im Folgenden kurz "Lastenheft (PDF)". |

**Änderungshistorie**

| Version | Datum | Änderung |
|---|---|---|
| 0.1 | 2026-10-20 | Erster Entwurf. |
| 0.2 | 2026-10-20 | Anmeldung ohne Passwort (D-24): Registrierung und Anmeldung per E-Mail-Link (Magic Link) oder OIDC. Passwort und TOTP nur noch für den Master-Admin. Entfallen: Passwort-Registrierung, Passwort-Reset, Konto-Sperre, Passwort-Policy. Neu: SEC-17, OPS-13, OPEN-20, OPEN-21, AC-AUTH neu gefasst. Betroffen: Abschnitte 2 bis 10, 12, 13, 15 bis 17. |
| 0.3 | 2026-10-20 | Technisches Review (31 Befunde) eingearbeitet, bewusst schlank gehalten. Neu: Browser-Bindung von Claim und Link (`auth_binding`-Cookie), Auth-Outbox `auth_request`, zwei DB-Rollen (OPS-14), injizierte Uhr (0.4 Regel 7), OIDC-Regeln (PKCE explizit, schlanker Principal, Google-E-Mail nur bei `@gmail.com` oder `hd` vertrauenswürdig), Sessions enden bei Status-, Rollen- und Familienwechsel, Master-Härtung (TOTP-Replay, Argon2id-Mindestparameter), Admin-TOTP-Ablauf, Fehlerformat und API-Konventionen präzisiert, PDF ohne externe Ressourcen, Backup ohne Sessions und Tokens, Claim-Löschfrist, IP-Kürzung in Logs. Fehler behoben: Off-by-one beim Zielkind-Limit, Sammelbestätigung in abgeschlossenen Jahren, Volume-Pfad `postgres:18`. Neue Abnahmekriterien (AC-AUTH-08a/b und 14 bis 23, AC-MASTER-06/07, AC-REG-13 bis 16, AC-OIDC-09 bis 13, AC-SEC-09 bis 13, AC-MAIL, AC-ENTRY-11/12, AC-FAM-04, AC-DP, AC-ADM-01, AC-BAK-09). |
| 0.3.1 | 2026-10-20 | Lastenheft-PDF auf v0.4 gebracht (F3 ohne Passwort, offene Punkte 6 und 7 an D-24 angepasst) und Verweise darauf vereinheitlicht ("Lastenheft (PDF)"). |
| 0.4 | 2026-10-21 | API-first (D-25): gemeinsamer OpenAPI-Vertrag `api/openapi.yaml` als Quelle der Wahrheit, Backend-Interfaces und DTOs per openapi-generator, Frontend-Client und Zod-Schemas per orval, springdoc entfällt. Vertrag in OpenAPI 3.0.3 (ersetzt die 3.1-Vorgabe aus 0.3). Betroffen: 0.4, 2, 4.2 bis 4.4, 8.1, SEC-01, 12.5, 13.2. |
| 0.4.1 | 2026-10-21 | Rollenwechsel für manuelle Tests in der lokalen Entwicklung (D-26, FS-DEV 7.12, AC-DEV), Umsetzung in M1. |
| 0.4.2 | 2026-10-22 | Endpunktübersicht (8.2) um die Endpunkte für schon spezifizierte Funktionen ergänzt: Nutzer anonymisieren (DP-05) und Einschreibung aus Vorjahr übernehmen (7.7). |
| 0.4.3 | 2026-10-22 | Datumseingabe immer im deutschen Format mit deutschem Kalender statt nativer Datumsfelder (D-27, UI-12). |
| 0.4.4 | 2026-10-22 | Design an Logo und Website des Horts angelehnt: Palette, Überschriften-Schrift, Logo-Zeichen (UI-01, OPEN-19). |
| 0.4.5 | 2026-10-22 | Höchstens zwei Nutzer pro Familie (D-28, INV-13, 7.1.6, AC-REG-17). |
| 0.4.6 | 2026-10-22 | OPEN-02 entschieden: Abrechnung je Familie **und** als Gesamtliste (D-12). Endpunkt mit optionalem Parameter `kind` (FS-PDF). |
| 0.4.7 | 2026-10-24 | Familien erfassen, ändern und löschen nur Einträge im laufenden Schuljahr; andere offene Schuljahre nur Admins (D-29, INV-14, 7.3, 7.4, AC-ENTRY-13). |
| 0.4.8 | 2026-10-24 | Tätigkeitstext höchstens 255 Zeichen statt 500, mit mitlaufendem Zähler und Pflichtfeld-Markierung in der UI (D-09, 7.4, AC-ENTRY-03). Uhrzeiten tolerant eingeben, z. B. `9` für `09:00` (UI-12). |

---

## 0. Lesehinweise für KI-Agents (verbindlich)

### 0.1 Status-Tags

Jede Entscheidung und Anforderung trägt einen Status:

| Tag | Bedeutung | Umgang |
|---|---|---|
| `[DECIDED]` | Vom Auftraggeber entschieden | Exakt umsetzen. Nicht ändern, nicht "verbessern". |
| `[PROPOSED]` | Vorschlag, noch nicht bestätigt | Darf als Default umgesetzt werden. Muss änderbar bleiben (Konfiguration oder klar isolierte Stelle). Nicht als Fakt behandeln. |
| `[OPEN]` | Ungeklärt | **Nicht raten.** Nur den angegebenen Interim-Default umsetzen und im Code mit `// OPEN-xx` markieren, oder beim Auftraggeber nachfragen. Siehe Abschnitt 16. |

Bei Konflikten gilt: `[DECIDED]` vor Lastenheft (PDF) vor diesem Dokument vor eigenen Annahmen.

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
| `F1`..`F10`, `N1`..`N6` | Verweise auf das Lastenheft (PDF) |

### 0.4 Arbeitsregeln

1. Reihenfolge der Umsetzung: Meilensteine aus Abschnitt 17.
2. Kein Feature über diese Spezifikation hinaus. Ideen gehören in Abschnitt 16, nicht in den Code.
3. Jedes Feature gilt erst als fertig (Definition of Done), wenn: Code, Migrationen, Tests (Unit und Integration), Abnahmekriterien aus Abschnitt 15 grün, API-Vertrag zuerst geändert und Code daraus generiert (D-25), keine neuen Warnungen der Security-Scans.
4. Sprache: UI-Texte deutsch (Du-Form, freundlich, kurz). Code, Bezeichner, Commits, Logs englisch. Datenbankbezeichner `snake_case`, englisch.
5. Zeitzone: Fachliche Daten in `Europe/Berlin`. Zeitstempel in der DB als `timestamptz` (UTC).
6. Keine personenbezogenen Daten in Logs, URLs oder Fehlermeldungen (siehe SEC-12).
7. Uhr: Alle fachlichen Zeitvergleiche und Zeitstempel nutzen einen injizierten `java.time.Clock` und gehen als Parameter in SQL (`expires_at > :now`), nie `now()` in fachlicher Logik. `DEFAULT now()` in der DDL ist nur Fallback. Tests steuern die Zeit über einen festen oder verstellbaren `Clock`. Session-Timeouts werden im Test über kurze konfigurierte Werte geprüft.

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
| D-08 | Registrierung und Anmeldung **ohne Passwort**: per **E-Mail-Link** (Magic Link, FS-AUTH 7.2) oder per **OIDC** (Anmeldung mit Google-Konto, "Gmail"). Weitere Anbieter siehe OPEN-15. Beide Wege führen zum selben Nutzerkonto-Modell. Ausnahme Master-Admin: D-22, D-24. | E-Mail-Link `[DECIDED]` (2026-10-20, ersetzt E-Mail + Passwort), OIDC mit Google `[DECIDED]` (Wunsch 2026-10-20), weitere Anbieter `[OPEN]` | Auftraggeber |
| D-09 | Stundenerfassung: Einheit 15 Minuten. Schnellauswahl (0,5 h, 1 h, 2 h, 3 h) **oder** exakt per Datums- und Uhrzeit-Picker. Kurzer Freitext zur Tätigkeit ist **Pflicht**, höchstens 255 Zeichen. | `[DECIDED]` | Auftraggeber, Länge 2026-10-24 |
| D-10 | Ein Admin **bestätigt** erfasste Stunden. Nur bestätigte Stunden zählen für das Soll. Die Familie sieht bestätigte und offene Stunden **getrennt**. | `[DECIDED]` | Auftraggeber |
| D-11 | Admin-Übersicht: dauerhaft sichtbarer Hinweis, welche Familien im Schuljahr noch keine 10 h erreicht haben. Liste sortier- und durchsuchbar. | `[DECIDED]` | Auftraggeber |
| D-12 | Abrechnung als **PDF**: Zwischenstand des laufenden Schuljahres und abgeschlossene Schuljahre, **je Familie und als Gesamtliste** (OPEN-02). | `[DECIDED]` | Auftraggeber, Umfang 2026-10-22 |
| D-13 | Rollen: Familienmitglied, **Admin** (an bestimmte Nutzer vergeben), **Master-Admin** (Notzugang auch ohne Admins; allein zuständig für Backup und Restore). | `[DECIDED]` | Auftraggeber |
| D-14 | **Mobile first**, schlichte moderne UI, optisch an die Hort-Website angelehnt. | `[DECIDED]` | Auftraggeber |
| D-15 | Backend: **Spring Boot** (Java oder Kotlin). Sprache siehe OPEN-08. | Framework `[DECIDED]`, Sprache `[OPEN]` | Auftraggeber |
| D-16 | Frontend: **SPA/PWA**. Konkret React mit TypeScript und Vite. | Ansatz `[DECIDED]`, React-Stack `[PROPOSED]` | Auftraggeber / Vorschlag |
| D-17 | Datenbank: **PostgreSQL** (relational, offen, API-fähig). | `[PROPOSED]` | Vorschlag, Begründung in Abschnitt 18 |
| D-18 | Betrieb als **Docker**-Container (Docker Compose) auf einem **IONOS VPS** (z. B. M+: 2 vCores, 4 GB RAM, 120 GB NVMe). | Docker `[DECIDED]`, VPS `[PROPOSED]` | Auftraggeber / Recherche |
| D-19 | Backup: täglich automatisch, verschlüsselt, auf mindestens zwei externe Ziele (IONOS Object Storage, Borg-Bucket, optional Google Drive oder Microsoft-Cloud). Restore nur durch Master-Admin. | Anforderung `[DECIDED]`, Werkzeuge `[PROPOSED]` | Auftraggeber / Vorschlag |
| D-20 | Authentifizierung per serverseitiger Session (Cookie `HttpOnly`, `Secure`, `SameSite=Lax`) mit CSRF-Schutz, **kein** JWT im Browser. | `[PROPOSED]` | Vorschlag |
| D-21 | Passwort-Hashing mit **Argon2id**, **nur** für das Passwort des Master-Admins (D-22). Andere Konten haben kein Passwort (D-24). | `[PROPOSED]` | Vorschlag |
| D-22 | Der **Master-Admin** ist ein Nutzer mit Rolle `MASTER_ADMIN`, dessen Zugangsdaten **per Konfiguration** (Umgebungsvariablen) definiert und beim Start in die DB synchronisiert werden. Die UI kann ihn weder ändern noch löschen. Dadurch funktioniert der Zugang auch, wenn kein Admin mehr existiert. Er meldet sich **nur lokal** (Passwort, TOTP) an, **nie** per OIDC und **nie** per E-Mail-Link, damit der Notzugang weder von einem externen Anbieter noch vom Mailversand abhängt. | `[PROPOSED]` | Vorschlag |
| D-23 | OIDC-Anbindung über **Spring Security `oauth2Login`** (Authorization Code Flow mit PKCE), anbieterneutral per Konfiguration. Kein JavaScript-SDK des Anbieters im Browser, nur Redirect. Die Identität wird über `(iss, sub)` geführt, nie über die E-Mail allein. Automatisches Verknüpfen mit bestehenden Konten nur unter den Bedingungen aus FS-AUTH. | `[PROPOSED]` | Vorschlag |
| D-24 | **Nutzer haben kein Passwort.** Registrierung und Anmeldung laufen ausschließlich per E-Mail-Link (Magic Link) oder OIDC (D-08). Einzige Ausnahme ist der Master-Admin: Er meldet sich lokal mit Passwort plus TOTP aus der Server-Konfiguration an (Notzugang, unabhängig von Mail und Identitätsanbieter, D-22). **Begründung:** Eltern melden sich selten an und vergessen Passwörter. Ohne Passwörter entfallen Passwort-Reset, Konto-Sperre nach Fehlversuchen, Passwort-Policy und das Risiko wiederverwendeter oder geleakter Passwörter. Der Zugang zum Postfach war über den Passwort-Reset ohnehin schon der Wiederherstellungsweg. **Folgen:** Das Mailkonto ist der Kontozugang (OPEN-09). Die Mailzustellung wird betriebskritisch (OPS-13). | `[DECIDED]` | Auftraggeber, 2026-10-20 |
| D-25 | **API-first.** Ein gemeinsamer OpenAPI-Vertrag `api/openapi.yaml` ist die Quelle der Wahrheit für die API. Backend und Frontend erzeugen ihren Code daraus: Spring die API-Interfaces und DTOs (die Controller sind handgeschrieben und implementieren die Interfaces), das Frontend den typisierten Client (TanStack-Query-Hooks) und Zod-Schemas. Jede API-Änderung beginnt im Vertrag. Werkzeuge: openapi-generator (Generator `spring`, `interfaceOnly`), orval (`react-query`, `zod`), Redocly CLI zum Linten. | Ansatz `[DECIDED]`, Werkzeuge `[PROPOSED]` | Auftraggeber, 2026-10-21 |
| D-26 | **Rollenwechsel für manuelle Tests (nur lokal).** In der lokalen Entwicklung kann man ohne Mail und ohne TOTP schnell zwischen Demo-Konten der Rollen Familienmitglied, unzugeordnet, Admin und Master wechseln (FS-DEV 7.12). In Produktion existiert die Funktion nicht. | Wunsch `[DECIDED]`, Ausgestaltung `[PROPOSED]` | Auftraggeber, 2026-10-21 |
| D-27 | **Datumseingabe immer im deutschen Format.** Datumsfelder zeigen und erwarten `TT.MM.JJJJ` und haben einen deutschen Kalender, unabhängig von der Sprache des Browsers oder Geräts (UI-12). Die API bleibt bei ISO-Daten (8.1). | `[DECIDED]` | Auftraggeber, 2026-10-22 |
| D-28 | Einer Familie sind **höchstens zwei Nutzer** zugeordnet (z. B. beide Elternteile). Es zählen alle zugeordneten Konten, unabhängig von Rolle und Status. Anonymisierte Konten gehören keiner Familie mehr an (DP-05). | `[DECIDED]` | Auftraggeber, 2026-10-22 |
| D-29 | Familien erfassen, ändern und löschen Stunden **nur im laufenden Schuljahr** (das heute enthält, 5.5). Einträge in einem anderen, noch nicht abgeschlossenen Schuljahr legen nur Admins an oder ändern sie, z. B. Nachträge im September für das Vorjahr. | `[DECIDED]` | Auftraggeber, 2026-10-24 |

---

## 3. Glossar

| Begriff | Bedeutung |
|---|---|
| Elternstunden | Von Eltern für den Hort unentgeltlich geleistete Arbeitszeit. |
| Schuljahr | Abrechnungszeitraum. Datensatz `school_year` mit Start- und Enddatum (Standard 1.9. bis 31.8.). |
| Familie (`family`) | Gruppe aus 1..n Kindern und 0..n Nutzern. Träger des Stundenkontos. |
| Kind (`child`) | Zentrale Stammdaten-Entität. Gehört zu genau einer Familie. |
| Nutzer (`app_user`) | Login-Konto einer Person (Elternteil oder Admin). Hat **kein** Passwort (D-24), Anmeldung per E-Mail-Link oder OIDC. |
| Familienmitglied | Nutzer mit Rolle `FAMILY_MEMBER`, der einer Familie zugeordnet ist. |
| Admin | Nutzer mit Rolle `ADMIN`. |
| Master-Admin | Notzugang, per Konfiguration definiert, hat alle Admin-Rechte plus Backup und Restore. Das **einzige** Konto mit Passwort plus TOTP (D-22). |
| E-Mail-Link (Magic Link) | Einmal-Link per Mail, mit dem sich ein Nutzer ohne Passwort anmeldet oder seine Registrierung abschließt. 15 Minuten gültig, einmal einlösbar (FS-AUTH 7.2, SEC-17). |
| Unbestätigt | Status `PENDING_VERIFICATION`: registriert, aber den E-Mail-Link noch nicht eingelöst. Kein Zugang. Wird nach 24 Stunden gelöscht (7.2). |
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
| Backend-Framework | Spring Boot (aktuelle stabile Linie zum Projektstart, Version vor Start prüfen). Stand 2026-10: Spring Boot 4.1.x mit Spring Security ≥ 7.1 | `[DECIDED]` |
| Sprache | Java oder Kotlin, aktuelle LTS-JDK (21 oder neuer) | `[OPEN]` OPEN-08, Interim: Java 25 |
| Build | Gradle oder Maven | `[OPEN]` OPEN-08, Interim: Gradle |
| Persistenz | Spring Data JPA (Hibernate) plus Flyway-Migrationen | `[PROPOSED]` |
| Datenbank | PostgreSQL, Major-Version fixieren (aktuelle stabile Major, vom Spring-Boot-Treiber unterstützt). Stand 2026-10: PostgreSQL 18 | `[PROPOSED]` |
| Security | Spring Security (Session, CSRF), Argon2id nur für das Master-Passwort (D-21), Bucket4j oder gleichwertig für Rate-Limits | `[PROPOSED]` |
| E-Mail-Link | Eigene Endpunkte und eigener Token-Speicher nach SEC-17 (FS-AUTH 7.2). **Nicht** Spring Security One-Time-Token-Login: `JdbcOneTimeTokenService` speichert den Token im Klartext, nutzt `?token=` im Query-String und war von CVE-2026-22751 betroffen (Mehrfach-Einlösung) | `[PROPOSED]` |
| OIDC-Login | Spring Security `oauth2Login` (OpenID Connect, Authorization Code mit PKCE, explizit konfiguriert, siehe 7.2). Anbieter per Konfiguration: Google zuerst, weitere siehe OPEN-15 | Google `[DECIDED]`, Technik `[PROPOSED]` |
| API-Vertrag | API-first (D-25): `api/openapi.yaml` in **OpenAPI 3.0.3**. Grund: openapi-generator dokumentiert für den Generator `spring` keinen 3.1-Support, 3.0.3 läuft in allen Werkzeugen stabil. Wechsel auf 3.1, sobald der Generator es trägt. Backend: Gradle-Plugin `org.openapi.generator` (Generator `spring`, `interfaceOnly`, `useSpringBoot4`, `useJackson3`, `skipDefaultInterface`, Bean Validation), generiert nach `build/`. Frontend: orval (`react-query` mit `fetch`-Mutator, `zod`), generiert nach `src/api/generated/`. Generierter Code liegt nicht im Git. Lint: Redocly CLI. Kein springdoc | `[PROPOSED]` |
| PDF | Serverseitig aus HTML/CSS (z. B. openhtmltopdf) oder PDFBox/OpenPDF. Wahl frei, solange Layout aus FS-PDF erreicht wird und Lizenz kompatibel ist. | `[PROPOSED]` |
| Frontend | React, TypeScript, Vite, PWA-Plugin | `[PROPOSED]` |
| Frontend-Bausteine | React Router, TanStack Query, React Hook Form plus Zod, Tailwind CSS mit zugänglichen Primitives (z. B. Radix/shadcn) | `[PROPOSED]` |
| Reverse Proxy | Caddy | `[PROPOSED]` |
| Captcha | ALTCHA (Widget lokal gebündelt, Verifikation serverseitig mit offizieller Java-Bibliothek) | `[PROPOSED]` |
| Backup | `pg_dump` plus restic (S3, SFTP, rclone-Backends), optional borgmatic für das Borg-Bucket | `[PROPOSED]` |
| Tests | JUnit 5, Testcontainers (PostgreSQL), Playwright (E2E, Mobile-Viewport), OWASP ZAP Baseline, JaCoCo (Abdeckung, NFR-04). Mails im Test über Mailpit bzw. einen Mail-Mock. ALTCHA im Profil `test` mit fester, niedriger Schwierigkeit, **nicht** abgeschaltet | `[PROPOSED]` |
| CI/CD | GitHub Actions: Build, Test, Scan, Image nach GHCR, Deployment per SSH | `[PROPOSED]` |

### 4.3 Repository-Layout `[PROPOSED]`

```
/api         OpenAPI-Vertrag (openapi.yaml), Quelle der Wahrheit für Backend und Frontend (D-25)
/backend     Spring Boot (Gradle/Maven), src/main, src/test, db/migration
/frontend    React + TypeScript + Vite
/ops         docker-compose.yml, caddy/Caddyfile, backup/, scripts/, runbooks/
/docs        Diese Spezifikation, ADRs, Lastenheft (PDF)
```

### 4.4 Backend-Paketstruktur `[PROPOSED]`

Ein Paket pro fachlichem Modul, darin Schichten `web` (Controller, DTOs), `service`, `domain` (Entitäten), `repo`.

| Modul | Inhalt |
|---|---|
| `auth` | Registrierung, E-Mail-Link (Anfordern, Einlösen, Aufräumen), OIDC, Captcha, Rate-Limits, Master-Login |
| `family` | Familie, Kind, Einschreibung, Nutzer-Zuordnung, Claims |
| `schoolyear` | Schuljahre, Stichtage, Soll |
| `timeentry` | Einträge, Bestätigung, Validierung |
| `overview` | Familienübersicht, Admin-Übersicht |
| `report` | PDF-Abrechnung |
| `integration` | API für Drittsysteme, API-Schlüssel |
| `master` | Backup-Status, Restore-Anstoß, Admin-Rollenvergabe im Notfall |
| `audit` | Audit-Log |
| `common` | Fehlerformat, Zeit/Zeitzone, Normalisierung, Konfiguration |

Generierter Code (D-25): API-Interfaces in `de.elternstunden.api`, DTOs in `de.elternstunden.api.model` (Suffix `Dto`). Die Controller der Module implementieren diese Interfaces.

Regeln: Controller enthalten keine Geschäftslogik. Entitäten verlassen die `web`-Schicht nie (nach außen nur die generierten DTOs). Alle Berechtigungsprüfungen zusätzlich auf Service-Ebene (`@PreAuthorize` oder explizit), nicht nur per URL-Muster.

---

## 5. Datenmodell

### 5.1 Prinzipien

- Primärschlüssel sind UUIDs (`gen_random_uuid()`, ab PostgreSQL 13 eingebaut). Das Kind hat zusätzlich `external_ref` als stabilen Anknüpfungspunkt für Drittsysteme (D-03, D-04).
- Alle Fremdschlüssel sind als Constraints in der DB definiert. **Kein** `ON DELETE CASCADE` auf fachlichen Daten (Ausnahme: Hilfstabellen `registration_claim`, `user_token`).
- Fachliche Regeln, die sich als Constraint ausdrücken lassen, stehen in der DB **und** in der Service-Schicht (doppelt, bewusst).
- Alle Beträge in **ganzen Minuten** (`integer`). Keine Fließkomma-Stunden.
- Löschen: `time_entry` per Soft-Delete (`deleted_at`). Kinder, Familien und Nutzer werden nicht physisch gelöscht, sondern über Anonymisierungs-Funktionen behandelt (siehe DP-05). Ausnahme: nie aktivierte Registrierungen (7.1.4 Schritt 9).
- Zwei DB-Rollen (OPS-14): Flyway läuft als Owner, die App als eingeschränkte Rolle. Rechte stehen in einer Flyway-Migration.
- Schema-Änderungen ausschließlich per Flyway (`V1__init.sql`, `V2__...`). Hibernate-DDL-Auto ist aus (`validate`).

### 5.2 Entitäten (Überblick)

```
school_year 1 ---- n child_enrollment n ---- 1 child n ---- 1 family 1 ---- n app_user
     |                                                          |
     +------------------- n time_entry n ----------------------+
                              (family_id, school_year_id, created_by -> app_user)

app_user 1 ---- n registration_claim      app_user 1 ---- n user_token
app_user 1 ---- n user_identity (OIDC)    auth_request (Auth-Outbox, kurzlebig, 7.2)
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
-- Nutzer (D-08, D-13, D-24)
-- ---------------------------------------------------------------
CREATE TYPE user_role   AS ENUM ('FAMILY_MEMBER', 'ADMIN', 'MASTER_ADMIN');
CREATE TYPE user_status AS ENUM ('PENDING_VERIFICATION', 'ACTIVE', 'DISABLED');  -- PENDING = E-Mail-Link noch nicht eingelöst

CREATE TABLE app_user (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email              citext      NOT NULL UNIQUE,
    password_hash      text,                                 -- Argon2id (D-21), NUR für MASTER_ADMIN (D-24)
    display_name       text        NOT NULL CHECK (btrim(display_name) <> ''),
    role               user_role   NOT NULL DEFAULT 'FAMILY_MEMBER',
    status             user_status NOT NULL DEFAULT 'PENDING_VERIFICATION',
    family_id          uuid REFERENCES family (id),          -- NULL = unzugeordnet
    managed_by_config  boolean     NOT NULL DEFAULT false,   -- true nur für den Master-Admin (D-22)
    email_verified_at  timestamptz,                          -- gesetzt beim ersten Einlösen eines E-Mail-Links oder per OIDC
    totp_secret_enc    bytea,                                -- nur Admin-TOTP, verschlüsselt (SEC-05, OPEN-09). Master-TOTP nur aus der Konfiguration
    last_login_at      timestamptz,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT app_user_master_ck CHECK (managed_by_config = (role = 'MASTER_ADMIN')),
    -- D-24: nur der Master hat ein Passwort, und er MUSS eins haben (D-22: Master nur lokal)
    CONSTRAINT app_user_password_master_only_ck CHECK ((password_hash IS NOT NULL) = (role = 'MASTER_ADMIN')),
    CONSTRAINT app_user_active_verified_ck CHECK (status <> 'ACTIVE' OR email_verified_at IS NOT NULL)
);
CREATE INDEX app_user_family_idx ON app_user (family_id);
-- INV-08: höchstens ein per Konfiguration verwalteter Nutzer
CREATE UNIQUE INDEX app_user_single_master_idx ON app_user (managed_by_config) WHERE managed_by_config;

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
    binding_hash     text,                                   -- SHA-256 des auth_binding-Cookies bei Anlage (7.2), NULL bei OIDC
    created_at       timestamptz NOT NULL DEFAULT now(),
    resolved_at      timestamptz,                            -- gesetzt bei Zuordnung (automatisch oder durch Admin)
    superseded_at    timestamptz                             -- gesetzt, wenn ein neuerer Claim gilt oder der Claim verworfen wurde
);
CREATE INDEX registration_claim_key_idx ON registration_claim (claim_key, created_at);
CREATE INDEX registration_claim_user_idx ON registration_claim (user_id);

-- E-Mail-Link (FS-AUTH 7.2, SEC-17). Ein Mechanismus für Anmeldung und Registrierungsabschluss.
CREATE TABLE user_token (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    purpose      text        NOT NULL CHECK (purpose IN ('LOGIN')),  -- text statt ENUM: neue Werte ohne ALTER TYPE
    token_hash   text        NOT NULL UNIQUE,                -- SHA-256 hex des Zufallstokens, Klartext wird nie gespeichert
    binding_hash text        NOT NULL,                       -- SHA-256 des auth_binding-Cookies beim Anfordern (7.2)
    expires_at   timestamptz NOT NULL,                       -- Anforderungszeit + 15 Minuten
    used_at      timestamptz,                                -- gesetzt beim Einlösen, danach ungültig
    created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX user_token_user_idx ON user_token (user_id, purpose);  -- ältere Token beim Neuanfordern entfernen

-- Auth-Outbox (7.2, 7.11). Anonyme Auth-Anfragen schreiben synchron nur diese eine Zeile (gleiche
-- Operation für alle Fälle, kein Orakel). Ein Worker verarbeitet sie und löscht sie danach.
CREATE TABLE auth_request (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    kind            text        NOT NULL CHECK (kind IN ('REGISTER', 'MAGIC_LINK')),
    email           citext      NOT NULL,
    payload         jsonb,                                   -- nur REGISTER: Anzeigename, Kinderdaten
    binding_hash    text        NOT NULL,                    -- SHA-256 des auth_binding-Cookies (7.2)
    attempts        integer     NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL,
    created_at      timestamptz NOT NULL                     -- per Clock gesetzt (0.4 Regel 7)
);
CREATE INDEX auth_request_due_idx ON auth_request (next_attempt_at);

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
        CHECK (char_length(btrim(description)) BETWEEN 3 AND 255),   -- D-09: 255 since V6, 500 before
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
    actor_user_id uuid,                                      -- bewusst ohne FK: append-only, nie aktivierte Konten werden gelöscht
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

Sessions: **Spring Session JDBC** legt seine Tabellen (`spring_session`, `spring_session_attributes`) selbst an bzw. per Flyway-Migration. Dadurch überleben Sessions einen Neustart `[PROPOSED]`. In der Session liegt nur ein schlanker eigener Principal (7.2), keine Spring-OAuth2-Objekte und keine Anbieter-Tokens.

Rechte: Eine eigene Migration vergibt die Rechte an die App-Rolle (OPS-14): DML auf die Fachtabellen, auf `audit_log` nur `INSERT` und `SELECT`.

### 5.4 Invarianten

| ID | Regel | Durchsetzung |
|---|---|---|
| INV-01 | `time_entry.school_year_id` MUSS das Schuljahr sein, dessen `[starts_on, ends_on]` das `work_date` enthält. Liegt das Datum in keinem Schuljahr, wird der Eintrag abgelehnt. | Service, Test |
| INV-02 | `time_entry.family_id` MUSS die Familie des erfassenden Nutzers sein (bei Rolle `FAMILY_MEMBER`). Admins dürfen für jede Familie erfassen oder korrigieren. | Service |
| INV-03 | `work_date` DARF NICHT in der Zukunft liegen (Zeitzone Europe/Berlin). | Service, `[PROPOSED]` OPEN-10 |
| INV-04 | Einträge in einem abgeschlossenen Schuljahr (`closed_at` gesetzt) sind unveränderlich. | Service, OPEN-06 |
| INV-05 | Ein `CONFIRMED`-Eintrag DARF von Familienmitgliedern weder geändert noch gelöscht werden. | Service |
| INV-06 | Ein `OPEN`-Eintrag DARF von Familienmitgliedern der **eigenen** Familie geändert oder (soft) gelöscht werden, wenn er zum laufenden Schuljahr gehört (INV-14). | Service, `[PROPOSED]` OPEN-11 |
| INV-07 | Ändert ein Admin einen `CONFIRMED`-Eintrag inhaltlich (Dauer, Datum, Text), wird er wieder `OPEN` und muss neu bestätigt werden. Ein Audit-Eintrag entsteht. | Service, `[PROPOSED]` OPEN-11 |
| INV-08 | Es gibt höchstens einen Nutzer mit `managed_by_config = true`. | DB-Constraint plus Start-Synchronisation |
| INV-09 | Schuljahre überlappen sich nicht. Beim Ändern der Stichtage DARF kein bestehender Eintrag außerhalb des neuen Bereichs landen (sonst Ablehnung mit klarer Fehlermeldung). | DB-Constraint, Service |
| INV-10 | Nutzer ohne Familie (`family_id IS NULL`, Rolle `FAMILY_MEMBER`) sehen und erfassen **keine** Familiendaten. | Service, Test |
| INV-11 | Jedes Konto (außer `MASTER_ADMIN`) hat **immer** seine E-Mail-Adresse als Anmeldeweg (E-Mail-Link, D-24). OIDC-Identitäten kommen hinzu und dürfen jederzeit entfernt werden. | Service, Test |
| INV-12 | Ein `MASTER_ADMIN` hat **keine** `user_identity` (kein OIDC-Login) und erhält **keine** E-Mail-Links (kein `user_token`). Er meldet sich nur per Passwort plus TOTP an (D-22). | Service, Test |
| INV-13 | Einer Familie sind höchstens zwei Nutzer zugeordnet (`app_user.family_id`, D-28). Jede Zuordnung sperrt dafür die Familie (`SELECT … FROM family … FOR UPDATE`), damit parallele Zuordnungen die Grenze nicht überschreiten. | Service, Test |
| INV-14 | Über die Familienendpunkte (x-roles `F`, auch wenn dort ein Admin für die eigene Familie erfasst) entstehen, ändern sich und verschwinden nur Einträge des **laufenden** Schuljahres: `work_date` liegt darin, und ein geänderter oder gelöschter Eintrag gehört dazu (D-29). Admins nutzen für andere offene Schuljahre die Admin-Endpunkte (7.5). | Service, Test |

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
| Registrieren, Anmelden per E-Mail-Link oder OIDC, Abmelden | ja | ja | ja | nur Master-Login mit Passwort plus TOTP (D-22), Abmelden ja |
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

### 7.0 Zuordnung zum Lastenheft (PDF)

| Lastenheft | Thema | Spezifikation |
|---|---|---|
| F1 Kinderliste | Stammdaten Kinder, Familien | FS-ADM (7.7) |
| F2 Schuljahr | Schuljahre, Stichtage, Soll | 5.5, FS-ADM (7.7) |
| F3 Registrierung | Selbstregistrierung (E-Mail-Link und OIDC, ohne Passwort, D-24), Zuordnung, Bot-Schutz | FS-REG (7.1), FS-AUTH (7.2) |
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

- **Weg A, E-Mail-Link:** Formular mit Name, E-Mail und Kinderdaten, mit Captcha. Das Einlösen des E-Mail-Links bestätigt die Adresse **und** schaltet das Konto frei (D-24). Kein Passwort.
- **Weg B, OIDC:** Anmeldung mit Google (weitere Anbieter siehe OPEN-15). Die E-Mail-Adresse ist beim Anbieter bereits bestätigt.

#### 7.1.2 Eingaben für die Zuordnung (Claim)

| Feld | Regel |
|---|---|
| `child.firstName` | Pflicht, 1 bis 80 Zeichen |
| `child.lastName` | Pflicht, 1 bis 80 Zeichen |
| `child.birthDate` | Pflicht, gültiges Datum, nicht in der Zukunft, nicht älter als 25 Jahre |

Zusätzlich bei Weg A: `displayName` (1 bis 80), `email` (gültig, höchstens 254 Zeichen), Captcha-Nachweis (SEC-04), Bestätigung der Datenschutzhinweise (DP-03).
Bei Weg B: `displayName` wird aus dem `name`-Claim vorbelegt und ist editierbar.

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

#### 7.1.4 Ablauf Weg A (E-Mail-Link)

1. Client holt eine Captcha-Challenge (`GET /api/v1/auth/captcha`), löst sie im Browser und sendet das Formular mit Nachweis (`POST /api/v1/auth/register`).
2. Server prüft **synchron** nur Captcha, Eingabevalidierung und die IP- und globalen Limits (SEC-04). Ungültiges Captcha: `400 CAPTCHA_INVALID`. Limit überschritten: `429` mit neutraler Meldung. Danach schreibt er genau **eine** Zeile in die Auth-Outbox `auth_request` (`kind = REGISTER`, Kinderdaten im `payload`, `binding_hash` aus dem `auth_binding`-Cookie, 7.2). Die Anfrage legt **keine** Session an.
3. Server antwortet **immer identisch** mit `202 Accepted` und der Meldung "Wir haben dir eine E-Mail geschickt. Klicke auf den Link darin, um dich anzumelden." (kein Orakel). Weil synchron für alle Fälle dieselbe Operation läuft, hängen Antwortzeit und Header nicht vom Fall ab.
4. Der Auth-Worker (7.2) verarbeitet die Anfrage. Das Limit je Ziel-E-Mail verwirft still (9.1). Fallunterscheidung:

   | Fall | Verhalten |
   |---|---|
   | E-Mail ist **neu** | Konto anlegen (`PENDING_VERIFICATION`, Rolle `FAMILY_MEMBER`, `family_id` NULL, `password_hash` NULL) per `INSERT … ON CONFLICT (email) DO NOTHING`. Greift der Konflikt (parallele Anfrage), weiter wie "Konto existiert". Claim speichern (`binding_hash` aus der Anfrage), `LOGIN`-Token erzeugen, Mail "Registrierung bestätigen". |
   | Konto existiert, `PENDING_VERIFICATION` | Kein neues Konto. Die neuen Kinderdaten **ersetzen** den offenen Claim: neuer `registration_claim`, der bisherige erhält `superseded_at`. Neuen `LOGIN`-Token erzeugen (ältere werden ungültig), Mail "Registrierung bestätigen". |
   | Konto existiert, `ACTIVE` | Kein neues Konto, die neuen Kinderdaten werden verworfen. Neuen `LOGIN`-Token erzeugen, Mail "Bereits registriert" mit Anmeldelink. |
   | Konto ist `MASTER_ADMIN` oder `DISABLED` | Nichts anlegen, **keine** Mail. Audit ohne E-Mail im Klartext. |

5. Beim Anlegen des Claims wird der Abgleich berechnet:
   - Zähle `registration_claim` mit gleichem `claim_key` in den letzten 24 Stunden, unter `pg_advisory_xact_lock(hashtextextended(claim_key, 0))`, damit parallele Anfragen das Limit nicht umgehen. Ist die Anzahl **≥ `max-claims-per-target`** (Standard 5, konfigurierbar), ist das Ergebnis `THROTTLED`, **ohne** Kind-Lookup. Ersetzte Claims zählen mit.
   - Sonst: Kinder mit gleichem Namen (normalisiert) und Geburtsdatum suchen. Genau eine Familie: `MATCHED` (mit `matched_child_id`). Keine: `NO_MATCH`. Mehrere verschiedene Familien: `AMBIGUOUS`.
6. Der Nutzer öffnet den Link und löst ihn auf der Bestätigungsseite per Klick ein (`POST /api/v1/auth/magic-link/redeem`, 7.2). Erst dann wird der Status `ACTIVE`, `email_verified_at` gesetzt und eine Session angelegt. **Vor dem Einlösen gibt es keinen Zugang.**
7. **Erst jetzt** wird bei `MATCHED` die Familie zugeordnet (`app_user.family_id`), `claim.resolved_at` gesetzt, Audit `USER_ASSIGNED_TO_FAMILY` (`method=AUTO_MATCH`) geschrieben und die Benachrichtigung aus 7.1.6 versendet. **Bedingung:** Der Claim wird nur angewendet, wenn er im **selben Browser** angelegt wurde, in dem der Link eingelöst wird (`registration_claim.binding_hash` = Hash des aktuellen `auth_binding`-Cookies). Sonst wird der Claim verworfen (`superseded_at`). Das Konto ist dann `ACTIVE`, aber unzugeordnet, und der Nutzer gibt die Kinderdaten auf `/pending` neu ein (7.1.7). Das verhindert, dass jemand eine fremde Adresse mit falschen Kinderdaten vorregistriert.
8. Ist der Link abgelaufen, fordert der Nutzer auf `/login` einen neuen an. Für ein unbestätigtes Konto erzeugt das einen neuen `LOGIN`-Token, das Einlösen schließt die Registrierung wie in Schritt 6 und 7 ab.
9. **Aufräumen:** Ein täglicher Job löscht physisch Konten mit `status = 'PENDING_VERIFICATION' AND created_at < :now - 24h`, die **keinen** noch gültigen `LOGIN`-Token haben, samt `registration_claim` und `user_token` (Cascade). Das Einlösen sperrt das Konto vorher per `SELECT … FOR UPDATE`, der Job prüft den Status in der `DELETE`-Bedingung erneut, so überholen sich beide nicht. Das ist eine bewusste Ausnahme zu DP-05, weil diese Konten nie aktiv waren.

#### 7.1.5 Ablauf Weg B (OIDC)

1. Nutzer wählt "Mit Google anmelden". Der Browser wird per Redirect zum Anbieter geleitet (Authorization Code mit PKCE, `state`, `nonce`).
2. Callback: Der Server prüft Signatur, `iss`, `aud`, `exp`, `nonce` und verlangt `email_verified = true` (SEC-06). Fehlt die Bestätigung: Abbruch mit Meldung "Dein Google-Konto hat keine bestätigte E-Mail-Adresse."
3. Entscheidung nach Fall. Die Zeilen werden **von oben nach unten** geprüft, die erste passende gilt:

| Fall | Verhalten |
|---|---|
| Identität `(iss, sub)` existiert, Konto `DISABLED` | Ablehnen mit neutraler Meldung. |
| Identität `(iss, sub)` existiert, Konto `MASTER_ADMIN` | Ablehnen. Darf nach INV-12 nicht vorkommen, daher zusätzlich Alarm an den Master. |
| Identität `(iss, sub)` existiert | Anmelden. Fertig. |
| Konto mit dieser E-Mail ist `MASTER_ADMIN` | Ablehnen: "Dieses Konto kann sich nur über den Master-Login anmelden." (INV-12) |
| Konto mit dieser E-Mail ist `DISABLED` | Ablehnen mit neutraler Meldung. |
| Konto mit dieser E-Mail existiert, aber der Anbieter ist für die Adresse **nicht autoritativ** (siehe unten) | Nicht automatisch verknüpfen. Meldung: "Melde dich bitte per E-Mail-Link an und verknüpfe Google danach in deinem Profil." |
| Konto mit dieser E-Mail existiert, `ACTIVE` | Identität verknüpfen, anmelden. Audit `IDENTITY_LINKED`. |
| Konto mit dieser E-Mail existiert, **unbestätigt** (`PENDING_VERIFICATION`) | Identität verknüpfen, Status `ACTIVE`, `email_verified_at` setzen (der Anbieter hat die Adresse bestätigt). Der vorhandene Claim wird **verworfen** (`superseded_at`, er kann von Dritten stammen, 7.1.4 Schritt 7). Anmelden. Der Nutzer ist unzugeordnet und gibt die Kinderdaten auf `/pending` ein (7.1.7). Audit `IDENTITY_LINKED`. |
| Kein Konto mit dieser E-Mail | Pending-Zustand in der Server-Session halten (`iss`, `sub`, E-Mail, Name). **Noch kein** DB-Konto. Weiter mit Schritt 4. |

**Autoritativ für die E-Mail** ist ein Anbieter nur, wenn er die Adresse selbst verwaltet. Das wird je Anbieter konfiguriert (`trust-email-for-linking`). Google: nur wenn `email` auf `@gmail.com` endet **oder** der Claim `hd` gesetzt ist (Google-Workspace-Domain). Weitere Anbieter (OPEN-15) verknüpfen standardmäßig **nie** per E-Mail (bei Microsoft ist der `email`-Claim veränderlich). Eine Vorab-Übernahme per Passwort ist ausgeschlossen, weil kein Konto ein Passwort hat (D-24).

4. "Noch ein Schritt": Das Kind-Formular (7.1.2) wird angezeigt. Nach dem Absenden legt der Server Konto (`ACTIVE`, `email_verified_at` gesetzt), `user_identity` und `registration_claim` an und berechnet den Abgleich wie in 7.1.4 Schritt 5. Bei `MATCHED` wird sofort zugeordnet (7.1.4 Schritt 7, ohne Browser-Bedingung, weil die Daten in dieser Session eingegeben wurden). `POST /auth/oidc/complete` ist idempotent: Existiert die Identität bereits (Doppelklick), wird nur angemeldet.
5. Captcha: Auf Weg B **kein** Captcha `[PROPOSED]`, weil der Anbieter-Login bereits eine Hürde ist. Die Rate-Limits pro Zielkind (SEC-04) gelten unverändert.

#### 7.1.6 Benachrichtigung bei Zuordnung

Wird ein Nutzer automatisch einer Familie zugeordnet, die schon aktive Mitglieder hat, erhalten diese eine E-Mail: "{displayName} wurde deiner Familie zugeordnet. Falls das nicht stimmt, wende dich bitte an das Hort-Team." Zusätzlich zeigt die Admin-Übersicht (FS-ADMOV) ein Widget "Neue automatische Zuordnungen" (letzte 14 Tage). Eine Selbstkorrektur durch Familienmitglieder gibt es zunächst nicht (OPEN-12).

**Volle Familie (D-28, INV-13):** Hat die Familie schon zwei Nutzer, ordnet ein Treffer **nicht** zu. Der Nutzer bleibt unzugeordnet, sieht den Wartescreen (7.1.7) und erscheint im Admin-Widget "Unzugeordnete Nutzer". Audit `AUTO_ASSIGNMENT_SKIPPED` (`reason=FAMILY_FULL`), die Bestandsmitglieder erhalten keine Mail. Eine Zuordnung durch Admins in eine volle Familie liefert `409 FAMILY_FULL`; der Admin entfernt vorher ein Mitglied.

#### 7.1.7 Unzugeordnete Nutzer

- Nutzer ohne Familie sehen nach dem Login nur den Hinweis "Zuordnung ausstehend. Wir konnten deine Angaben keinem Kind zuordnen. Ein Admin prüft das." sowie ihre eingegebenen Kinderdaten.
- Sie KÖNNEN die Kinderdaten **korrigieren**, höchstens 3-mal pro Konto `[PROPOSED]` (`PUT /me/claim`). Jede Korrektur erzeugt einen neuen `registration_claim` (der vorherige erhält `superseded_at`) und unterliegt dem Rate-Limit pro Zielkind. Wurde der Claim verworfen (7.1.4 Schritt 7, 7.1.5), gibt der Nutzer die Kinderdaten erstmals ein. Diese Eingabe zählt nicht als Korrektur. Für zugeordnete Nutzer liefert `PUT /me/claim` `409 ALREADY_ASSIGNED`.
- Admins sehen alle unzugeordneten Nutzer mit Claim-Daten (FS-ADMOV Widget "Unzugeordnete Nutzer") und erhalten **Kandidatenvorschläge**: Kinder mit gleichem Geburtsdatum oder ähnlichem Namen (Levenshtein-Distanz der normalisierten Nachnamen höchstens 2, per Erweiterung `fuzzystrmatch` in eigener Migration). Zuordnung per Klick (F5). Danach `resolved_at` setzen und Audit `USER_ASSIGNED_TO_FAMILY` (`method=ADMIN`).

#### 7.1.8 Partnerhinweis (F4)

Siehe FS-FAM. Für die Zuordnung des zweiten Elternteils gilt derselbe Ablauf: Er gibt dieselben Kinderdaten an und landet in derselben Familie.

---

### 7.2 FS-AUTH: Anmeldung, Sitzung, E-Mail-Link, OIDC

Grundsatz (D-24): Nutzer haben **kein Passwort**. Es gibt genau zwei Anmeldewege, den E-Mail-Link und OIDC. Registrierung (7.1.4) und Anmeldung nutzen denselben Link-Mechanismus. Einzige Ausnahme ist der Master-Login (unten).

#### E-Mail-Link (Magic Link, D-08, D-24)

**Browser-Bindung (`auth_binding`)**

- `POST /auth/register` und `POST /auth/magic-link` setzen, falls noch nicht vorhanden, das Cookie `__Host-auth_binding` (32 Byte Zufall, `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/`, 30 Tage). Ob es gesetzt wird, hängt nur davon ab, ob der Browser es schon hat, nie von der E-Mail (kein Orakel).
- Sein SHA-256-Hash wird an der Anfrage (`auth_request`), am Token (`user_token.binding_hash`) und am Claim (`registration_claim.binding_hash`) gespeichert. Verwendung: Claim nur im selben Browser anwenden (7.1.4 Schritt 7), Rückfrage beim Einlösen auf einem anderen Gerät (unten, OPEN-21).

**Anfordern**

- `POST /api/v1/auth/magic-link` mit `email` und Captcha-Nachweis (SEC-04).
- Synchron prüft der Server nur Captcha, Validierung, IP- und globales Limit und schreibt **eine** Zeile in `auth_request` (`kind = MAGIC_LINK`). Die Anfrage legt **keine** Session an.
- Antwort **immer** `202 Accepted` mit derselben neutralen Meldung: "Wenn es ein Konto gibt, haben wir dir eine E-Mail geschickt. Der Link ist 15 Minuten gültig." Status, Body, Header und Antwortzeit hängen nicht vom Fall ab. Ausnahmen: `400 CAPTCHA_INVALID` und `429` für IP- oder globale Limits (9.1). Beide hängen nicht von der Ziel-E-Mail ab.

**Auth-Worker**

- Ein `@Scheduled`-Worker (alle 2 Sekunden) holt fällige Zeilen aus `auth_request` (`FOR UPDATE SKIP LOCKED`). Je Zeile laufen Fallunterscheidung, Token und Mailversand in **einer** Transaktion, die Mail wird am Ende versendet.
- Schlägt der Versand fehl, wird zurückgerollt, `attempts` erhöht und `next_attempt_at` um 1 Minute verschoben. Höchstens 3 Versuche und nur innerhalb von 15 Minuten nach `created_at`, danach wird die Zeile verworfen (Metrik, OPS-13). Nach Erfolg wird die Zeile gelöscht.
- Da der Worker bei jedem Versuch neu entscheidet, sind Wiederholungen idempotent.
- Fallunterscheidung für `MAGIC_LINK` (für `REGISTER` siehe 7.1.4 Schritt 4):

| Fall | Verhalten |
|---|---|
| Limit je Ziel-E-Mail überschritten (9.1) | Kein Token, keine Mail (stilles Verwerfen, Metrik). |
| Konto `ACTIVE` | `LOGIN`-Token erzeugen, Mail "Anmeldelink" senden. |
| Konto `PENDING_VERIFICATION` | `LOGIN`-Token erzeugen, Mail "Anmeldelink" senden. Das Einlösen schließt die Registrierung ab (7.1.4 Schritt 6 und 7). |
| Keine E-Mail bekannt | **Kein** Token, **kein** Konto. Mail "Kein Konto" mit Link auf die Registrierung (`/register`), höchstens **eine** pro Zieladresse und 24 Stunden, sonst still verwerfen (9.1). |
| Konto `MASTER_ADMIN` oder `DISABLED` | Kein Token, **keine** Mail (INV-12). Audit ohne E-Mail im Klartext. |

**Token** (Details SEC-17)

- 32 Byte (256 Bit) aus einem CSPRNG, base64url-kodiert. In der DB steht nur der SHA-256-Hash (`user_token`, Zweck `LOGIN`). Der Klartext steht ausschließlich in der Mail.
- 15 Minuten gültig (`expires_at`), **einmal** einlösbar (`used_at`).
- Eine neue Anfrage (auch eine erneute Registrierung) macht alle älteren, nicht eingelösten `LOGIN`-Token des Nutzers ungültig, indem sie gelöscht werden. Der Worker sperrt dafür zuerst das Konto (`SELECT … FROM app_user … FOR UPDATE`), damit parallele Anfragen nicht zwei gültige Token erzeugen.

**Link und Bestätigungsseite**

- Link: `{APP_BASE_URL}/login/link#token={token}` `[PROPOSED]`. Die Basis kommt **ausschließlich** aus der Konfiguration (`APP_BASE_URL`), nie aus `Host` oder `X-Forwarded-Host`. Der Token steht im **URL-Fragment**: Er wird beim Abruf nicht an den Server übertragen, landet nicht in Server- oder Proxy-Logs und nicht im `Referer`.
- Ein **GET** auf den Link verbraucht den Token **nie**. Mail-Scanner und Link-Vorschauen rufen Links vorab ab, das darf weder anmelden noch den Token entwerten. Es gibt keinen Endpunkt, der einen Token per GET einlöst.
- Die SPA zeigt auf `/login/link` eine Bestätigungsseite mit der Schaltfläche "Jetzt anmelden" und entfernt das Fragment sofort aus der Adresszeile (`history.replaceState`).
- Erst der Klick sendet `POST /api/v1/auth/magic-link/redeem` mit `{token}` (CSRF-geschützt wie jeder `POST`).

**Einlösen**

1. Token per Hash suchen, Token und Konto sperren (`SELECT … FOR UPDATE`) und prüfen: `purpose = 'LOGIN'`, `used_at IS NULL`, `expires_at > :now`, Konto `ACTIVE` oder `PENDING_VERIFICATION`, nicht `MASTER_ADMIN`. Sonst `400 LINK_INVALID`.
2. **Anderes Gerät (OPEN-21, Interim):** Passt der Hash des `auth_binding`-Cookies nicht zu `user_token.binding_hash` und fehlt `confirmOtherDevice: true` im Body, antwortet der Server `409 OTHER_DEVICE_CONFIRMATION_REQUIRED` mit der maskierten Kontoadresse (z. B. `m***@gmail.com`). Der Token wird dabei **nicht** verbraucht. Die Seite fragt: "Dieser Link wurde auf einem anderen Gerät angefordert. Als m\*\*\*@gmail.com anmelden?" Ein zweiter Klick sendet `confirmOtherDevice: true`. Das schützt gegen untergeschobene fremde Links und hält Scanner, die nur einmal klicken, vom Verbrauch ab.
3. Token entwerten (`used_at = :now`). Bei `PENDING_VERIFICATION`: Status `ACTIVE`, `email_verified_at` setzen und den Claim nach 7.1.4 Schritt 7 anwenden oder verwerfen.
4. Anmelden wie unten unter "Anmeldung technisch", `last_login_at` setzen, `200` mit den Daten von `GET /me`. Audit `MAGIC_LINK_REDEEMED`.

- Fehler: Unbekannter, abgelaufener, bereits eingelöster oder ersetzter Token sowie ein unpassender Kontostatus liefern **dieselbe** Antwort `400 LINK_INVALID` ("Der Link ist abgelaufen oder wurde schon benutzt. Fordere einfach einen neuen an."). Der Grund wird nicht unterschieden (SEC-16).
- Geräteübergreifend: Der Link funktioniert nach der Rückfrage in jedem Browser (OPEN-21). Ein zusätzlicher Code für den Gerätewechsel ist offen (OPEN-20).

**Aufräumen**

- Ein täglicher Job löscht abgelaufene und eingelöste Token sowie unbestätigte Konten nach 24 Stunden (7.1.4 Schritt 9) und Claims nach Frist (DP-05).

#### Anmeldung technisch (gilt für E-Mail-Link, OIDC, Master-Login)

- Principal ist ein eigener, schlanker `AppUserPrincipal` (`userId`, Rolle, Familien-ID, Authentifizierungszeitpunkt). Der Principal-Name ist die `app_user.id`, nie die E-Mail. Keine Spring-OAuth2-Objekte und keine Anbieter-Tokens in der Session.
- Ein eigener Login-Endpunkt MUSS die `SessionAuthenticationStrategy` aufrufen (neue Session-ID plus neuer CSRF-Token) und den `SecurityContext` explizit über das `SecurityContextRepository` speichern. Die SPA liest danach den neuen CSRF-Token ein.
- CSRF für die SPA: `csrf(c -> c.spa())` (Cookie `XSRF-TOKEN`, Header `X-XSRF-TOKEN`, 8.1).
- Für `/api/**` antwortet die Security mit `401` bzw. `403` als `application/problem+json` (8.1), **nie** mit einer Weiterleitung zum OIDC-Anbieter.

#### Master-Login (D-22) `[PROPOSED]`

- `POST /api/v1/auth/master/login` mit E-Mail, Passwort und TOTP-Code. Eigene Seite `/login/master`, die von der normalen Anmeldeseite nicht verlinkt ist.
- Fehlermeldung immer neutral: "Anmeldung fehlgeschlagen." Kein Unterschied zwischen falscher E-Mail, falschem Passwort und falschem Code. Bei unbekannter E-Mail wird ein Dummy-Hash geprüft (konstante Antwortzeit).
- TOTP (RFC 6238): ±1 Zeitschritt Toleranz. Jeder Zeitschritt ist nur **einmal** gültig (zuletzt genutzter Schritt im Speicher, eine Instanz genügt).
- Strenge IP-Limits (9.1). Ab 10 Fehlversuchen pro Stunde (über alle IPs) geht eine Alarm-Mail an den Master. Es gibt **keine** Kontosperre, damit der Notzugang nicht blockierbar ist. Schutz bieten Argon2id und TOTP.
- Der Master kann weder E-Mail-Link noch OIDC nutzen (INV-12).

#### Zweiter Faktor für Admins (SEC-05, OPEN-09)

- Ob TOTP für `ADMIN` Pflicht ist, regelt `app.security.admin-totp-required` (Interim `false`, OPEN-09). Ein Admin mit eingerichtetem TOTP braucht es immer.
- Nach dem ersten Faktor (E-Mail-Link oder OIDC) hat die Session nur den Zustand `MFA_PENDING`. Sie gilt ausschließlich für `POST /auth/mfa/verify` und, wenn TOTP Pflicht ist, aber noch fehlt, für die Einrichtung. Der Zustand verfällt nach 10 Minuten.
- `POST /auth/mfa/verify` mit `code` (TOTP oder ein Wiederherstellungscode): Erfolg führt zur vollen Session mit neuer Session-ID, Fehler gibt `400 MFA_INVALID`. TOTP-Regeln (Toleranz, Replay) und IP-Limit wie beim Master-Login.
- Einrichtung: `POST /me/totp/setup` liefert das Secret (Base32, als QR im Frontend). `POST /me/totp/confirm` mit einem gültigen Code aktiviert es und liefert einmalig 10 Wiederherstellungscodes (nur als Hash gespeichert, SEC-05). `DELETE /me/totp` deaktiviert, nur mit frischem Login (5 Minuten) und nur, wenn TOTP nicht Pflicht ist. Ein Admin kann das TOTP eines anderen Admins zurücksetzen (`DELETE /admin/users/{id}/totp`, Audit).
- Wiederherstellungscodes liegen in einer eigenen Tabelle `user_recovery_code (user_id, code_hash, used_at)`, angelegt mit der Admin-TOTP-Migration (Meilenstein M3).

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
app:
  oidc:
    google:
      trust-email-for-linking: GMAIL_OR_HD   # 7.1.5; andere Anbieter: NEVER
```

- **PKCE MUSS explizit** aktiv sein (`ClientRegistration.clientSettings.requireProofKey(true)`), nicht auf Framework-Defaults verlassen. AC-OIDC-09 prüft `code_challenge_method=S256`.
- Weitere Anbieter werden durch **zusätzliche Konfigurationsblöcke** ergänzt (generischer `issuer-uri`), ohne Codeänderung. Welche Anbieter aktiv sind: OPEN-15. Interim: nur Google.
- Der Server MUSS `iss`, `aud`, Signatur, `exp` und `nonce` validieren (Spring tut das für OIDC; Test erforderlich) und `email_verified = true` verlangen.
- **Keine Anbieter-Tokens speichern:** `OAuth2AuthorizedClientRepository` ist ein No-op. Nach dem Callback mappt ein eigener `AuthenticationSuccessHandler` auf den `AppUserPrincipal` (oben). Das `OAuth2AuthenticationToken` wird **nicht** in den `SecurityContext` übernommen.
- **Pending-Zustand:** Ohne DB-Konto (7.1.5 Schritt 4) hat die Session nur den Zustand `OIDC_PENDING`. Er gilt ausschließlich für `GET /auth/oidc/pending` und `POST /auth/oidc/complete` und verfällt nach 15 Minuten. Alle anderen Endpunkte liefern `401`.
- **Kein JavaScript-SDK** des Anbieters im Frontend. Der Button ist ein normaler Link auf den Einstiegs-Endpunkt (DP-04). Der Button MUSS die Branding-Vorgaben des Anbieters erfüllen (UI-06).
- Die Anmeldung wird **nach** dem Callback zu einer normalen Server-Session (Cookie), identisch zum E-Mail-Link.
- **Profil:** Eingeloggte Nutzer KÖNNEN weitere Anbieter verknüpfen und Verknüpfungen jederzeit entfernen. Der E-Mail-Link bleibt immer als Anmeldeweg erhalten (INV-11). Verknüpfen verlangt eine frische Anmeldung (nicht älter als 5 Minuten, sonst `403 REAUTH_REQUIRED`). Ablauf: `POST /me/identities/{provider}/link` (JSON, CSRF) liefert die Einstiegs-URL, die SPA navigiert per Top-Level-GET dorthin. So greift `form-action 'self'` (SEC-10) nicht. Beim Verknüpfen gilt die E-Mail-Regel nicht, weil der Nutzer bereits angemeldet ist.
- `MASTER_ADMIN` kann OIDC **nicht** nutzen (D-22, INV-12).
- ADMIN-Konten dürfen sich per OIDC anmelden. Danach greift der zweite Faktor wie oben.

#### Sitzung (D-20)

| Rolle | Leerlauf-Timeout | Absolute Dauer |
|---|---|---|
| `FAMILY_MEMBER` | 30 Tage (gleitend, mobile Bequemlichkeit) | 90 Tage |
| `ADMIN` | 30 Minuten | 8 Stunden |
| `MASTER_ADMIN` | 15 Minuten | 4 Stunden |

- Alle Werte `[PROPOSED]` und konfigurierbar. Sessions in Spring Session JDBC. Session-Cookie `__Host-SESSION` (SEC-09).
- Spring Session kennt nur den Leerlauf-Timeout: Er wird beim Login je Rolle gesetzt (`maxInactiveInterval`). Die absolute Dauer prüft ein Filter über die Erstellzeit der Session.
- Jede Anmeldung (E-Mail-Link, OIDC, Master-Login, MFA-Abschluss) erzeugt eine neue Session-ID. Sessions auf anderen Geräten bleiben bestehen.
- **Sessions folgen dem Kontozustand:** Jeder Request lädt Status, Rolle und Familie des Nutzers per ID aus der DB (eine kleine Abfrage). Rollen- und Familienwechsel wirken damit sofort. Deaktivieren und Anonymisieren beenden zusätzlich **alle** Sessions des Nutzers (`FindByIndexNameSessionRepository`, Principal-Name = `app_user.id`). Admins können alle Sessions eines Nutzers beenden (FS-ADM).

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
5. `canEdit` und `canDelete` sind nur bei `OPEN` und eigener Familie wahr (INV-05, INV-06), im offenen Schuljahr (INV-04) und nur im laufenden Schuljahr (INV-14).
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

Gemeinsam: **Freitext "Was hast du gemacht?"** ist Pflicht, 3 bis 255 Zeichen nach Trim (D-09). Das Feld zeigt einen mitlaufenden Zähler ("42/255") und nimmt keine weiteren Zeichen an. Ein leeres oder zu kurzes Feld wird rot markiert und mit Text erklärt (UI-08). Ein Chip "Andere Dauer" wechselt in den exakten Modus.

#### Serverseitige Regeln (alle MÜSSEN erzwungen werden, nie nur im Client)

1. `duration_minutes % 15 == 0`, `15 <= duration <= 720` (OPEN-10 für Obergrenze).
2. `description` getrimmt, Länge 3 bis 255. HTML wird nicht interpretiert, Ausgabe stets escaped.
3. `work_date` in einem existierenden, nicht abgeschlossenen Schuljahr (INV-01, INV-04), nicht in der Zukunft (INV-03). Fehler: Zukunft `400 DATE_IN_FUTURE`, kein Schuljahr `400 NO_SCHOOL_YEAR`, abgeschlossenes Schuljahr `409 SCHOOL_YEAR_CLOSED`. Über die Familienendpunkte zusätzlich nur im **laufenden** Schuljahr (D-29, INV-14), sonst `400 DATE_OUTSIDE_CURRENT_SCHOOL_YEAR`. Die Prüfungen laufen in dieser Reihenfolge. Admins tragen über `POST /admin/families/{id}/entries` auch in andere offene Schuljahre ein.
4. Familie des Nutzers (INV-02). `created_by` ist der angemeldete Nutzer.
5. **Duplikatschutz:** Ein inhaltlich identischer Eintrag (gleiche Familie, Datum, Startzeit, Dauer, Beschreibung) innerhalb von 2 Minuten wird mit `409 DUPLICATE_ENTRY` abgelehnt `[PROPOSED]`. Prüfung und Einfügen laufen unter `pg_advisory_xact_lock` auf die Familie, damit ein Doppelklick nicht zwei Einträge erzeugt.
6. Rate-Limit: höchstens 60 Einträge pro Nutzer und Stunde `[PROPOSED]`.
7. Neuer Eintrag hat Status `OPEN`.

#### Ändern und Löschen

- Familienmitglieder: nur eigene Familie, nur `OPEN` (INV-05, INV-06). Ein `CONFIRMED`-Eintrag liefert `409 ENTRY_CONFIRMED`. Löschen ist Soft-Delete.
- Familienmitglieder ändern und löschen nur Einträge des laufenden Schuljahres (D-29, INV-14). Ein Eintrag aus einem anderen Schuljahr liefert `409 SCHOOL_YEAR_NOT_CURRENT` (nach `CONCURRENT_MODIFICATION`, `ENTRY_CONFIRMED` und `SCHOOL_YEAR_CLOSED`). Ein neues Datum muss ebenfalls im laufenden Schuljahr liegen (Regel 3).
- Admins: alle Einträge, siehe INV-07.
- Jede Änderung und Löschung erzeugt einen Audit-Eintrag.
- Änderungen per `PATCH` als JSON Merge Patch (RFC 7396, 8.1). Änderbar sind nur `workDate`, `startTime`, `durationMinutes`, `description`. Andere oder unbekannte Felder (z. B. `status`, `familyId`, `createdBy`) führen zu `400 VALIDATION_FAILED`.
- Änderungen nutzen optimistisches Locking (`version` im Body, 8.1). Konflikt: `409 CONCURRENT_MODIFICATION`. **Jede** Änderung eines Eintrags erhöht `version`, auch Bestätigen und Zurücksetzen auf `OPEN`.

#### UX-Ziel

Ein Standardeintrag ("1 h, heute") MUSS in höchstens **4 Interaktionen** möglich sein (Öffnen, Chip, Text, Absenden). Siehe UI-04.

---

### 7.5 FS-CONF: Bestätigung durch Admins (F7)

1. Admin-Ansicht "Zu bestätigen": alle `OPEN`-Einträge, gruppierbar nach Familie, filterbar nach Familie und Zeitraum, sortiert nach Erfassungsdatum (älteste zuerst).
2. Aktionen: einzeln bestätigen, alle offenen Einträge einer Familie bestätigen, Mehrfachauswahl bestätigen.
3. Bestätigen setzt `status = CONFIRMED`, `confirmed_by`, `confirmed_at` und schreibt Audit `ENTRY_CONFIRMED`. Die Operation ist **idempotent** (bereits bestätigte Einträge bleiben unverändert, kein Fehler).
4. Sammelbestätigung als ein SQL-Update, das auch abgeschlossene Schuljahre ausschließt und `version` erhöht:
   ```sql
   UPDATE time_entry te
      SET status = 'CONFIRMED', confirmed_by = :adminId, confirmed_at = :now,
          updated_at = :now, version = te.version + 1
     FROM school_year sy
    WHERE te.id = ANY(:ids) AND te.status = 'OPEN' AND te.deleted_at IS NULL
      AND sy.id = te.school_year_id AND sy.closed_at IS NULL
   ```
   Einträge aus abgeschlossenen Jahren in der Auswahl bleiben unverändert. Die Antwort nennt die Anzahl bestätigter und übersprungener Einträge.
5. Admins können Einträge vor der Bestätigung **bearbeiten** oder **löschen** (z. B. falsche Dauer). Ein Ablehnen mit Begründung und Rückfrage an die Familie gibt es zunächst nicht (OPEN-11).
6. Zähler offener Einträge erscheint als Badge in der Admin-Navigation.
7. In abgeschlossenen Schuljahren ist Bestätigen gesperrt (INV-04). Einzelbestätigung: `409 SCHOOL_YEAR_CLOSED`.

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
| `page`, `size` | Standard `0`, `50`. Höchstwert `size` = 200, darüber `400 PAGE_SIZE_EXCEEDED` |

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
| Nutzer | Suchen, Zuordnung zu Familie setzen oder entfernen (F5), Rolle setzen (D-13), deaktivieren, alle Sessions beenden. |
| Schuljahre | Anlegen, Label, Stichtage und `required_minutes` ändern (INV-09, Konflikt mit bestehenden Einträgen: `409 ENTRIES_OUTSIDE_RANGE`, Überlappung: `409 SCHOOL_YEAR_OVERLAP`), abschließen und wieder öffnen (OPEN-06). |
| Import | CSV-Import für Kinder `[PROPOSED]`, siehe unten. Quelle der Kinderliste: OPEN-03. |

#### CSV-Import Kinder `[PROPOSED]`

- Spalten: `first_name;last_name;birth_date;family_key;external_ref` (Trennzeichen `;`, UTF-8, Datum `YYYY-MM-DD` oder `DD.MM.YYYY`). Kinder mit gleichem `family_key` bilden eine Familie.
- **Vorschau (Dry-Run)** vor dem Schreiben: Anzahl neu, geändert, unverändert, fehlerhafte Zeilen mit Zeilennummer.
- Idempotent: Zuordnung bestehender Kinder über `external_ref`, sonst über normalisierten Namen plus Geburtsdatum.
- Der Import läuft in **einer Transaktion**. Bei Fehlern: alles oder nichts.
- Datei wird nicht gespeichert. Größenlimit 1 MB, darüber `413`.

---

### 7.8 FS-PDF: Abrechnung (F10, D-12)

#### Arten

| Art | Bedingung | Kennzeichnung im PDF |
|---|---|---|
| `INTERIM` (Zwischenstand) | Schuljahr läuft noch | "Zwischenstand, Stand {Datum Uhrzeit}" |
| `FINAL` (Abschluss) | Schuljahr beendet (`ends_on < heute`) oder `closed_at` gesetzt | "Abschluss Schuljahr {Label}" |

#### Umfang (D-12: beides)

| Scope | Inhalt |
|---|---|
| `ALL` | Gesamtübersicht aller soll-pflichtigen Familien: Familie, Kinder, bestätigte Stunden, Soll, fehlend, Status. Summenzeile (Anzahl Familien, davon erfüllt). |
| `FAMILY` | Einzelabrechnung einer Familie: Kinder, alle **bestätigten** Einträge (Datum, Zeit, Dauer, Tätigkeit, bestätigt am), Summe, Soll, Ergebnis. Separat darunter: "Noch nicht bestätigt (zählt nicht)" mit den offenen Einträgen. |

#### Endpunkt

`GET /api/v1/admin/reports/billing?schoolYearId=…&scope=ALL|FAMILY&familyId=…&kind=INTERIM|FINAL` liefert `application/pdf` (Attachment). Ohne `schoolYearId` das aktuelle Schuljahr, ohne `kind` die zum Schuljahr passende Art (oben). `familyId` ist bei `FAMILY` Pflicht. Dateiname: `elternstunden_{label}_{scope}_{yyyy-MM-dd}.pdf` (Label mit `/` zu `-`).

#### Regeln

1. Layout: A4 hochkant, Kopfzeile mit Name des Horts und Schuljahr, Fußzeile "Seite x/y" und Erstellzeitpunkt (Europe/Berlin). Schriften eingebettet. Optisch an die Hort-Website angelehnt (Design-Tokens aus UI-01).
2. Datenminimierung: **keine** Geburtsdaten, **keine** E-Mail-Adressen im PDF. Kinder mit Vor- und Nachnamen, sonst nichts.
3. Dauer als `x h y min`. Summe ebenso.
4. Generierung synchron, Ziel höchstens 5 Sekunden bei 1.000 Familien (NFR-03).
5. PDFs werden **nicht** gespeichert, sondern bei Bedarf erzeugt. Jede Erzeugung schreibt einen Audit-Eintrag (`REPORT_GENERATED`, Parameter, keine Inhalte).
6. Ein PDF für `FINAL` in einem noch nicht abgeschlossenen Schuljahr ist nicht möglich (Fehler `SCHOOL_YEAR_NOT_FINISHED`).
7. **Keine Injection, kein Fremdzugriff:** Bei HTML-Vorlagen nur eine Template-Engine mit Auto-Escaping (Freitexte wie `description` erscheinen als Text). Der Renderer lädt **keine** externen Ressourcen: ein eigener Resolver erlaubt nur Schriften und Bilder aus dem Classpath, alles andere (http, file, data) wird verweigert.

---

### 7.9 FS-MASTER: Master-Admin (D-13, D-22)

#### Bootstrap

Umgebungsvariablen:

| Variable | Bedeutung |
|---|---|
| `MASTER_ADMIN_EMAIL` | E-Mail des Master-Admins |
| `MASTER_ADMIN_PASSWORD_HASH` | Argon2id-Hash (im Spring-Security-Format). **Nie** Klartext in der Konfiguration. Mindestparameter: m = 19456 KiB, t = 2, p = 1 (OWASP). |
| `MASTER_ADMIN_DISPLAY_NAME` | Optional, Standard "Master-Admin" |
| `MASTER_ADMIN_TOTP_SECRET` | TOTP-Secret (Base32). In Produktion Pflicht `[PROPOSED]`. Wird nur aus der Konfiguration gelesen, **nicht** in der DB gespeichert. |

Beim Start (Profil `prod`):

1. Fehlen `MASTER_ADMIN_EMAIL`, `MASTER_ADMIN_PASSWORD_HASH` oder `MASTER_ADMIN_TOTP_SECRET`, oder kodiert der Hash schwächere Argon2id-Parameter als oben, bricht der Start mit klarer Fehlermeldung ab (**Fail Fast**).
2. Existiert ein Nutzer mit `managed_by_config = true`, wird er aktualisiert (E-Mail, Hash, Name). Sonst wird er angelegt (`role = MASTER_ADMIN`, `status = ACTIVE`, `email_verified_at` = jetzt, `family_id` NULL).
3. Ein Nutzer mit gleicher E-Mail, der **nicht** der verwaltete ist, führt zum Startabbruch mit Hinweis (Konflikt manuell auflösen).
4. Konfiguration ist die Quelle der Wahrheit: Der Hash aus der Umgebung überschreibt bei jedem Start den DB-Wert. Das TOTP-Secret liegt nur in der Konfiguration.

#### Verhalten

- Anmeldung nur über den eigenen Master-Login (Passwort plus TOTP, FS-AUTH 7.2), mit strengeren Limits (SEC-04). **Kein OIDC, kein E-Mail-Link** (D-22, INV-12). Damit funktioniert der Notzugang auch, wenn Mailversand oder Identitätsanbieter ausfallen.
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
| Registrierung bestätigen | Registrierung Weg A mit neuer oder noch unbestätigter Adresse (7.1.4) | E-Mail-Link (15 Minuten gültig). Hinweis: Ohne Klick wird die Registrierung nach 24 Stunden gelöscht, ein neuer Link lässt sich auf der Anmeldeseite anfordern. |
| Bereits registriert | Registrierung mit bereits bekannter Adresse (7.1.4) | Hinweis "Du hast schon ein Konto" plus E-Mail-Link (15 Minuten gültig) |
| Anmeldelink | Anfrage `POST /auth/magic-link` für ein aktives oder unbestätigtes Konto (7.2) | E-Mail-Link (15 Minuten gültig, einmal einlösbar) |
| Kein Konto | Anfrage `POST /auth/magic-link` für eine unbekannte Adresse (7.2), höchstens eine pro Adresse und 24 Stunden | Hinweis, dass es kein Konto gibt, Link auf die Registrierung. Kein Token. |
| Neue Zuordnung | Automatische Zuordnung zu Familie mit bestehenden Mitgliedern (7.1.6) | Name des neuen Mitglieds, Hinweis an Hort-Team |

Jede Mail mit Link enthält den Satz "Du hast diese Mail nicht angefordert? Dann ignoriere sie einfach." Der Link steht als Schaltfläche und zusätzlich als Klartext-URL in der Mail.

Technik: Spring Mail über SMTP. Die vier Auth-Mails (Registrierung bestätigen, Bereits registriert, Anmeldelink, Kein Konto) verschickt der Auth-Worker aus `auth_request` (7.2): persistent, bis zu 3 Versuche, nur innerhalb von 15 Minuten. Andere Mails (Neue Zuordnung, Alarme) gehen **asynchron** mit bis zu 3 Wiederholungen raus. Ein Versandfehler wird geloggt (ohne Adresse und ohne Token im Klartext) und führt **nicht** zu einer anderen Antwort an den Client (kein Orakel). Absenderadresse und SMTP-Dienst: OPEN-07. Zugangsdaten nur per Umgebungsvariable oder Docker Secret. Seit D-24 ist der Mailversand betriebskritisch (OPS-13).

---

### 7.12 FS-DEV: Rollenwechsel für manuelle Tests (D-26) `[PROPOSED]`

Ziel: Beim manuellen Testen in Sekunden zwischen den Rollen wechseln, ohne jedes Mal einen E-Mail-Link oder TOTP-Code zu brauchen.

- **Nur im Profil `dev`.** Die Beans für Seed und Endpunkt existieren nur mit dem Profil `dev`. Ist zusätzlich `prod` aktiv, bricht der Start ab (Fail Fast). Im Produktions-Build des Frontends fehlt die Dev-Leiste vollständig.
- **Demo-Konten:** Ein Seed nur für `dev` (eigener Flyway-Ort, z. B. `classpath:db/dev`, idempotent) legt an:
  - `familie@dev.local`: `FAMILY_MEMBER` mit Demo-Familie
  - `partner@dev.local`: zweites Mitglied derselben Familie
  - `unzugeordnet@dev.local`: `FAMILY_MEMBER` ohne Familie, mit Claim
  - `admin@dev.local`: `ADMIN`
  - Master: das per Konfiguration verwaltete Konto mit Dev-Werten (7.9)
  - Ab M2 kommen Demo-Schuljahr, Kinder und Einträge dazu.
- **Endpunkt:** `POST /api/v1/dev/login-as` mit `{account}` (eines der Demo-Konten). Er meldet über denselben Codepfad an wie der E-Mail-Link (7.2 "Anmeldung technisch": neue Session-ID, neuer CSRF-Token, Rollen-Timeouts) und überspringt nur Mail und TOTP. Im Vertrag steht er mit Tag `dev`, `x-roles: [A]` und der Erweiterung `x-dev-only: true`.
- **Dev-Leiste im Frontend:** Nur im Vite-Dev-Server (`import.meta.env.DEV`). Sie ist klein und unten eingeblendet, zeigt Rolle und Konto und hat je eine Schaltfläche pro Demo-Konto sowie "Abmelden".
- Der echte Ablauf (E-Mail-Link über Mailpit, Google über den OIDC-Mock) bleibt lokal voll nutzbar.

---

## 8. API-Konventionen und Übersicht

### 8.1 Konventionen

| Thema | Regel |
|---|---|
| Basis | `/api/v1`, JSON (UTF-8). Integration getrennt unter `/api/integration/v1` (FS-INT). |
| Zeit | Datum RFC 3339 `full-date` (`YYYY-MM-DD`). Zeitstempel RFC 3339 `date-time` in UTC mit `Z`. Uhrzeit als String `HH:mm` mit `pattern: ^([01]\d\|2[0-3]):[0-5]\d$` (bewusst **nicht** `format: time`, das verlangt Sekunden und Offset). Fachliche Datumswerte sind Europe/Berlin-Kalendertage. |
| Dauer | Immer ganze **Minuten** (`durationMinutes`). |
| IDs | UUID als String. |
| Fehlerformat | `application/problem+json` (RFC 9457): `type` = `about:blank`, `title` = HTTP-Statustext, `status`, `detail` (kurzer technischer Text ohne PII). Erweiterungen: `code` (stabiler, maschinenlesbarer Fehlercode, z. B. `LINK_INVALID`), `traceId` und bei Validierungsfehlern `errors: [{field, code, message}]` ohne den abgelehnten Wert. Die UI zeigt Texte aus ihrer Ressourcendatei anhand von `code` (UI-02). Keine Stacktraces. Gilt für **alle** Fehler unter `/api/**`, auch für `401`, `403`, CSRF und `429` aus Security-Filtern und für unbekannte Pfade. |
| Statuscodes | `200/201/204` Erfolg, `202` angenommen (neutrale Antworten), `400` Validierung (auch fachliche Regelverletzungen am Request, kein `422`), `401` nicht angemeldet, `403` keine Berechtigung, `404` nicht gefunden (auch bei fremden IDs, kein Existenz-Orakel), `409` Konflikt mit dem Zustand (Version, Duplikat, abgeschlossenes Jahr), `413` Upload zu groß, `429` Rate-Limit mit `Retry-After` in Sekunden. Für `/api/**` gibt es **nie** eine Weiterleitung (`302`) zum Login. |
| Paging | `page` (ab 0), `size` (Standard 50, höchstens 200, darüber `400 PAGE_SIZE_EXCEEDED`). Antwort als explizites DTO `PageResponse<T>`: `{content: [...], page, size, totalElements, totalPages}`. Spring-`Page` wird nie direkt serialisiert. |
| PATCH | JSON Merge Patch (RFC 7396, `application/merge-patch+json` oder `application/json`). Nur dokumentierte Felder sind änderbar. Unbekannte Felder führen zu `400` (Jackson `fail-on-unknown-properties=true`, Schutz vor Mass Assignment). |
| Nebenläufigkeit | Optimistisches Locking über `version` im Request-Body. Konflikt: `409 CONCURRENT_MODIFICATION`. Kein `ETag`/`If-Match`. |
| Request-Größe | JSON-Bodies höchstens 64 KB, CSV-Import höchstens 1 MB (`413`). |
| CSRF | Alle zustandsändernden Requests (`POST/PUT/PATCH/DELETE`) tragen den CSRF-Token (Cookie `XSRF-TOKEN`, Header `X-XSRF-TOKEN`), auch anonyme Auth-Endpunkte. |
| CORS | **Aus.** Same-Origin-Betrieb (SPA und API über dieselbe Domain). |
| Dokumentation | **API-first** (D-25): Der Vertrag `api/openapi.yaml` (OpenAPI 3.0.3, 4.2) beschreibt jede Operation mit `operationId`, `tags`, Fehlerantworten (`problem+json`) und Beispielen. Die Erweiterung `x-roles` nennt die erlaubten Rollen (Kürzel aus 8.2) und ist Pflicht. Ein Test gleicht Vertrag und registrierte Handler ab: Jede Operation hat einen Handler, und es gibt keinen `/api`-Handler außerhalb des Vertrags. Doku-Endpunkte gibt es in der App nicht (kein springdoc). Der TypeScript-Client wird aus dem Vertrag generiert, nicht von Hand gepflegt. |
| Versionierung | Brechende Änderungen nur mit neuer Major-Version im Pfad. Für die Integrations-API (FS-INT) wird eine Abkündigung mit den Headern `Deprecation` (RFC 9745) und `Sunset` (RFC 8594) mindestens 6 Monate vorher angekündigt. |
| Idempotenz | `DELETE` und Bestätigung sind idempotent. `POST /auth/oidc/complete` ist idempotent (7.1.5). Doppelte Einträge: siehe FS-ENTRY. |

### 8.2 Endpunktübersicht `[PROPOSED]`

Rollen: **A** = anonym, **U** = angemeldet (jede Rolle), **F** = `FAMILY_MEMBER` mit Familie, **AD** = `ADMIN` oder `MASTER_ADMIN`, **M** = `MASTER_ADMIN`, **OP** = Session im Zustand `OIDC_PENDING`, **MP** = Session im Zustand `MFA_PENDING` (7.2). OP und MP haben **nur** die hier ausdrücklich genannten Endpunkte, alle anderen liefern `401`.

| Methode und Pfad | Rolle | Spezifikation |
|---|---|---|
| `GET /auth/captcha` | A | SEC-04, FS-REG |
| `POST /auth/register` | A | FS-REG Weg A (immer `202`) |
| `POST /auth/magic-link` | A | E-Mail-Link anfordern (immer `202`), FS-AUTH |
| `POST /auth/magic-link/redeem` | A | E-Mail-Link einlösen, legt Session an, FS-AUTH |
| `POST /auth/master/login` | A | Master-Login mit Passwort plus TOTP (D-22), FS-AUTH |
| `POST /auth/mfa/verify` | MP | Zweiter Faktor für Admins (7.2) |
| `POST /auth/logout` | U, OP, MP | FS-AUTH |
| `GET /oauth2/authorization/{id}`, `GET /login/oauth2/code/{id}` | A | FS-AUTH OIDC (Spring-Standardpfade, außerhalb `/api/v1`) |
| `GET /auth/oidc/pending` | OP | Daten für Schritt "Noch ein Schritt" (FS-REG 7.1.5) |
| `POST /auth/oidc/complete` | OP | Kinderdaten absenden, Konto anlegen (idempotent) |
| `GET /me` | U | Profil, Rolle, Familienstatus, Verknüpfungen |
| `PATCH /me` | U | Anzeigename ändern |
| `POST /me/identities/{provider}/link` | U | Liefert die Einstiegs-URL zum Verknüpfen, frische Anmeldung nötig (`403 REAUTH_REQUIRED`) |
| `DELETE /me/identities/{id}` | U | Nur **eigene** Identitäten (SEC-08), Entfernen immer erlaubt (INV-11) |
| `PUT /me/claim` | U (unzugeordnet) | Kinderdaten eingeben oder korrigieren (max. 3 Korrekturen), zugeordnet: `409 ALREADY_ASSIGNED` |
| `POST /me/totp/setup`, `POST /me/totp/confirm`, `DELETE /me/totp` | AD | Admin-TOTP (7.2, OPEN-09) |
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
| `PUT /admin/users/{id}/family`, `DELETE /admin/users/{id}/family` | AD | Zuordnung (F5), volle Familie: `409 FAMILY_FULL` (D-28) |
| `PUT /admin/users/{id}/role` | AD | Rolle (`FAMILY_MEMBER` oder `ADMIN`) |
| `POST /admin/users/{id}/disable`, `…/enable`, `…/terminate-sessions` | AD | FS-ADM, Deaktivieren beendet alle Sessions (7.2) |
| `POST /admin/users/{id}/anonymize` | AD | DP-05 (a), nicht umkehrbar, beendet alle Sessions |
| `DELETE /admin/users/{id}/totp` | AD | Admin-TOTP eines anderen Admins zurücksetzen (7.2) |
| `GET /admin/entries?status=OPEN` | AD | FS-CONF |
| `POST /admin/entries/{id}/confirm`, `POST /admin/entries/confirm` (Bulk) | AD | FS-CONF |
| `POST /admin/families/{id}/entries`, `PATCH/DELETE /admin/entries/{id}` | AD | Korrekturen (INV-07) |
| `GET/POST /admin/school-years`, `PATCH /admin/school-years/{id}`, `POST …/close`, `POST …/reopen` | AD | FS-ADM, 5.5 |
| `POST /admin/school-years/{id}/enrollments/copy-previous` | AD | Einschreibung aus Vorjahr übernehmen (7.7) |
| `GET /admin/reports/billing` | AD | FS-PDF |
| `GET /admin/audit` | AD | SEC-11 |
| `GET /master/backups/status`, `POST /master/backups/run` | M | Abschnitt 11 |
| `GET/POST/DELETE /master/api-clients` | M | FS-INT |
| `GET /status` | A | Öffentliche, einfache Prüfseite ohne Details (OPS-07) |
| `POST /dev/login-as` | A, nur Profil `dev` | Rollenwechsel für manuelle Tests (FS-DEV 7.12, `x-dev-only`) |
| `GET /actuator/health/**` | intern | nur im Docker-Netz, nicht über Caddy (OPS-07) |

---

## 9. Sicherheit

Grundhaltung: Die App verarbeitet Daten von Minderjährigen. Sicherheit hat Vorrang vor Komfort, wo beides kollidiert. Alle Werte "Standard" sind `[PROPOSED]` und per Konfiguration änderbar.

| ID | Anforderung |
|---|---|
| SEC-01 | **Deny by default.** Jede Endpunkt-Methode deklariert ihre Rolle explizit (Abschnitt 6). Ein nicht deklarierter Endpunkt ist gesperrt. Ein Test MUSS alle Endpunkte gegen den API-Vertrag prüfen: Jede Operation hat `x-roles` (D-25, 8.1), und die tatsächliche Rollenregel stimmt damit überein (AC-SEC-09). |
| SEC-02 | **Transport.** Nur HTTPS. HTTP leitet auf HTTPS um. HSTS (`max-age=31536000`, `includeSubDomains` erst nach stabiler Domain-Einrichtung). TLS-Zertifikate automatisch über Caddy. |
| SEC-03 | **Passwort nur für den Master-Admin (D-22, D-24).** Nutzerkonten haben kein Passwort (`password_hash` NULL, per CHECK-Constraint erzwungen). Das Master-Passwort wird mit Argon2id gehasht (Spring `Argon2PasswordEncoder`, mindestens m = 19456 KiB, t = 2, p = 1, nicht die schwächeren Spring-Defaults; Parameter so wählen, dass ein Hash auf dem Ziel-VPS etwa 250 bis 500 ms dauert). Der Hash kommt ausschließlich aus der Konfiguration (OPS-12) und lässt sich über UI und API nicht ändern. |
| SEC-04 | **Bot-Schutz und Rate-Limits** (siehe 9.1). |
| SEC-05 | **Zweiter Faktor (TOTP, RFC 6238).** `MASTER_ADMIN`: Pflicht in Produktion, Secret nur aus der Konfiguration (7.9). `ADMIN`: optional aktivierbar, Pflicht per Schalter `app.security.admin-totp-required` (OPEN-09, neu bewertet, weil seit D-24 das Mailkonto der Admin-Zugang ist). Ablauf und Endpunkte: FS-AUTH 7.2. ±1 Zeitschritt, jeder Schritt nur einmal gültig. Admin-Secret verschlüsselt gespeichert (AES-GCM, Schlüssel `APP_ENCRYPTION_KEY`), 10 Einmal-Wiederherstellungscodes (nur als Hash gespeichert). |
| SEC-06 | **OIDC-Härtung.** Authorization Code mit PKCE (**explizit** konfiguriert, `requireProofKey`), `state` und `nonce` Pflicht. Prüfen: Signatur, `iss`, `aud`, `exp`, `nonce`. `email_verified = true` Pflicht. Identitätsschlüssel ist `(iss, sub)`. Automatisches Verknüpfen per E-Mail nur, wenn der Anbieter für die Adresse autoritativ ist (7.1.5). Feste, in der Konfiguration hinterlegte Redirect-URIs. Scopes nur `openid profile email`. Access-, Refresh- und ID-Tokens werden **nicht** gespeichert, weder in der DB noch in der Session (`OAuth2AuthorizedClientRepository` als No-op, eigener Principal, 7.2). Logout beendet nur die lokale Session. |
| SEC-07 | **Injection.** Ausschließlich parametrisierte Abfragen. Sortierfelder und Filter aus Requests nur über Whitelist. Eingaben per Bean Validation. Im Frontend kein `dangerouslySetInnerHTML`, Ausgaben sind escaped. |
| SEC-08 | **Zugriffskontrolle gegen IDOR.** `family_id` für `FAMILY_MEMBER` kommt **immer aus der Session**, nie aus Pfad oder Body. Zugriffe auf fremde Ressourcen liefern `404`. Pro Endpunkt MUSS es einen Test "Nutzer aus Familie A greift auf Daten von Familie B zu" geben. **Nutzergebundene** Ressourcen (Identitäten, Claims, TOTP) werden auf die `user_id` aus der Session gefiltert, auch gegenüber Mitgliedern derselben Familie. |
| SEC-09 | **Session und CSRF.** Session-Cookie `__Host-SESSION`, `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/`, ohne `Domain`. Session-Fixation-Schutz (neue Session-ID und neuer CSRF-Token nach jeder Anmeldung, 7.2). CSRF-Token für alle zustandsändernden Requests. Logout invalidiert serverseitig. Anonyme Auth-Endpunkte legen keine Session an. |
| SEC-10 | **HTTP-Header** (Caddy und Spring): `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`. Zusätzlich `X-Content-Type-Options: nosniff`, `Referrer-Policy: same-origin`, `Permissions-Policy` ohne Kamera/Mikrofon/Geolocation, HSTS. Keine Inline-Skripte, keine externen Ressourcen (Schriften, Skripte, Bilder) von Dritten. ALTCHA wird im Build `altcha/external` mit separatem Worker-Skript und CSS eingebunden (kein `blob:`-Worker, damit die CSP ohne Ausnahme auskommt). Navigation zum OIDC-Anbieter nur per Top-Level-GET, nie als Formular-Weiterleitung (sonst greift `form-action 'self'`). |
| SEC-11 | **Audit-Log.** Protokolliert werden mindestens: E-Mail-Link angefordert (nur Nutzer-ID, bei unbekannter Adresse nur als Metrik), eingelöst und ungültig, Master-Login-Erfolg und -Fehlschlag (ohne Passwort und Code), Rollenänderungen, Zuordnungen (auto und manuell), Bestätigungen, Änderungen und Löschungen von Einträgen, Stammdatenänderungen, Schuljahr-Änderungen, PDF-Erzeugung, API-Client-Verwaltung, Backup-/Restore-Aktionen, Verknüpfen von OIDC-Identitäten. Die Tabelle ist **append-only**: Die App-Rolle der Datenbank erhält auf `audit_log` nur `INSERT` und `SELECT`, kein `UPDATE`, `DELETE` oder `TRUNCATE` (OPS-14) `[PROPOSED]`. `details` enthält nur IDs und technische Werte. Aufbewahrungsfrist: OPEN-16. |
| SEC-12 | **Logging ohne personenbezogene Daten.** Keine Passwörter, Tokens, E-Mail-Adressen, Kindernamen, Geburtsdaten oder Freitexte in Logs und Fehlermeldungen. Stattdessen IDs und `traceId`. JSON-Logs mit Korrelations-ID. IP-Adressen nur gekürzt (IPv4 /24, IPv6 /48). Caddy-Access-Log aus oder mit höchstens 7 Tagen Aufbewahrung. |
| SEC-13 | **Geheimnisse.** Nur per Umgebungsvariable oder Docker Secret, nie im Repository oder Image. CI prüft mit gitleaks. Rotation dokumentiert (Runbook OPS-08). |
| SEC-14 | **Lieferkette und Container.** Abhängigkeits-Scan (OWASP Dependency-Check oder Trivy, `npm audit`), SBOM (CycloneDX), Image-Scan in CI. Container: Nicht-Root-Nutzer, `read_only` wo möglich, `cap_drop: [ALL]`, `no-new-privileges`, Basis-Images fixiert (Digest), automatische Update-PRs (Renovate oder Dependabot). |
| SEC-15 | **Server-Härtung** (VPS): SSH nur mit Schlüssel, Root-Login aus, Firewall nur 22/80/443, fail2ban, automatische Sicherheitsupdates. Befehle in OPS-06. |
| SEC-16 | **Fehlerbehandlung.** Auth-Fehler immer neutral formuliert. Keine Unterscheidung zwischen bekanntem und unbekanntem Konto (Registrierung, Link-Anfrage) und keine Angabe, warum ein Link ungültig ist (unbekannt, abgelaufen, benutzt, ersetzt). Master-Login: keine Unterscheidung zwischen falscher E-Mail, falschem Passwort und falschem Code. Keine Stacktraces nach außen. |
| SEC-17 | **E-Mail-Link-Härtung** (FS-AUTH 7.2). Token mit 256 Bit aus einem CSPRNG, base64url. In der DB nur der SHA-256-Hash. Gültig 15 Minuten, einmal einlösbar, unter Zeilensperre entwertet (paralleles Einlösen ergibt genau eine Session). Eine neue Anfrage macht ältere Token ungültig. Link-Basis nur aus `APP_BASE_URL`, nie aus Request-Headern. Der Token steht im URL-Fragment, nie im Query-String, in Logs, Fehlermeldungen oder Metriken. Ein GET verbraucht nie einen Token, eingelöst wird nur per `POST` mit CSRF-Token. Nach dem Einlösen gibt es eine neue Session-ID. Links funktionieren geräteübergreifend, auf einem fremden Gerät erst nach Rückfrage mit maskierter Adresse (`auth_binding`, OPEN-21). Anonyme Auth-Anfragen laufen über die Outbox `auth_request` (gleiche Antwort und Zeit für alle Fälle). Limits gegen Mail-Bombing laut 9.1. |

### 9.1 SEC-04 im Detail: Bot-Schutz und Rate-Limits

**Captcha: ALTCHA** `[PROPOSED]` (D-07). Ablauf:

1. `GET /api/v1/auth/captcha` liefert eine signierte Challenge (HMAC-SHA-256 mit `ALTCHA_HMAC_KEY`, mindestens 32 zufällige Byte, nur serverseitig).
2. Das Widget im Browser löst die Proof-of-Work-Aufgabe (ohne Nutzerinteraktion) und liefert einen Nachweis (`payload`).
3. Der Server verifiziert **jeden** Nachweis mit der offiziellen ALTCHA-Java-Bibliothek: Signatur gültig, nicht abgelaufen (Gültigkeit 5 Minuten), **nicht wiederverwendet** (Replay-Schutz: bereits eingelöste Challenges bis zum Ablauf im Speicher oder in der DB vermerken).
4. Schwierigkeit (`maxNumber`) so einstellen, dass das Lösen auf einem durchschnittlichen Smartphone etwa 1 bis 3 Sekunden dauert. Wert konfigurierbar.
5. Das Widget wird **lokal gebündelt** (kein CDN, SEC-10).
6. Pflicht bei: Registrierung Weg A und E-Mail-Link anfordern. Nicht beim Einlösen eines Links und nicht beim Master-Login (Notzugang, dort greifen strenge IP-Limits).

**Rate-Limits** (Standardwerte, konfigurierbar; Antwort `429` mit `Retry-After`):

| Ziel | Schlüssel | Standard |
|---|---|---|
| Registrierung Weg A | IP | 30 pro Stunde, 100 pro Tag (großzügig, weil Familien bei einem Elternabend oder im Hort-WLAN aus demselben Netz kommen; der Schutz liegt bei Captcha, Zielkind- und globalem Limit) |
| Registrierung gesamt | global | 200 pro Stunde, bei Überschreitung Alarm an Master |
| **Zuordnungsversuche pro Zielkind** | `claim_key` | **5 pro 24 Stunden**, über alle Wege und IPs (aus `registration_claim`, unter Advisory-Lock gezählt, überlebt Neustarts, 7.1.4 Schritt 5) |
| **Mails mit E-Mail-Link (Mail-Bombing)** | Ziel-E-Mail, Registrierung und Link-Anfrage zusammen | **5 pro Stunde**. Geprüft im Auth-Worker. Bei Überschreitung kein Token und keine Mail (stilles Verwerfen, Metrik), die Antwort war schon `202`. |
| Mail "Kein Konto" | Ziel-E-Mail | **1 pro 24 Stunden**, danach still verwerfen (Spam- und Reputationsschutz, OPS-13) |
| E-Mail-Link anfordern | IP | 20 pro Stunde |
| E-Mail-Link anfordern | global | 200 pro Stunde, bei Überschreitung Alarm an Master |
| E-Mail-Link einlösen | IP | 30 pro 15 Minuten |
| OIDC-Callback | IP | 30 pro Minute |
| Master-Login und MFA-Prüfung | IP | 5 pro 15 Minuten, danach `429` bis zum Ablauf des Fensters |
| Master-Login | Konto | Keine Sperre (Notzugang). Ab 10 Fehlversuchen pro Stunde Alarm-Mail an den Master (7.2). |
| Eintrag erfassen | Nutzer | 60 pro Stunde |
| Integration API | Client | 60 pro Minute |

Umsetzung: Bucket4j (oder gleichwertig) im Speicher für IP-, Adress- und Nutzerlimits, mit begrenztem Speicher (z. B. Caffeine, höchstens 100.000 Schlüssel, Ablauf nach dem längsten Fenster). Schlüssel:

- IP: IPv4 als /32, IPv6 als **/64-Präfix** (sonst trivial umgehbar).
- E-Mail: `HMAC-SHA-256(APP_ENCRYPTION_KEY, normalisierte E-Mail)`, nie die Adresse im Klartext.
- Die **IP** MUSS aus dem vom Caddy gesetzten Header kommen. Spring MUSS so konfiguriert sein, dass nur dem Caddy-Container vertraut wird (`server.forward-headers-strategy` und Trusted-Proxy-Einstellung), sonst ist das Limit per Header-Fälschung umgehbar.
- Das Limit pro Zielkind ist bewusst **datenbankbasiert**.

**Bekannte Restschwäche (bewusst akzeptiert, D-05):** Name plus Geburtsdatum eines Kindes sind für Dritte (z. B. andere Eltern) teils bekannt oder erratbar. Gegenmaßnahmen: Captcha und Limits (oben), Zuordnung erst nach Einlösen des E-Mail-Links, Benachrichtigung der Familie und Admin-Widget bei automatischer Zuordnung (7.1.6), Familien sehen nur Vornamen der Kinder und keine Kontaktdaten. Verschärfungen (z. B. Admin-Freigabe neuer Mitglieder) sind in OPEN-12 offen.

**Restschwächen des E-Mail-Links (bewusst akzeptiert, D-24):**

- Wer das Postfach (oder das Google-Konto) eines Nutzers kontrolliert, kontrolliert dessen Konto. Bei Admins betrifft das den Zugang zu allen Kinderdaten (OPEN-09).
- Anfragen für fremde Adressen erzeugen Mails an Dritte ("Kein Konto", "Anmeldelink"). Das begrenzen Captcha und die Limits oben ("Kein Konto" höchstens einmal pro Tag und Adresse).
- Wiederholte Anfragen für eine fremde Adresse machen den zuletzt verschickten Link des Opfers ungültig. Auch das begrenzt das Limit je Ziel-E-Mail.
- Ein fremder Link kann einem Nutzer untergeschoben werden. Die Rückfrage mit maskierter Adresse auf einem fremden Gerät (7.2, OPEN-21) macht das sichtbar, verhindert es aber nicht, wenn der Nutzer trotzdem bestätigt.
- Mail-Scanner, die JavaScript ausführen und klicken, erreichen höchstens die Rückfrage für ein fremdes Gerät. Den Token verbrauchen sie nur, wenn sie auch die Rückfrage bestätigen.
- Vorregistrierung einer fremden Adresse mit falschen Kinderdaten: Der Claim wird nur im selben Browser angewendet (7.1.4 Schritt 7), bei Aktivierung per Google wird er verworfen (7.1.5). Es bleibt ein Bedienärgernis: Das Opfer gibt die Kinderdaten neu ein.

---

## 10. Datenschutz

Hinweis: Dieser Abschnitt beschreibt technische und organisatorische Anforderungen. Er ersetzt **keine Rechtsberatung**. Rechtsgrundlagen, Texte und Fristen sind mit dem Verein und dessen Datenschutzbeauftragten zu klären (OPEN-16).

| ID | Anforderung |
|---|---|
| DP-01 | **Datenkategorien:** Kinder (Vorname, Nachname, Geburtsdatum), Eltern (Name, E-Mail, ggf. OIDC-Kennung `sub`), Elternstunden (Datum, Dauer, Freitext), Audit-Log, Sessions, Anmeldetoken (nur Hash, höchstens 15 Minuten gültig, danach gelöscht). Keine besonderen Kategorien nach Art. 9 DSGVO vorgesehen. In der UI steht beim Freitext der Hinweis "Bitte keine sensiblen Daten eintragen." |
| DP-02 | **Datensparsamkeit und Need-to-know.** Familien sehen nur Vornamen ihrer Kinder und Anzeigenamen der Mitglieder. PDFs enthalten keine Geburtsdaten und keine E-Mail-Adressen. Integrations-Scopes trennen Geburtsdatum von übrigen Kinderdaten. |
| DP-03 | **Transparenz.** Seiten "Datenschutz" und "Impressum" sind aus jedem Screen erreichbar, auch vor dem Login. Texte liefert der Verein (Platzhalter bis dahin). Bei Registrierung Weg A: Pflicht-Checkbox "Ich habe die Datenschutzhinweise gelesen." Bei Weg B: Hinweis vor dem Redirect, welche Daten der Anbieter erhält und welche zurückkommen (Name, E-Mail). |
| DP-04 | **OIDC (Google) datenschutzfreundlich.** Freiwillig, die Anmeldung per E-Mail-Link bleibt gleichwertig verfügbar. **Kein** JavaScript-SDK und keine Ressourcen des Anbieters auf Seiten vor dem Klick. Erst der Klick auf den Button leitet zum Anbieter weiter (Datenübermittlung an einen Anbieter möglicherweise außerhalb der EU). Angeforderte Daten: nur `openid`, `profile`, `email`. Rechtliche Bewertung: OPEN-16. |
| DP-05 | **Löschung und Anonymisierung.** Es gibt Admin-Funktionen: (a) Nutzer anonymisieren (E-Mail wird `deleted-{id}@invalid`, Name "Gelöschter Nutzer", Identitäten und Tokens gelöscht, Status `DISABLED`; Einträge bleiben erhalten, `created_by` zeigt auf den anonymisierten Nutzer). Unbestätigte Registrierungen, die nie aktiv waren, werden nach 24 Stunden physisch gelöscht (7.1.4 Schritt 9). Kinderdaten in Claims können auch von Unbefugten stammen: Ein Job löscht Claims 30 Tage nach `resolved_at` oder `superseded_at` sowie alle Claims eines anonymisierten Nutzers. Der jeweils gültige Claim eines unzugeordneten Nutzers bleibt, bis ein Admin zuordnet. (b) Kind anonymisieren (Namen auf Initialen, Geburtsdatum auf das Jahr) nach Austritt und Ablauf der Aufbewahrungsfrist. (c) Familie löschen nur, wenn keine Daten in Aufbewahrungsfrist hängen. Fristen: OPEN-16. In Backups wirkt Löschung mit Ablauf der Backup-Aufbewahrung (BAK-02), das ist zu dokumentieren. |
| DP-06 | **Betroffenenrechte.** Berichtigung durch Admins (FS-ADM). Auskunft und Export: Admin-Funktion "Daten einer Familie exportieren" (JSON und PDF) `[PROPOSED]`, Phase 2. |
| DP-07 | **Auftragsverarbeitung.** Mit dem Hoster (IONOS), dem SMTP-Anbieter (OPEN-07, er verarbeitet alle E-Mail-Adressen und Anmeldelinks) und jedem Cloud-Backup-Anbieter (Google Drive oder Microsoft, falls genutzt) ist ein Auftragsverarbeitungsvertrag zu schließen. Hosting in Deutschland oder der EU. Backups werden **clientseitig verschlüsselt** (restic, rclone crypt), Cloud-Anbieter sehen nur Chiffretext. |
| DP-08 | **Dokumentation.** Verzeichnis der Verarbeitungstätigkeiten und TOM-Beschreibung führt der Verein. Die Abschnitte 9, 11 und 12 dieses Dokuments sind dafür die technische Grundlage. |
| DP-09 | **Keine Tracker.** Keine Analytics, keine externen Schriften, keine CDNs, keine Social-Plugins. Cookies: nur technisch notwendige (Session, CSRF, `auth_binding` für die Anmeldung). Ob dadurch kein Cookie-Banner nötig ist, bestätigt der Datenschutzbeauftragte des Vereins. |

---

## 11. Backup und Restore (N5, D-19)

### 11.1 Anforderungen

| ID | Anforderung |
|---|---|
| BAK-01 | **Umfang:** PostgreSQL-Datenbank als logischer Dump (`pg_dump --format=custom`). Konfigurationsdateien (Compose, Caddyfile) liegen im Git-Repository. **Geheimnisse** (Passwörter, Schlüssel, `.env`) liegen **nicht** im Repository, sondern im Passwortmanager des Betreibers. Weitere persistente Daten gibt es nicht (keine Uploads). **Nicht** gesichert werden die Inhalte von `spring_session*`, `user_token` und `auth_request` (`pg_dump --exclude-table-data=…`). Nach einem Restore sind damit alle alten Sessions und Links ungültig. Der Dump läuft mit der Owner-Rolle (OPS-14). |
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
        --format=custom --file="$DUMP" \
        --exclude-table-data='spring_session*' \
        --exclude-table-data=user_token --exclude-table-data=auth_request   # BAK-01
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

Umgebung des Containers: `RESTIC_REPO_S3=s3:https://<IONOS_S3_ENDPOINT>/<bucket>`. Der S3-Zugang (nur für diesen Bucket berechtigt) liegt als Datei-Secret `backup_s3_credentials` vor (`AWS_SHARED_CREDENTIALS_FILE=/run/secrets/backup_s3_credentials`), **nicht** als Umgebungsvariable. Der **Endpunkt** steht in der IONOS-Dokumentation unter "Endpoints" und ist vor Einrichtung nachzuschlagen (Platzhalter, nicht geraten). Optional `RESTIC_REPO_RCLONE=rclone:<remote>:<pfad>` mit `rclone.conf` (Typ `crypt` über `drive` bzw. `onedrive`) als Read-only-Mount unter `/home/backup/.config/rclone/` (der Container läuft als Nicht-Root-Nutzer `backup`, SEC-14).

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
5. Der Restore läuft mit der Owner-Rolle (`POSTGRES_USER`, OPS-14), daher gehören die Objekte danach wieder ihr. Die App-Rolle ist eine Cluster-Rolle und steckt nicht im Dump. Sie MUSS vorher existieren (legt das DB-Init-Skript an, OPS-14). Die Rechte (`GRANT`) stellt der Dump wieder her.
6. Nach dem Restore sind alle Sessions und offenen Links ungültig (BAK-01). Nutzer melden sich neu an.

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
      DB_USER: elternstunden_app          # App-Rolle (OPS-14)
      FLYWAY_USER: ${POSTGRES_USER}       # Owner, nur für Migrationen (OPS-14)
      MAIL_FROM: ${MAIL_FROM}
      SMTP_HOST: ${SMTP_HOST}
      SMTP_PORT: ${SMTP_PORT}
      SMTP_USER: ${SMTP_USER}
      MASTER_ADMIN_EMAIL: ${MASTER_ADMIN_EMAIL}
      GOOGLE_CLIENT_ID: ${GOOGLE_CLIENT_ID}
      JAVA_TOOL_OPTIONS: "-XX:MaxRAMPercentage=60"
    secrets:
      - db_password                       # Owner-Passwort (Flyway)
      - db_app_password                   # App-Rolle (Datasource)
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
    image: postgres:18.x@sha256:…        # Version und Digest fixieren, siehe 4.2 und SEC-14
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}     # Owner-Rolle (OPS-14)
      POSTGRES_PASSWORD_FILE: /run/secrets/db_password
      TZ: Europe/Berlin
    secrets: [db_password, db_app_password]
    volumes:
      - pgdata:/var/lib/postgresql        # ab postgres:18 liegt PGDATA unter /var/lib/postgresql/18/docker
      - ./ops/db/init:/docker-entrypoint-initdb.d:ro   # legt die App-Rolle an (OPS-14)
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
      AWS_SHARED_CREDENTIALS_FILE: /run/secrets/backup_s3_credentials
    secrets:
      - db_password                       # Owner-Rolle, nur lesend genutzt (pg_dump)
      - backup_s3_credentials
      - restic_password_s3
      - restic_password_rclone
    volumes:
      - backup_state:/state
      - backup_trigger:/trigger
      - ./secrets/rclone.conf:/home/backup/.config/rclone/rclone.conf:ro   # Nicht-Root-Nutzer backup
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
  db_app_password:            { file: ./secrets/db_app_password }
  backup_s3_credentials:      { file: ./secrets/backup_s3_credentials }
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

- Spring liest die Secrets über `configtree:/run/secrets/`: Der Dateiname entspricht dem Property-Namen (Mapping in der `application-prod.yml`, z. B. `spring.datasource.password` aus `db_app_password` und `spring.flyway.password` aus `db_password`). Die genaue Zuordnung legt die Implementierung fest, Secrets DÜRFEN NICHT als normale Umgebungsvariablen im Compose stehen.
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
| `APP_BASE_URL` | Env | Basis-URL für Links in Mails, einzige Quelle dafür (SEC-17) |
| `APP_VERSION` | Env | Image-Tag |
| `POSTGRES_DB`, `POSTGRES_USER` | Env | Datenbank und Owner-Rolle (OPS-14) |
| `db_password` | **Secret** | Passwort der Owner-Rolle (Flyway, Backup, Restore) |
| `db_app_password` | **Secret** | Passwort der App-Rolle `elternstunden_app` (OPS-14) |
| `app_encryption_key` | **Secret** | AES-GCM-Schlüssel für Admin-TOTP-Secrets und HMAC-Schlüssel der Rate-Limit-Schlüssel (32 Byte, Base64) |
| `altcha_hmac_key` | **Secret** | HMAC-Schlüssel der Captcha-Challenges (mindestens 32 Byte) |
| `master_admin_password_hash` | **Secret** | Argon2id-Hash des Master-Passworts |
| `master_admin_totp_secret` | **Secret** | Base32-Secret für das Master-TOTP, in Produktion Pflicht |
| `MASTER_ADMIN_EMAIL`, `MASTER_ADMIN_DISPLAY_NAME` | Env | Master-Konto (FS-MASTER) |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `MAIL_FROM` | Env | Mailversand (OPEN-07), betriebskritisch (OPS-13) |
| `smtp_password` | **Secret** | SMTP-Zugang |
| `GOOGLE_CLIENT_ID` | Env | OIDC-Client (Google) |
| `google_client_secret` | **Secret** | OIDC-Client-Secret |
| `RESTIC_REPO_S3`, `RESTIC_REPO_RCLONE` | Env | Backup-Ziele (11.3) |
| `backup_s3_credentials` | **Secret** | S3-Zugang für Backup im Format der AWS-Credentials-Datei (11.3) |
| `restic_password_s3`, `restic_password_rclone` | **Secret** | Repository-Passwörter |
| `TZ` | Env | `Europe/Berlin` |

**OIDC-Voraussetzung:** Für Google muss im Google-Cloud-Projekt ein OAuth-Client samt Zustimmungsbildschirm angelegt werden (Datenschutz-URL und Domain erforderlich, Details bei der Einrichtung prüfen). Die Redirect-URI lautet `https://{APP_DOMAIN}/login/oauth2/code/google`. Sie setzt eine **fertige Domain mit TLS** voraus (OPEN-13). Für die Entwicklung wird ein getrennter Client oder ein lokaler Testanbieter genutzt. Damit der Redirect-URI-Host und das Schema hinter dem Proxy stimmen, MUSS `server.forward-headers-strategy` passend gesetzt sein.

### 12.5 CI/CD (GitHub Actions) `[PROPOSED]`

| Job | Inhalt |
|---|---|
| `contract` | Lint des API-Vertrags (Redocly, D-25) |
| `backend` | Code-Generierung aus dem Vertrag, Build, Unit- und Integrationstests (Testcontainers mit PostgreSQL), Coverage-Report |
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
| OPS-07 | **Monitoring:** `/actuator/health/liveness` und `/readiness` nur im Docker-Netz (eigener Management-Port, Caddy leitet nur `/api` und die SPA weiter). Externer Uptime-Check auf eine öffentliche, einfache Prüfseite (z. B. `/api/v1/status` ohne Details). Alarm per Mail an den Master. Plattenplatz, Container-Neustarts und Mailversand (OPS-13) überwachen. Metriken (Micrometer) nur intern. |
| OPS-08 | **Runbooks** (in `/docs/runbooks`, jeweils Schritt für Schritt): Deployment, Rollback, Restore (11.4), Totalverlust (11.5), **Master-Zugang verloren** (siehe unten), **Mailversand gestört** (OPS-13), Secrets rotieren, Admin-Rolle vergeben, OS- und Image-Updates, Zertifikatsproblem. |
| OPS-09 | **Domain und TLS:** A/AAAA-Eintrag auf den VPS, Ports 80/443 erreichbar, Caddy holt Zertifikate selbst. Die Domain ist Voraussetzung für OIDC und Mailversand (SPF, DKIM, DMARC, siehe OPS-13). Offen: OPEN-13. |
| OPS-10 | **Ressourcen** (VPS M+ mit 4 GB): `app` 1 GB, `db` 768 MB, `caddy` 128 MB, `backup` 256 MB, Rest für Betriebssystem. JVM mit `-XX:MaxRAMPercentage=60`. |
| OPS-11 | **Updates:** Renovate oder Dependabot erzeugt PRs. Monatliches Wartungsfenster für Basis-Images und OS. Sicherheitsupdates des OS automatisch (`unattended-upgrades`). |
| OPS-12 | Das Projekt liefert `ops/scripts/hash-password.sh`, das einen Argon2id-Hash im von der App erwarteten Format ausgibt (für `master_admin_password_hash`, das einzige Passwort im System, D-24), mit mindestens den Parametern aus 7.9. |
| OPS-13 | **Mailzustellung ist betriebskritisch (D-24).** Ohne Mail kann sich niemand per E-Mail-Link anmelden oder registrieren. Es bleiben nur OIDC und der Master-Login. Pflicht vor Go-live: SPF, DKIM und DMARC für die Absenderdomain (DMARC zunächst `p=quarantine` mit Berichten, nach stabiler Beobachtung `p=reject`). Versand über einen SMTP-Anbieter mit TLS (OPEN-07). Zustelltest bei Gmail, GMX, web.de, T-Online und Outlook. Monitoring der Versandfehler (Metrik) mit Alarm an den Master bei Ausfall oder erhöhter Fehlerquote. Nach jedem Deployment eine Test-Mail als Smoke-Test. |
| OPS-14 | **Zwei DB-Rollen** `[PROPOSED]`. (1) **Owner** = `POSTGRES_USER` (Secret `db_password`): besitzt das Schema, Flyway migriert damit (`spring.flyway.user`), Backup und Restore nutzen sie. (2) **App** = `elternstunden_app` (Secret `db_app_password`): Datasource der App, nur DML auf die Fachtabellen, auf `audit_log` nur `INSERT` und `SELECT`, kein DDL, kein `TRUNCATE`. Die App-Rolle legt ein Init-Skript des DB-Containers an (`ops/db/init/`, läuft nur bei leerem Volume, bei bestehendem Volume einmalig manuell, Runbook). Die Rechte vergibt eine Flyway-Migration (`GRANT … TO elternstunden_app`, auch `ALTER DEFAULT PRIVILEGES` für künftige Tabellen). In dev gilt dieselbe Aufteilung. Damit kann eine SQL-Injection über die App den Audit-Log nicht ändern (AC-SEC-06). |

**Runbook "Master-Zugang verloren":**

1. Per SSH als `deploy` auf den Server, ins Projektverzeichnis.
2. `./ops/scripts/hash-password.sh` ausführen, neues Passwort eingeben, Hash ausgeben lassen.
3. Hash in `secrets/master_admin_password_hash` schreiben (Rechte 600). Bei verlorenem TOTP zusätzlich ein neues Secret in `secrets/master_admin_totp_secret` hinterlegen.
4. `docker compose up -d --force-recreate app`.
5. Anmelden. Beim Start schreibt die App einen Audit-Eintrag `MASTER_CREDENTIALS_SYNCED` (Akteur `SYSTEM`).

**Runbook "Mailversand gestört"** (Kurzfassung, Details in `/docs/runbooks`):

1. Als Master anmelden (der Master-Login ist unabhängig von Mail).
2. Versandfehler-Metrik und App-Logs prüfen (`traceId`, keine Adressen in Logs).
3. SMTP-Anbieter-Status, Zugangsdaten (`smtp_password`) und DNS-Einträge (SPF, DKIM, DMARC) prüfen.
4. Nach der Behebung eine Test-Mail senden. Betroffene Nutzer fordern einfach einen neuen Link an (alte Links sind nach 15 Minuten ohnehin abgelaufen).

---

## 13. Frontend (D-14, D-16)

| ID | Anforderung |
|---|---|
| UI-01 | **Design-Tokens.** Alle Farben, Abstände, Radien, Schriften als CSS-Variablen an einer Stelle. Die Palette stammt aus Logo und Website des Horts (Auftraggeber, 2026-10-22, OPEN-19): Orange `#F28D13` (Haupt-Schaltflächen), Gelb `#FBBD16`, Limette `#8ABA27`, Türkis `#76ADB8`, Grün `#29A042`, Lila `#7573B4`, Überschriften `#7B6DA4` wie auf der Website, Text `#333333`. Das Logo-Zeichen ist als SVG nachgezeichnet. Logofarben stehen nie als Textfarbe auf Weiß (Kontrast). Kontrast mindestens WCAG AA. Systemschrift, Überschriften in der Handschrift der Website (Indie Flower, OFL, lokal gebündelt, keine externen Fonts, SEC-10). |
| UI-02 | **Texte externalisiert.** Alle sichtbaren Texte in einer Ressourcendatei (`de`), keine hartcodierten Strings in Komponenten. Formate über `Intl` (de-DE). Anrede: Du, freundlich, kurz. |
| UI-03 | **Mobile first.** Basis 360 px Breite, einspaltig. Tippflächen mindestens 44×44 px. Wichtige Aktionen im Daumenbereich (unten). Keine Hover-Abhängigkeit. Safe-Area-Insets beachten. Keine horizontale Scrollleiste ab 320 px. Ab 768 px: Admin-Listen als Tabelle, darunter als Karten. Zielgeräte: aktuelle und vorherige Version von iOS Safari und Android Chrome, dazu aktuelle Desktop-Browser. |
| UI-04 | **Erfassung in höchstens 4 Interaktionen** für den Standardfall (Öffnen, Dauer-Chip, Text, Absenden). Primäre Schaltfläche "Stunden eintragen" ist auf der Familienübersicht dauerhaft erreichbar. Erfassung als Bottom-Sheet. Datum standardmäßig heute. Nach dem Absenden: Hinweis "Eingetragen. Wartet auf Bestätigung." und aktualisierte Fortschrittsanzeige. |
| UI-05 | **Screens** (Tabelle unten). |
| UI-06 | **Login und Registrierung (ohne Passwort, D-24).** Oben "Mit Google anmelden" (Link auf `/oauth2/authorization/google`), dann Trenner "oder", dann ein Feld "E-Mail" und die Schaltfläche "Anmeldelink schicken". Es gibt kein Passwortfeld. Nach dem Absenden: "Schau in dein Postfach. Der Link ist 15 Minuten gültig." mit der Möglichkeit, erneut zu senden. Die Bestätigungsseite `/login/link` zeigt eine große Schaltfläche "Jetzt anmelden". Wurde der Link auf einem anderen Gerät angefordert, folgt die Rückfrage "Dieser Link wurde auf einem anderen Gerät angefordert. Als m\*\*\*@gmail.com anmelden?" mit "Ja, anmelden" und "Abbrechen" (7.2). Bei ungültigem Link erscheint der Text aus 7.2 plus das Feld zum erneuten Anfordern. Der Google-Button MUSS die Branding-Vorgaben des Anbieters einhalten (Prüfung vor Go-live). Keine Anbieter-Skripte (DP-04). |
| UI-07 | **Fortschrittsanzeige.** Balken oder Ring: bestätigt als Vollfläche, offen als schraffiert oder heller. Text: "4 h 30 min von 10 h bestätigt", darunter "plus 1 h wartet auf Bestätigung". Bei Erfüllung: "Geschafft! Eure 10 Stunden sind erfüllt." |
| UI-08 | **Zustände.** Lade-Skelette, freundliche Leerzustände ("Noch keine Stunden eingetragen. Tippe auf 'Stunden eintragen'."), Fehlermeldungen verständlich und ohne technische Details, Inline-Validierung mit denselben Regeln wie serverseitig (Zod). Offline: Banner "Keine Verbindung". Es gibt **kein** Offline-Schreiben. |
| UI-09 | **Barrierefreiheit WCAG 2.2 AA.** Semantisches HTML, beschriftete Felder, sichtbarer Fokus, `aria-live` für Rückmeldungen, `aria-sort` in Tabellenköpfen, Bedienung per Tastatur, Skalierung bis 200 %, `prefers-reduced-motion` beachten. |
| UI-10 | **PWA.** Manifest (Name, Icons, Theme-Farbe), installierbar. Service Worker cacht nur die App-Hülle, **nie** API-Antworten. Update-Hinweis bei neuer Version. Kein Push. |
| UI-11 | **Admin-Listen.** Suchfeld oben (Debounce 250 ms, mindestens 2 Zeichen), sortierbare Spaltenköpfe, Filter und Suche in der URL (teilbar, Zurück-Button funktioniert), Sammelaktionen mit Mehrfachauswahl. Hinweisleiste (FS-ADMOV) auf allen Admin-Seiten. |
| UI-12 | **Datum und Uhrzeit.** Datumsfelder als eigenes Eingabefeld im Format `TT.MM.JJJJ` (Punkte werden beim Tippen ergänzt) mit deutschem Kalender zum Aufklappen, unabhängig von der Browsersprache (D-27). Uhrzeiten als Textfeld im 24-Stunden-Format `HH:MM`. Eingaben wie `9`, `9:00`, `9.30`, `930` oder `1530` werden beim Verlassen des Felds zu `09:00`, `09:30` bzw. `15:30` ergänzt, auch über den Ziffernblock ohne Doppelpunkt. Anzeige de-DE. Datumsgrenzen (`min`, `max`) aus dem Schuljahr und "nicht in der Zukunft". |
| UI-13 | **Datenschutz und Impressum** aus jedem Screen erreichbar (Fußzeile), auch ohne Login. |

### 13.1 Screens (UI-05)

| Route | Rolle | Inhalt |
|---|---|---|
| `/login` | anonym | Google-Button, E-Mail-Feld "Anmeldelink schicken" (mit Captcha), danach Hinweis "Schau in dein Postfach", Link "Registrieren" |
| `/register` | anonym | Weg A: Name, E-Mail, Kinderdaten, Captcha, Datenschutz-Checkbox. Daneben "Mit Google registrieren" |
| `/register/claim` | OIDC-Pending | "Noch ein Schritt": Kinderdaten, Anzeigename |
| `/login/link` | anonym | Bestätigungsseite für den E-Mail-Link: Token aus dem URL-Fragment, Schaltfläche "Jetzt anmelden", bei fremdem Gerät Rückfrage mit maskierter Adresse, erst der Klick löst ein (7.2) |
| `/login/master` | anonym | Master-Login: E-Mail, Passwort, TOTP-Code. Von `/login` aus nicht verlinkt (7.2) |
| `/pending` | unzugeordnet | Wartescreen mit Kinderdaten-Korrektur (max. 3). Ohne gültigen Claim (7.1.4 Schritt 7, 7.1.5): Formular "Gib die Daten deines Kindes ein" |
| `/login/mfa` | MFA-Pending | Eingabe TOTP-Code oder Wiederherstellungscode (Admins, 7.2) |
| `/` | Familie | Familienübersicht (FS-FAM): Fortschritt, Schaltfläche "Stunden eintragen", Einträge, Partnerhinweis |
| `/entries/new` (als Sheet über `/`) | Familie | Erfassen (FS-ENTRY) |
| `/family` | Familie | Mitglieder, Kinder (Vornamen), Partnerhinweis |
| `/profile` | angemeldet | Anzeigename, E-Mail (nur Anzeige, Anmeldeweg), Verknüpfungen (Google), Abmelden, ggf. TOTP einrichten und Wiederherstellungscodes (Admins) |
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

- React, TypeScript (`strict`), Vite. Datenzugriff mit TanStack Query über den aus dem Vertrag generierten Client (orval, D-25). Formulare mit React Hook Form und den aus dem Vertrag generierten Zod-Schemas, damit Client und Server dieselben Regeln prüfen (UI-08).
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
| NFR-04 | **Qualität:** Kritische Pfade (Normalisierung und Abgleich, Zeitregeln und Schuljahr-Logik, Rechteprüfung, Fallunterscheidungen bei Registrierung, E-Mail-Link und OIDC) MÜSSEN vollständig durch Tests abgedeckt sein, messbar als mindestens 90 % **Branch**-Abdeckung (JaCoCo) in den betroffenen Klassen. Service-Schicht mindestens 80 % Zeilenabdeckung. Die Grenzen prüft der Build (`jacocoTestCoverageVerification`). Mutationstests (PIT) KANN man ergänzen. Statische Analyse: TypeScript `strict`, ESLint, im Backend Compiler-Warnungen als Fehler plus ein Linter-Werkzeug. |
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
| AC-REG-01 | **Treffer.** Gegeben Kind "Anna Müller", geboren 2017-05-03, in Familie F. Wenn sich ein Elternteil mit gültigem Captcha mit "mueller", "anna", 03.05.2017 registriert und den E-Mail-Link im selben Browser einlöst, dann ist `family_id` = F, es gibt einen Audit-Eintrag `USER_ASSIGNED_TO_FAMILY` mit `method=AUTO_MATCH`. |
| AC-REG-02 | **Kein Orakel.** Statuscode, Body und Antwort-Header von `POST /auth/register` sind identisch für: Treffer, kein Treffer, bereits registrierte E-Mail (aktiv und unbestätigt), deaktiviertes Konto, Master-E-Mail, `THROTTLED`. Ein Test vergleicht die Antworten. |
| AC-REG-03 | **Zweiter Elternteil.** Registriert sich ein zweiter Elternteil mit denselben Kinderdaten, landet er in Familie F. Für das erste Mitglied wechselt `partnerHint` auf `false` und es erhält die Mail "Neue Zuordnung". |
| AC-REG-04 | **Tippfehler.** Bei "Mueler" (falsch) bleibt der Nutzer nach dem Einlösen des E-Mail-Links unzugeordnet (`NO_MATCH`). Er sieht den Wartescreen. Der Admin sieht ihn im Widget mit Kandidat "Anna Müller". Nach Zuordnung durch den Admin sieht der Nutzer die Familienübersicht. |
| AC-REG-05 | **Limit pro Zielkind.** Bei 6 Registrierungen mit identischen Kinderdaten innerhalb 24 Stunden von verschiedenen IPs ist ab der 6. das Ergebnis `THROTTLED` ohne Kind-Lookup. Das Limit überlebt einen Neustart der App. |
| AC-REG-06 | **Captcha.** Fehlender, ungültiger, abgelaufener oder wiederverwendeter Nachweis führt bei `POST /auth/register` und `POST /auth/magic-link` zu `400 CAPTCHA_INVALID`. |
| AC-REG-07 | **Geschwister.** Hat Familie F zwei Kinder, genügt die Angabe eines der beiden. |
| AC-REG-08 | **Mehrdeutig.** Passen Name und Geburtsdatum auf Kinder in **verschiedenen** Familien, ist das Ergebnis `AMBIGUOUS` und der Nutzer bleibt unzugeordnet. |
| AC-REG-09 | **Normalisierung.** Alle Testvektoren aus 7.1.3 ergeben die genannten Werte (Unit-Test). |
| AC-REG-10 | **Zugang und Zuordnung erst nach dem Klick.** Nach der Registrierung und vor dem Einlösen des Links (`POST /auth/magic-link/redeem`) ist das Konto `PENDING_VERIFICATION`, `family_id` ist NULL, `email_verified_at` ist NULL, es gibt keine Session und geschützte Endpunkte liefern `401`. Nach dem Einlösen ist das Konto `ACTIVE`, `email_verified_at` ist gesetzt, eine Session besteht und die Zuordnung ist erfolgt (bei `MATCHED`). |
| AC-REG-11 | **Korrektur durch Nutzer.** Ein unzugeordneter Nutzer kann die Kinderdaten höchstens 3-mal korrigieren, danach `409`. Jede Korrektur zählt für das Limit pro Zielkind. |
| AC-REG-12 | **Aufräumen.** Ein Konto, das 24 Stunden nach der Registrierung noch `PENDING_VERIFICATION` ist, wird vom Job samt `registration_claim` und `user_token` gelöscht. Ein jüngeres unbestätigtes Konto und alle aktiven Konten bleiben unberührt (Test mit simulierter Uhr). |
| AC-REG-13 | **Paralleler Claim.** 10 gleichzeitige Registrierungen mit gleichem `claim_key` ergeben höchstens 5 Ergebnisse, die nicht `THROTTLED` sind. |
| AC-REG-14 | **Parallele E-Mail.** Zwei gleichzeitige Registrierungen derselben E-Mail ergeben zweimal `202`, genau ein Konto und keinen `5xx`. |
| AC-REG-15 | **Claim nur im selben Browser.** Konto `PENDING_VERIFICATION` mit Claim aus Browser X: Einlösen des Links in Browser Y (nach Rückfrage) oder Aktivierung per OIDC führt zu **keiner** automatischen Zuordnung. Der Claim ist verworfen, der Nutzer landet auf `/pending` und gibt die Kinderdaten neu ein. Eine erneute Registrierung derselben Adresse ersetzt den offenen Claim. |
| AC-REG-16 | **Benachrichtigung.** Bei automatischer Zuordnung erhalten alle aktiven Bestandsmitglieder die Mail "Neue Zuordnung". Das Admin-Widget zeigt die Zuordnung 14 Tage lang. |
| AC-REG-17 | **Volle Familie (D-28).** Familie F hat zwei Nutzer. Ein dritter Nutzer mit Treffer bleibt nach dem Einlösen des Links unzugeordnet (Wartescreen, Admin-Widget), die Mitglieder erhalten keine Mail. Die Zuordnung durch einen Admin liefert `409 FAMILY_FULL`, nach dem Entfernen eines Mitglieds gelingt sie. Zwei gleichzeitige Zuordnungen in eine Familie mit einem Mitglied ergeben genau zwei Mitglieder. |

### 15.2 OIDC (FS-AUTH)

| ID | Kriterium |
|---|---|
| AC-OIDC-01 | **Neuer Nutzer.** Callback mit gültigem Token und `email_verified=true`, unbekannte E-Mail: Es entsteht noch **kein** DB-Konto. Nach Absenden der Kinderdaten existieren Konto (`ACTIVE`), `user_identity` und `registration_claim`. Bei Treffer ist die Familie sofort zugeordnet. |
| AC-OIDC-02 | **E-Mail nicht bestätigt.** `email_verified=false` führt zu Ablehnung ohne Konto. |
| AC-OIDC-03 | **Verknüpfen.** Existiert ein `ACTIVE`-Konto mit derselben (vom Anbieter verifizierten) E-Mail, wird die Identität verknüpft, der Nutzer angemeldet und `IDENTITY_LINKED` auditiert. Der E-Mail-Link funktioniert für dieses Konto weiterhin. |
| AC-OIDC-04 | **Unbestätigtes Konto.** Existiert ein `PENDING_VERIFICATION`-Konto mit derselben E-Mail, wird verknüpft, das Konto ist danach `ACTIVE` mit gesetztem `email_verified_at` und der Nutzer ist angemeldet. Der vorhandene Claim ist verworfen (`superseded_at`), es erfolgt **keine** automatische Zuordnung, der Nutzer landet auf `/pending`. |
| AC-OIDC-05 | **Master.** Ein OIDC-Login mit der E-Mail des Master-Admins wird abgelehnt. |
| AC-OIDC-06 | **Manipulation.** Falscher `state`, falscher `nonce`, falsches `aud`, falscher `iss`, abgelaufenes Token oder ungültige Signatur führen zur Ablehnung (Test mit gemocktem Anbieter). |
| AC-OIDC-07 | **Verknüpfung entfernen.** Auch die einzige OIDC-Identität eines Kontos lässt sich entfernen (`204`). Danach meldet sich der Nutzer per E-Mail-Link an (INV-11). |
| AC-OIDC-08 | **Keine Tokenspeicherung.** Access-, Refresh- und ID-Token des Anbieters liegen weder in der DB noch in der Session. In `spring_session_attributes` steht nach dem Login nur der eigene Principal, kein `OAuth2AuthorizedClient` und kein `OidcUser` (Test auf Persistenz). |
| AC-OIDC-09 | **PKCE.** Der Authorization-Request enthält `code_challenge` mit `code_challenge_method=S256`. Ein Callback ohne passenden `code_verifier` wird abgelehnt. |
| AC-OIDC-10 | **Pending ohne Rechte.** Eine Session im Zustand `OIDC_PENDING` erhält auf allen Endpunkten außer `/auth/oidc/pending`, `/auth/oidc/complete` und `/auth/logout` `401`. Der Zustand verfällt nach 15 Minuten. |
| AC-OIDC-11 | **Deaktiviert über Identität.** Bei vorhandener `(iss, sub)` und Kontostatus `DISABLED` wird abgelehnt. |
| AC-OIDC-12 | **Nicht autoritative E-Mail.** Google-Token ohne `hd` und ohne `@gmail.com`, gleiche E-Mail wie ein bestehendes Konto: Es wird **nicht** automatisch verknüpft, die Meldung verweist auf E-Mail-Link plus Verknüpfung im Profil. |
| AC-OIDC-13 | **Verknüpfen nur frisch.** Liegt die letzte Anmeldung länger als 5 Minuten zurück, liefert `POST /me/identities/{provider}/link` `403 REAUTH_REQUIRED`. |

### 15.3 Anmeldung (FS-AUTH)

| ID | Kriterium |
|---|---|
| AC-AUTH-01 | **Neutrale Anfrage.** `POST /auth/magic-link` liefert für aktive, unbestätigte, unbekannte und deaktivierte E-Mail sowie für die Master-E-Mail identischen Status (`202`), Body und Header. Ein Test vergleicht die Antworten. |
| AC-AUTH-02 | **Scanner-Prefetch.** Ein `GET` auf den Link aus der Mail (inklusive aller Ressourcen und API-Aufrufe, die die Bestätigungsseite ohne Klick auslöst) verbraucht den Token nicht und legt keine Session an. Ein anschließender `POST /auth/magic-link/redeem` meldet an. Es gibt keinen Endpunkt, der einen Token per `GET` einlöst (E2E und Integrationstest). |
| AC-AUTH-03 | **Einmalig.** Ein zweites Einlösen desselben Tokens liefert `400 LINK_INVALID`, auch direkt nach dem ersten Erfolg. |
| AC-AUTH-04 | **Abgelaufen.** Ein Token, der älter als 15 Minuten ist, liefert `400 LINK_INVALID` (Test mit simulierter Uhr, 14:59 ok, 15:01 abgelehnt). |
| AC-AUTH-05 | **Ersetzt.** Nach einer zweiten Anfrage für dieselbe E-Mail liefert der erste Token `400 LINK_INVALID`, der zweite meldet an. |
| AC-AUTH-06 | **Gleiche Fehlerantwort.** Unbekannter, abgelaufener, eingelöster und ersetzter Token liefern identischen Status, Body und Header. |
| AC-AUTH-07 | **Nur Hash gespeichert.** `user_token.token_hash` ist der SHA-256-Hash des Tokens aus der Mail. Der Klartext-Token steht weder in der DB noch in Logs (Log-Scan). Der Token kodiert mindestens 256 Bit. |
| AC-AUTH-08a | **Mail-Bombing je Adresse.** Sechs Anfragen für dieselbe Ziel-E-Mail innerhalb einer Stunde (Registrierung und Link-Anfrage gemischt, verschiedene IPs) liefern sechsmal `202`, aber es gehen nur fünf Mails raus (Prüfung über Mailpit oder Mail-Mock). Die Mail "Kein Konto" geht höchstens einmal pro 24 Stunden an dieselbe Adresse. |
| AC-AUTH-08b | **IP-Limit.** Die 21. Link-Anfrage einer IP innerhalb einer Stunde liefert `429` mit `Retry-After`. IPv6-Adressen aus demselben /64 zählen gemeinsam. Alle Grenzwerte sind konfigurierbar. |
| AC-AUTH-09 | **Unbekannte E-Mail.** Eine Link-Anfrage für eine unbekannte Adresse legt weder Konto noch Token an und verschickt die Mail "Kein Konto" mit Link auf die Registrierung. |
| AC-AUTH-10 | **Kein Link für Master und deaktivierte Konten.** Für die Master-E-Mail und ein `DISABLED`-Konto entsteht kein Token und keine Mail. Ein vorhandener Token eines inzwischen deaktivierten Kontos liefert beim Einlösen `400 LINK_INVALID`. |
| AC-AUTH-11 | Eine Admin-Session ohne Aktivität für 30 Minuten wird mit `401` beendet. |
| AC-AUTH-12 | `POST` ohne gültigen CSRF-Token wird mit `403` abgelehnt, auch beim Einlösen eines Links. |
| AC-AUTH-13 | **Session-Fixation.** Die Session-ID nach dem Einlösen unterscheidet sich von der davor. |
| AC-AUTH-14 | **Paralleles Einlösen.** 10 gleichzeitige `redeem`-Aufrufe mit demselben Token ergeben genau einmal `200`, neunmal `400 LINK_INVALID` und genau eine Session. |
| AC-AUTH-15 | **Keine Session anonym.** `POST /auth/register` und `POST /auth/magic-link` setzen in keinem Fall ein Session-Cookie. Die `Set-Cookie`-Header sind in allen Fällen (aktiv, unbestätigt, unbekannt, deaktiviert, Master) identisch. |
| AC-AUTH-16 | **Timing.** Über 200 Anfragen je Fall (unbekannt, aktiv, unbestätigt, Master) unterscheiden sich die Mediane der Antwortzeit von `/auth/magic-link` und `/auth/register` um höchstens 20 ms. |
| AC-AUTH-17 | **Link-Basis.** Ein Request mit gefälschtem `Host` oder `X-Forwarded-Host` erzeugt trotzdem einen Link auf `APP_BASE_URL`. Der Link hat die Form `/login/link#token=…` ohne Query-Parameter. |
| AC-AUTH-18 | **Sessions folgen dem Konto.** Nach Deaktivierung oder Anonymisierung liefert jede bestehende Session des Nutzers `401`. Nach Rollen- oder Familienwechsel gelten in bestehenden Sessions sofort die neuen Rechte. |
| AC-AUTH-19 | **Absolute Dauer.** Eine Familien-Session endet nach der absoluten Dauer trotz Aktivität, ebenso eine Admin-Session (Test mit kurzen konfigurierten Werten). |
| AC-AUTH-20 | **CSRF-Rotation.** Nach dem Einlösen ist der alte CSRF-Token ungültig, der neue funktioniert. |
| AC-AUTH-21 | **Aufräum-Job gegen Einlösen.** Ein Einlösen parallel zum Job führt nie zu einem gelöschten aktiven Konto. Ein unbestätigtes Konto mit noch gültigem Token wird nicht gelöscht. |
| AC-AUTH-22 | **Anderes Gerät.** Ohne passendes `auth_binding`-Cookie liefert `redeem` `409 OTHER_DEVICE_CONFIRMATION_REQUIRED` mit maskierter Adresse, der Token bleibt gültig. Mit `confirmOtherDevice: true` wird angemeldet. Im selben Browser gibt es keine Rückfrage. |
| AC-AUTH-23 | **Admin-TOTP.** Ein Admin mit eingerichtetem TOTP hat nach E-Mail-Link oder OIDC nur `MFA_PENDING` und erhält auf allen Endpunkten außer `/auth/mfa/verify` und `/auth/logout` `401`. Ein gültiger Code führt zur vollen Session mit neuer Session-ID, ein Wiederherstellungscode funktioniert genau einmal. Mit `admin-totp-required=true` muss ein Admin ohne TOTP es zuerst einrichten. |

### 15.4 Stunden erfassen und bestätigen (FS-ENTRY, FS-CONF)

| ID | Kriterium |
|---|---|
| AC-ENTRY-01 | **Schnellerfassung.** "1 h", heute, Text "Garten aufgeräumt": Eintrag `OPEN`, Familienübersicht zeigt `openMinutes` +60, `confirmedMinutes` unverändert. |
| AC-ENTRY-02 | **Raster.** Dauer 20 min, 0 min oder 735 min wird abgelehnt. 15 min und 720 min werden akzeptiert. |
| AC-ENTRY-03 | **Freitext Pflicht.** Leer, nur Leerzeichen oder 2 Zeichen: `400`. 3 und 255 Zeichen: ok. 256 Zeichen: `400`. In der UI ist das leere Feld beim Absenden rot markiert, der Zähler zeigt "n/255", mehr als 255 Zeichen lassen sich nicht eingeben. |
| AC-ENTRY-04 | **Datum.** Zukunft: `400 DATE_IN_FUTURE`. Datum in keinem Schuljahr: `400 NO_SCHOOL_YEAR`. Datum in abgeschlossenem Schuljahr: `409 SCHOOL_YEAR_CLOSED`. |
| AC-ENTRY-05 | **Exakt.** 2026-10-14, 15:30, 2 h 15 min wird mit `start_time=15:30` und `duration_minutes=135` gespeichert. |
| AC-ENTRY-06 | **Duplikat.** Identischer Eintrag innerhalb 2 Minuten: `409 DUPLICATE_ENTRY`. |
| AC-ENTRY-07 | **Fremde Familie.** Ändern oder Löschen eines Eintrags einer anderen Familie: `404`. |
| AC-ENTRY-08 | **Bestätigt ist gesperrt.** Familienmitglied ändert oder löscht einen `CONFIRMED`-Eintrag: `409 ENTRY_CONFIRMED`. |
| AC-ENTRY-09 | **Eigene offene Einträge** dürfen geändert und (soft) gelöscht werden. Gelöschte Einträge zählen nicht und erscheinen nicht. |
| AC-ENTRY-10 | **Nebenläufigkeit.** Zwei gleichzeitige Änderungen am selben Eintrag: eine liefert `409 CONCURRENT_MODIFICATION`. |
| AC-ENTRY-11 | **Doppelklick.** Zwei gleichzeitige identische `POST` ergeben genau einen Eintrag und einmal `409 DUPLICATE_ENTRY`. |
| AC-ENTRY-12 | **Bestätigen gegen Bearbeiten.** Ein Familien-`PATCH` mit altem `version` nach einer Sammelbestätigung ergibt `409 CONCURRENT_MODIFICATION`. Der Eintrag bleibt `CONFIRMED` und unverändert. |
| AC-ENTRY-13 | **Nur das laufende Schuljahr (D-29).** Schuljahre 2025/26 (beendet, nicht abgeschlossen) und 2026/27 (läuft). Ein Familienmitglied mit Datum aus 2025/26: `400 DATE_OUTSIDE_CURRENT_SCHOOL_YEAR`. Ein offener Eintrag aus 2025/26 hat `canEdit = canDelete = false`; Ändern oder Löschen liefert `409 SCHOOL_YEAR_NOT_CURRENT`, ebenso das Verschieben eines Eintrags aus 2026/27 nach 2025/26 `400 DATE_OUTSIDE_CURRENT_SCHOOL_YEAR`. Ein Admin trägt über `POST /admin/families/{id}/entries` für 2025/26 ein (`201`) und ändert den alten Eintrag (`200`). |
| AC-CONF-01 | **Bestätigen.** Admin bestätigt: Status `CONFIRMED`, `confirmed_by` und `confirmed_at` gesetzt, Minuten zählen für das Soll. Zweites Bestätigen ändert nichts und liefert Erfolg. |
| AC-CONF-02 | **Sammelbestätigung.** Bei 3 ausgewählten Einträgen werden alle bestätigt. Bereits bestätigte bleiben unverändert. |
| AC-CONF-03 | **Änderung nach Bestätigung.** Ändert ein Admin Dauer, Datum oder Text eines bestätigten Eintrags, wird er wieder `OPEN`, die bestätigte Summe sinkt, ein Audit-Eintrag entsteht. |
| AC-CONF-04 | Ein Familienmitglied, das einen Eintrag bestätigen will, erhält `403`. |
| AC-CONF-05 | In einem abgeschlossenen Schuljahr ist Bestätigen gesperrt: einzeln `409 SCHOOL_YEAR_CLOSED`, in der Sammelbestätigung werden solche Einträge übersprungen und als übersprungen gezählt. |

### 15.5 Schuljahr (5.5)

| ID | Kriterium |
|---|---|
| AC-SY-01 | Bei Schuljahren 2026/27 (1.9.2026 bis 31.8.2027) und 2027/28: Eintrag vom 2027-08-31 gehört zu 2026/27, 2027-09-01 zu 2027/28. |
| AC-SY-02 | Stichtage so ändern, dass vorhandene Einträge außerhalb lägen: `409 ENTRIES_OUTSIDE_RANGE`. |
| AC-SY-03 | Überlappende Schuljahre werden abgelehnt (DB-Constraint und Service). |
| AC-SY-04 | Der System-Job legt das nächste Schuljahr mit Standard-Stichtagen an, wenn es 90 Tage vor Beginn fehlt. Er legt kein Duplikat an. |

### 15.6 Übersichten (FS-FAM, FS-ADMOV)

| ID | Kriterium |
|---|---|
| AC-FAM-01 | `partnerHint` ist `true` bei genau einem Mitglied, bei zwei Mitgliedern `false`. |
| AC-FAM-02 | Ein unzugeordneter Nutzer erhält bei `GET /me/family/overview` `403 FAMILY_ASSIGNMENT_PENDING`. |
| AC-FAM-03 | Eine Familie sieht keine Nachnamen, Geburtsdaten oder E-Mail-Adressen von Kindern oder Mitgliedern. |
| AC-FAM-04 | **Unzugeordnet überall.** Alle F-Endpunkte (`overview`, `entries` GET/POST/PATCH/DELETE) liefern für unzugeordnete Nutzer `403 FAMILY_ASSIGNMENT_PENDING` (INV-10). |
| AC-OV-01 | **Hinweisleiste.** "n von m" stimmt mit den Daten überein. 600 bestätigte Minuten gelten als erfüllt, 599 nicht. Offene Minuten zählen nicht. |
| AC-OV-02 | **Keine Verdopplung.** Eine Familie mit 2 eingeschriebenen Kindern erscheint einmal, ihre Minuten werden nicht verdoppelt (Regressionstest zu 5.6). |
| AC-OV-03 | **Sortieren.** Standard `missingMinutes` absteigend. Jede erlaubte Spalte sortiert auf- und absteigend. Ein unbekanntes Sortierfeld liefert `400`. |
| AC-OV-04 | **Suche.** "mueller" findet "Müller". Suche findet über Familienbezeichnung, Kindernamen, Mitgliedsnamen und E-Mail. Weniger als 2 Zeichen: `400`. |
| AC-OV-05 | `size` über 200 liefert `400 PAGE_SIZE_EXCEEDED`. |
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
| AC-MASTER-03 | Das Master-Konto lässt sich über UI und API weder ändern noch löschen. OIDC-Login ist abgelehnt, und für die Master-E-Mail wird kein E-Mail-Link verschickt (AC-AUTH-10). |
| AC-MASTER-04 | Wird Hash oder TOTP in der Konfiguration geändert, gilt nach Neustart der neue Wert, das alte Passwort funktioniert nicht mehr. Ein `SYSTEM`-Audit-Eintrag entsteht. |
| AC-MASTER-05 | Ein `ADMIN` erhält auf `/master/*` und `/api/v1/master/*` `403`. |
| AC-MASTER-06 | **Fehlversuche.** Falsches Passwort, falscher TOTP-Code und unbekannte E-Mail ergeben identische Antworten (Status, Body, Header). Ein wiederverwendeter TOTP-Code im selben Zeitschritt wird abgelehnt. Das IP-Limit greift nach 5 Versuchen. Ab 10 Fehlversuchen pro Stunde geht eine Alarm-Mail raus, das Konto wird nicht gesperrt. |
| AC-MASTER-07 | **Fail Fast.** Ein Start im Profil `prod` ohne TOTP-Secret oder mit einem Argon2id-Hash unter den Mindestparametern (7.9) bricht ab. |

### 15.9 Sicherheit (Abschnitt 9)

| ID | Kriterium |
|---|---|
| AC-SEC-01 | Ein automatischer Test stellt sicher, dass **jeder** Endpunkt eine explizite Rollenregel hat (SEC-01). |
| AC-SEC-02 | IDOR-Suite: für jeden Endpunkt mit Ressourcen-ID greift ein Nutzer aus Familie A auf Daten von Familie B zu und erhält `404`. Für nutzergebundene Ressourcen (z. B. `DELETE /me/identities/{id}`) gilt das auch gegenüber dem Partner aus derselben Familie. |
| AC-SEC-03 | Antworten auf `/` und `/api/v1/me` enthalten die Header aus SEC-10. |
| AC-SEC-04 | Ein Log-Scan nach einem E2E-Durchlauf findet keine E-Mail-Adressen, Kindernamen, Freitexte oder Tokens (SEC-12). |
| AC-SEC-05 | Rate-Limits liefern `429` mit `Retry-After`. Die IP stammt nur aus dem vertrauten Proxy-Header, ein vom Client gesetzter `X-Forwarded-For` ändert das Limit nicht. |
| AC-SEC-06 | `audit_log` ist für die App-Rolle nicht änderbar und nicht löschbar (Test mit `UPDATE`, `DELETE` und `TRUNCATE` als `elternstunden_app`, OPS-14). |
| AC-SEC-07 | Container laufen als Nicht-Root, mit `cap_drop: ALL`. Der Image-Scan meldet keine kritischen Schwachstellen. |
| AC-SEC-08 | Ein ZAP-Baseline-Scan gegen die Test-Umgebung liefert keine Befunde ab "Medium" (manuell bewertet). |
| AC-SEC-09 | **Rollenmatrix.** Für jede Zeile aus 8.2 und jede Rolle (anonym, unzugeordnet, F, AD, M, OP, MP) wird der erwartete Status aus einer Tabelle geprüft (aus der OpenAPI generiert). |
| AC-SEC-10 | **Mass Assignment.** `PATCH /me/family/entries/{id}` mit `status`, `familyId`, `confirmedBy` oder `createdBy` im Body liefert `400` und ändert nichts. |
| AC-SEC-11 | **Prod-Exposition und Fehlerformat.** In Produktion sind `/v3/api-docs`, Swagger UI und `/actuator/**` über Caddy nicht erreichbar (`404`). Fehlerantworten unter `/api/**` sind `application/problem+json` ohne Stacktrace, auch bei `401`, `403`, CSRF-Fehler, `429` aus Filtern und unbekannten Pfaden. Kein `302` zum Login. |
| AC-SEC-12 | **PDF ohne Fremdzugriff.** Ein Eintrag mit HTML bzw. `<img src="http://…">` führt beim PDF-Rendern zu keinem Netzwerkzugriff und erscheint als Text. |
| AC-SEC-13 | **Audit-Inhalt.** Für jede Aktion aus SEC-11 entsteht genau ein Eintrag, ohne E-Mail, Namen oder Token in `details`. |

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
| AC-BAK-09 | **Ohne Sessions und Tokens.** Der Dump enthält keine Daten aus `spring_session*`, `user_token` und `auth_request`. Nach einem Restore sind alle alten Sessions ungültig. |

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

### 15.13 Mail (FS-MAIL)

| ID | Kriterium |
|---|---|
| AC-MAIL-01 | **Inhalte.** Jede Mail aus 7.11 hat einen Klartext- und einen HTML-Teil, keine externen Bilder und kein Tracking, den Satz zum Ignorieren und bei Links den Link als Schaltfläche und als Klartext (Prüfung über Mailpit). |
| AC-MAIL-02 | **Wiederholung.** Bei SMTP-Ausfall wiederholt der Auth-Worker bis zu 3-mal, nach 15 Minuten nicht mehr. Die Antwort an den Client bleibt `202`, die Fehler-Metrik steigt. |

### 15.14 Datenschutz und Stammdaten (Abschnitt 10, FS-ADM)

| ID | Kriterium |
|---|---|
| AC-DP-01 | **Anonymisieren.** Nach der Anonymisierung hat der Nutzer die E-Mail `deleted-{id}@invalid`, keine Identitäten, Tokens, Claims oder Sessions mehr. Seine Einträge bleiben erhalten. |
| AC-DP-02 | **Claim-Frist.** Erledigte und ersetzte Claims sind 30 Tage nach `resolved_at` bzw. `superseded_at` gelöscht (Test mit simulierter Uhr). Der gültige Claim eines unzugeordneten Nutzers bleibt. |
| AC-ADM-01 | **CSV-Import.** Der Dry-Run schreibt nichts. Ein fehlerhafter Import ändert nichts (eine Transaktion). Ein erneuter Import derselben Datei ist idempotent. Dateien über 1 MB ergeben `413`. |

### 15.15 Lokaler Rollenwechsel (FS-DEV)

| ID | Kriterium |
|---|---|
| AC-DEV-01 | **Nicht in Produktion.** Ohne Profil `dev` gibt es weder Demo-Konten noch `POST /dev/login-as` (Anfrage liefert `401` bzw. `404`, kein Handler). Ein Start mit den Profilen `dev` und `prod` zusammen bricht ab. |
| AC-DEV-02 | **Wechsel.** Im Profil `dev` meldet `POST /dev/login-as` für jedes Demo-Konto an. `GET /me` zeigt danach die passende Rolle. Ein weiterer Aufruf wechselt das Konto mit neuer Session-ID. |
| AC-DEV-03 | **Kein Frontend-Rest.** Der Produktions-Build des Frontends enthält keinen Code der Dev-Leiste (Suche nach einem eindeutigen Marker im Bundle). |

---

## 16. Offene Fragen (Register)

Regel für KI-Agents: Bei jedem Punkt gilt der **Interim-Default**, bis der Auftraggeber entscheidet. Im Code mit `// OPEN-xx` markieren, damit er nach der Entscheidung gefunden und angepasst wird. Nicht raten, nichts darüber hinaus bauen.

| ID | Frage | Interim-Default | Betrifft |
|---|---|---|---|
| OPEN-01 | Welche Folgen hat es bei weniger als 10 h (Ausgleich, Frist)? Wird Überschuss ins nächste Jahr übertragen? | Keine automatischen Folgen. Admins sehen den Hinweis (D-11). Überschuss verfällt am Jahresende. | FS-ADMOV, FS-PDF |
| OPEN-02 | ~~Abrechnungs-PDF: je Familie, als Gesamtliste oder beides?~~ Entschieden 2026-10-22: beides (D-12). | – | FS-PDF |
| OPEN-03 | Woher stammt die Kinderliste und wie wird sie initial befüllt? | Manuelles Anlegen plus CSV-Import (7.7). | FS-ADM |
| OPEN-04 | Welche Familien sind in einem Schuljahr soll-pflichtig? Wie ist es bei Einstieg im laufenden Jahr (anteilig?) oder bei Befreiung? | Einschreibung pro Kind und Schuljahr (`child_enrollment`), volles Soll von 10 h, keine Befreiungen, kein Anteilig. | 5.3, FS-ADM, 5.6 |
| OPEN-05 | Restore per Server-Skript (Phase 1) oder zusätzlich per Knopf in der Master-UI? | Nur Skript. UI zeigt Status und löst Backups aus. | Abschnitt 11 |
| OPEN-06 | Wie verhält sich ein abgeschlossenes Schuljahr (Nachträge, Korrekturen durch Admins)? | Abgeschlossen = schreibgeschützt für alle, Admin kann wieder öffnen. | INV-04 |
| OPEN-07 | Welcher SMTP-Dienst und welche Absenderadresse? Seit D-24 **betriebskritisch**: Ohne Mail keine Anmeldung per Link (OPS-13). Der Anbieter muss SPF, DKIM und DMARC für die Absenderdomain unterstützen und einen AV-Vertrag anbieten (DP-07). | SMTP per Konfiguration. Dev mit Mailpit. | FS-MAIL, OPS-13 |
| OPEN-08 | Java oder Kotlin? Gradle oder Maven? | Java, Gradle. | 4.2 |
| OPEN-09 | Zweiter Faktor (TOTP) für Admins? **Neu bewertet nach D-24:** Ohne Passwort ist das Postfach (bzw. das Google-Konto) eines Admins der einzige Faktor für den Zugang zu allen Kinderdaten (Namen, Geburtsdaten). Ein kompromittiertes Mailkonto genügt. Der Aufwand für TOTP ist gering (wenige Admins). **Empfehlung:** TOTP für `ADMIN` vor Go-live zur Pflicht machen, sowohl beim E-Mail-Link als auch bei OIDC. | Pflicht nur für Master, für Admins optional aktivierbar (gilt für E-Mail-Link und OIDC). Schalter `app.security.admin-totp-required=false`, Ablauf ist fertig spezifiziert (7.2), Umstellen ist reine Konfiguration. | SEC-05, FS-AUTH |
| OPEN-10 | Obergrenze pro Eintrag (720 min)? Dürfen Einträge in der Zukunft liegen? | 720 min, keine Zukunftsdaten. | INV-03, DDL |
| OPEN-11 | Admin-Workflow bei Abweichungen: Ablehnen mit Begründung oder Rückfrage? Darf die Familie eigene offene Einträge ändern? Was passiert bei Änderung bestätigter Einträge? | Kein Ablehnen. Familie darf offene ändern. Admin-Änderung setzt auf `OPEN` zurück (INV-06, INV-07). | FS-CONF |
| OPEN-12 | Selbstkorrektur bei falscher automatischer Zuordnung (z. B. Familienmitglied meldet oder entfernt)? Admin-Freigabe neuer Mitglieder als Verschärfung? | Nur Mail an Mitglieder und Admin-Widget. | FS-REG 7.1.6 |
| OPEN-13 | Hosting: VPS-Standort Deutschland bestätigen, Tarif, wer bucht? Domain und DNS? Staging-Umgebung? | Entwicklung lokal per Compose. Ziel-VPS M+. Domain offen. | Abschnitt 12 |
| OPEN-14 | Soll die Integration später schreibend sein? Brauchen Admins einen CSV-Export? | Nur lesende API. Kein CSV-Export. | FS-INT |
| OPEN-15 | Welche OIDC-Anbieter über Google hinaus (Microsoft, Apple, andere)? | Nur Google. Weitere nur per Konfiguration ergänzbar. | FS-AUTH |
| OPEN-16 | Datenschutz und Recht: Rechtsgrundlage, Hinweise, Google-Login (Drittlandübermittlung), Löschfristen, AV-Verträge, Audit-Log-Aufbewahrung, Cookie-Hinweis. | Technisch datensparsam, Texte Platzhalter. Keine Löschautomatik für Fachdaten. Ausnahmen aus Sicherheitsgründen: unbestätigte Registrierungen (24 h), Claims (30 Tage, DP-05), Tokens und Auth-Anfragen. | Abschnitt 10 |
| OPEN-17 | Backup: Welche Cloud (Google Drive oder Microsoft)? Welcher Anbieter hinter dem Borg-Bucket? Aufbewahrung 14/8/12 passend? | restic nach IONOS S3 plus Borg-Bucket. Cloud-Ziel aus. Aufbewahrung 14/8/12. | Abschnitt 11 |
| OPEN-18 | Dürfen Nutzer mehreren Familien angehören (Patchwork)? Gibt es eine Obergrenze für Mitglieder je Familie? | Genau eine Familie je Nutzer, keine Obergrenze. | 5.3 |
| OPEN-19 | Offizielle Design-Werte des Horts (Farben, Schrift, Logo als Vektordatei). | Palette, Überschriften-Schrift und Logo-Zeichen von der Website übernommen (UI-01, 2026-10-22). Das Logo-Zeichen ist nachgezeichnet, bis eine Vektordatei vorliegt. | UI-01, FS-PDF |
| OPEN-20 | Soll die Mail mit dem E-Mail-Link zusätzlich einen 6-stelligen Code enthalten, für den Gerätewechsel (Mail am Handy, Anmeldung am PC)? Ein Code bräuchte eigene, strenge Versuchslimits je Token. | Nein. Nur der Link. | FS-AUTH, SEC-17 |
| OPEN-21 | Soll der Link an den anfragenden Browser gebunden werden (z. B. Zufallswert im Cookie beim Anfordern, Einlösen nur im selben Browser)? Das schützt auch gegen untergeschobene fremde Links (9.1), scheitert aber, wenn die Mail auf einem anderen Gerät geöffnet wird. | Weiche Bindung: geräteübergreifend erlaubt, aber mit Rückfrage und maskierter Kontoadresse, wenn das `auth_binding`-Cookie nicht passt (7.2). Kinderdaten aus der Registrierung gelten nur im selben Browser (7.1.4). | FS-AUTH, SEC-17 |

---

## 17. Meilensteine (Reihenfolge für die Umsetzung) `[PROPOSED]`

Jeder Meilenstein ist erst fertig, wenn die zugehörigen Abnahmekriterien (Abschnitt 15) grün sind und die Pipeline durchläuft.

| Nr. | Meilenstein | Inhalt | Abnahme |
|---|---|---|---|
| M0 | Fundament | Repository-Layout, CI, `docker compose` für dev (DB mit zwei Rollen nach OPS-14, Mailpit, OIDC-Testanbieter), Flyway `V1` plus Rechte-Migration, Health-Endpunkte, Testcontainers, Fehlerformat (8.1, auch für Security-Filter), injizierte Uhr (0.4 Regel 7), `AGENTS.md` | Pipeline grün, Start per Compose |
| M1 | Anmeldung per E-Mail-Link | Registrierung Weg A (ohne Zuordnung), Auth-Outbox und Worker, Browser-Bindung, E-Mail-Link (Anfordern, Bestätigungsseite, Einlösen, Rückfrage anderes Gerät), Logout, Sessions (inkl. absoluter Dauer), CSRF, Rate-Limits inkl. Mail-Bombing-Schutz, ALTCHA, Mailversand, Aufräum-Job, Master-Bootstrap, Master-Login (Passwort plus TOTP), Security-Header, lokaler Rollenwechsel (FS-DEV) | AC-AUTH außer 11, 18 und 23; AC-REG-02 und 10 nur für die Fälle ohne Abgleich; AC-REG-06, 12, 14; AC-MASTER; AC-MAIL; AC-SEC-01, 03, 05, 06, 11; AC-DEV. AC-SEC-09 und 13 wachsen ab hier mit jedem Meilenstein mit |
| M2 | Stammdaten und Zuordnung | Schuljahre, Familien, Kinder, Einschreibung, Normalisierung, Abgleich, Claims (inkl. Browser-Bedingung und Löschfrist), Zuordnung (auto und manuell), Admin-Widgets für Unzugeordnete, Nutzerverwaltung (Rolle, Deaktivieren, Anonymisieren), CSV-Import | AC-REG vollständig, AC-AUTH-11 und 18, AC-SY, AC-SEC-02, AC-DP, AC-ADM-01 |
| M3 | OIDC und Admin-TOTP | Google-Login (PKCE explizit, eigener Principal, Pending-Zustand), Verknüpfungsregeln inkl. E-Mail-Autorität, "Noch ein Schritt", Profil-Verknüpfung, zweiter Faktor für Admins (7.2) | AC-OIDC, AC-AUTH-23 |
| M4 | Erfassen und Bestätigen | Einträge, Familienübersicht, Partnerhinweis, Admin-Bestätigung | AC-ENTRY, AC-CONF, AC-FAM, AC-SEC-10 |
| M5 | Admin-Übersicht | Hinweisleiste, Liste mit Suche, Sortierung, Paging, Familiendetail, Dashboard | AC-OV |
| M6 | Abrechnung | PDF `ALL` und `FAMILY` (ohne externe Ressourcen), Audit | AC-PDF, AC-SEC-12 |
| M7 | Betrieb und Backup | Dockerfiles, Prod-Compose (DB-Rollen nach OPS-14), Caddy, Backup-Sidecar, Restore-Skript, Restore-Test, Server-Härtung, Deploy-Pipeline, Monitoring inkl. Mailversand (OPS-13), Runbooks | AC-BAK, AC-SEC-07 |
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
