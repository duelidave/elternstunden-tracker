# AGENTS.md

Guide for AI coding agents and contributors (NFR-08).

## Source of truth

`docs/TECHNISCHES-LASTENHEFT.md` (German). Read section 0 first (binding), then 2, 4, 8.1 and the milestone in section 17 you work on; load other sections as needed. Status tags: `[DECIDED]` implement exactly, `[PROPOSED]` default but keep it changeable, `[OPEN]` only the interim default from section 16, marked in code with `// OPEN-xx` (SQL/YAML: `-- OPEN-xx` / `# OPEN-xx`).

## Rules (Lastenheft 0.4, repeated verbatim in substance)

1. Order of implementation: milestones from section 17.
2. No feature beyond the specification. Ideas go to section 16, not into the code.
3. Definition of Done: code, migrations, tests (unit and integration), acceptance criteria from section 15 green, API contract changed first and code generated from it (D-25), no new warnings from the security scans.
4. Language: UI texts German (informal "du", friendly, short). Code, identifiers, commits, logs English. Database identifiers `snake_case`, English.
5. Time zone: business data in `Europe/Berlin`. Timestamps in the DB as `timestamptz` (UTC).
6. No personal data in logs, URLs or error messages (SEC-12).
7. Clock: all business time comparisons use the injected `java.time.Clock` and pass the time into SQL as a parameter, never `now()` in business logic.

## Commands

| Task | Command |
|---|---|
| Full backend check | `./gradlew check` (needs Docker for Testcontainers) |
| Format Java | `./gradlew :backend:spotlessApply` |
| Regenerate Spring API code | `./gradlew :backend:openApiGenerate` (runs before every compile) |
| Run the stack | `cp .env.example .env && docker compose up --build` |
| Backend without Docker | `./gradlew :backend:bootRun --args='--spring.profiles.active=dev'` |
| Lint the contract | `cd frontend && npm run lint:api` |
| Frontend checks | `cd frontend && npm run lint && npm run typecheck && npm test && npm run build` |
| Frontend dev server | `cd frontend && npm run dev` |

## Conventions

- **API-first (D-25):** change `api/openapi.yaml` first. Every operation needs `operationId`, `tags` and `x-roles` (codes from 8.2). Spring generates interfaces and `*Dto` classes into `de.elternstunden.api` (never edit, never commit); controllers implement them. orval generates hooks and zod schemas into `frontend/src/api/generated` (gitignored). `ContractCoverageTest` fails if a handler under `/api` is not in the contract or vice versa.
- **Packages:** one module per domain below `de.elternstunden` (section 4.4) with `web`, `service`, `domain`, `repo`; shared code in `common`. No business logic in controllers, entities never leave the web layer.
- **Errors:** throw `ApiException(ErrorCode…)`; every error is `application/problem+json` with `code` and `traceId` (8.1). Never put personal data into `detail`.
- **Security:** deny by default (`ApiSecurityConfig`); every new endpoint gets an explicit rule matching its `x-roles`.
- **Database:** Flyway only (`backend/src/main/resources/db/migration`), migrations backwards compatible. Flyway runs as owner, the app as `elternstunden_app` (OPS-14); append-only tables revoke `UPDATE`/`DELETE`.
- **Dev-only code (D-26):** backend only with `@Profile("dev")`, frontend only behind `import.meta.env.DEV`. `npm run build` fails if dev toolbar code ends up in the bundle (AC-DEV-03).
- **Frontend:** texts only in `src/i18n/de.ts`, colours and sizes only as tokens in `src/styles/tokens.css` (UI-01). Forms validate with the generated zod schemas (UI-08).
- **Secrets:** never in the repository. Local example values only in `.env.example`.
- **Git:** small commits, Conventional Commits (`feat:`, `fix:`, `docs:`, `test:`, `build:`, `ci:`, `chore:`).
