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
frontend/   React, TypeScript, Vite, TanStack Query, orval
ops/        Betrieb (DB-Init-Skript; Prod-Compose, Caddy, Backup folgen in M7)
docs/       Lastenheft und Spezifikation
```

Hinweise für KI-Agents und Mitwirkende: [`AGENTS.md`](AGENTS.md).
