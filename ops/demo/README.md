# Demo hinter Traefik

Kleiner Stack zum Vorzeigen und für Ressourcentests auf einem Server mit **vorhandenem Traefik**. Das ist **nicht**
der Produktionsbetrieb: Der kommt mit M7 (Caddy, Backup, Härtung, `ops/`, Lastenheft 12). Die Limits sind für einen
Server mit 2 GB RAM und 1 vCore gewählt (IONOS VPS S+).

Dienste: `db` (PostgreSQL), `app` (Spring Boot), `web` (nginx mit der gebauten SPA) und `mailpit` (fängt alle Mails ab).
Traefik leitet `/api/`, `/oauth2/` und `/login/oauth2/` an `app`, alles andere an `web`. `/actuator` wird nie
weitergegeben.

> **Profil:** Die Demo läuft mit `prod`, nie mit `dev`. Das Profil `dev` hat Demo-Konten und einen Login-Controller für
> den Rollenwechsel. Die Compose-Datei setzt `prod` fest, die App startet bei `dev` plus `prod` nicht (AC-DEV-01).
> Die Datenbank ist zu Beginn leer. Schuljahr und Familien legst du als Master-Admin an (CSV-Import im Admin-Bereich).

## Einrichten

```bash
cd ops/demo
cp .env.example .env && chmod 600 .env     # .env ist gitignored
```

In `.env` jeden Wert `CHANGE_ME` ersetzen:

- **Traefik:** `TRAEFIK_NETWORK` (Netz, über das Traefik die Container erreicht), `TRAEFIK_ENTRYPOINT`,
  `TRAEFIK_CERTRESOLVER`. Nachschlagen in der Traefik-Konfiguration bzw. mit `docker network ls`.
- **`TRUSTED_PROXY_REGEX`:** Adresse, von der Traefik zur App verbindet (SEC-04). Nur von dort wird
  `X-Forwarded-For` für die Rate-Limits übernommen. Am besten die genaue Container-Adresse, zum Beispiel
  `172\.18\.0\.2` (`docker network inspect <netz>`), mindestens das Subnetz. Ist die Regex falsch, wird der Header ignoriert,
  und alle Besucher teilen sich ein Limit.
- **Geheimnisse:** `openssl rand -base64 24` für die Datenbankpasswörter, `openssl rand -base64 48` für
  `APP_ENCRYPTION_KEY` und `ALTCHA_HMAC_KEY` (mindestens 32 Zeichen, sonst startet die App nicht).
- **Master-Admin:** `MASTER_ADMIN_EMAIL`, Passwort-Hash und TOTP-Secret.
  - Hash (Argon2id, mindestens `m=19456,t=2,p=1`): `argon2` installieren (`apt install argon2`) und
    `echo -n 'DEIN-PASSWORT' | argon2 "$(openssl rand -hex 8)" -id -k 19456 -t 2 -p 1 -e` ausführen. Die Ausgabe in
    einfachen Anführungszeichen in `.env` eintragen. Das Passwort danach aus dem Shell-Verlauf entfernen.
  - TOTP-Secret: `head -c 20 /dev/urandom | base32` und das Ergebnis in eine Authenticator-App eintragen
    (Typ „zeitbasiert“, SHA-1, 6 Stellen, 30 Sekunden).

## Starten und Betrieb

```bash
docker compose up -d --build         # erster Start dauert wegen Gradle und npm einige Minuten
docker compose ps                    # app wird healthy, sobald Flyway durch ist
docker compose logs -f app
docker compose down                  # stoppen, Daten bleiben im Volume pgdata
docker compose down -v               # stoppen und Daten löschen
```

**Mails lesen** (Mailpit ist nur lokal am Server gebunden): am eigenen Rechner
`ssh -L 8025:localhost:8025 <server>`, dann http://localhost:8025 öffnen.

**Update:** `git pull && docker compose up -d --build`.

## Ressourcen messen

```bash
docker stats --no-stream
free -m
docker inspect -f '{{.Name}} OOMKilled={{.State.OOMKilled}}' $(docker compose ps -q)
```

Gemessen bei Ruhe auf einem Entwicklungsrechner: `app` rund 240 MB, `db` rund 50 MB, `mailpit` rund 26 MB, `web`
rund 10 MB. Bereit ist die App nach etwa 16 Sekunden. Last und PDF-Erzeugung sind damit noch nicht gemessen.
Das Bauen der Images braucht deutlich mehr Speicher als der Betrieb. Auf einem 2-GB-Server sollten die Images
vorgebaut aus einer Registry kommen.

## Daten

Nur Testdaten verwenden. Echte Familiendaten gehören erst auf einen Server, wenn mit dem Hoster ein
Auftragsverarbeitungsvertrag besteht (DP-07).
