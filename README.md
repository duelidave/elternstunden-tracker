# Elternstunden-Zeiterfassung

Web-App für den Hort an der Montessorischule: Familien erfassen Elternstunden, Admins bestätigen und rechnen pro Schuljahr ab.
Quelle der Wahrheit ist [`docs/TECHNISCHES-LASTENHEFT.md`](docs/TECHNISCHES-LASTENHEFT.md), der API-Vertrag liegt in [`api/openapi.yaml`](api/openapi.yaml) (API-first, D-25).

## Voraussetzungen

- Docker mit Compose v2
- JDK 25 (nur für Builds außerhalb von Docker)
- Node 24 LTS oder neuer (Frontend)

## Schnellstart

```bash
cp .env.example .env          # lokale Beispielwerte, nie produktiv verwenden
docker compose up --build     # DB, Mailpit, OIDC-Mock, App
```

| Dienst | URL |
|---|---|
| Status (öffentlich) | http://localhost:8088/api/v1/status |
| Health (intern) | http://localhost:8089/actuator/health/readiness |
| Mailpit | http://localhost:8025 |
| OIDC-Mock (Discovery) | http://localhost:8090/default/.well-known/openid-configuration |
| Frontend (Dev-Server) | http://localhost:5173 |

Frontend starten (Proxy `/api` auf Port 8088):

```bash
cd frontend && npm ci && npm run dev
```

Alle Ports hängen nur an `127.0.0.1` und lassen sich in `.env` ändern.

## Lokal ausprobieren

Die App öffnest du immer über den Dev-Server (http://localhost:5173), auch die Links aus den Mails zeigen dorthin.
Am besten mit Chrome oder Firefox: Beide akzeptieren das `Secure`-Session-Cookie auch auf `http://localhost`.

- **Registrieren:** `/register` ausfüllen, die Mail kommt in Mailpit (http://localhost:8025) an. Link öffnen, "Jetzt anmelden".
- **Anmelden:** `/login`, E-Mail eingeben, Link aus Mailpit öffnen.
- **Rollen wechseln (D-26):** Die Dev-Leiste unten meldet ohne Mail direkt als Familie, Partner, Unzugeordnet, Admin oder Master an.
  Sie gibt es nur mit `npm run dev`, nie im Produktions-Build.
- **Master-Login:** `/login/master` mit `master@dev.local` und dem Passwort `local-dev-master`. Den Code liefert eine
  Authenticator-App mit dem Secret `MASTER_ADMIN_TOTP_SECRET` aus `.env`, oder `oathtool --totp -b <secret>`.
- **Automatische Zuordnung:** Registriere dich mit dem Kind "Anna Müller", geboren am 03.05.2017. Nach dem Klick auf den
  Link (im selben Browser) bist du der "Familie Müller" zugeordnet. Mit einem Tippfehler landest du auf dem Wartescreen.
- **Mit Google anmelden:** Lokal steht ein OIDC-Mock (http://localhost:8090) für Google. Auf seiner Anmeldeseite
  gibst du als „user/subject" einfach eine Adresse ein, etwa `anna@gmail.com`. Neue Adressen landen auf „Noch ein
  Schritt", bekannte melden sich direkt an. Nur `@gmail.com`-Adressen werden mit einem bestehenden Konto verknüpft
  (7.1.5); andere verknüpfst du im Profil.
- **Zweiter Faktor für Admins:** Im Profil „Einrichten", QR-Code mit einer Authenticator-App scannen. Danach fragt die
  Anmeldung per E-Mail-Link oder Google nach dem Code. Die Dev-Leiste überspringt ihn (D-26).
- **Admin-Bereich:** Über die Dev-Leiste als Admin anmelden, dann "Zum Admin-Bereich". Die Übersicht zeigt
  "Uli Unzugeordnet" mit dem Vorschlag "Mila Muster" zum Zuordnen. Dort gibt es auch Kinder, Familien, Nutzer und
  Schuljahre, inklusive CSV-Import.

## Bauen und testen

```bash
./gradlew check                       # Code aus dem Vertrag generieren, Unit- und Integrationstests (Docker nötig), Spotless, Error Prone, JaCoCo
./gradlew :backend:spotlessApply      # Java formatieren
./gradlew :backend:bootRun --args='--spring.profiles.active=dev'   # Backend ohne Docker, liest ../.env

cd frontend
npm run lint:api                      # API-Vertrag prüfen (Redocly)
npm run lint && npm run typecheck && npm test && npm run build
```

## Aufbau

```
api/        OpenAPI-Vertrag (Quelle der Wahrheit für beide Seiten)
backend/    Spring Boot 4.1, Java 25, Gradle
frontend/   React, TypeScript, Vite, React Router, TanStack Query, orval, Tailwind CSS
ops/        Betrieb (DB-Init-Skript; Prod-Compose, Caddy, Backup folgen in M7)
docs/       Lastenheft und Spezifikation
```

Hinweise für KI-Agents und Mitwirkende: [`AGENTS.md`](AGENTS.md).
