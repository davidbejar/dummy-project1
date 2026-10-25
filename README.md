# dummy-project1

Full-stack scaffold:

| Part       | Stack                                            | Location     |
|------------|--------------------------------------------------|--------------|
| Backend    | Java 25, Spring Boot 4.1, Spring Data JPA, Flyway | `backend/`   |
| Database   | CockroachDB 26.3 (single node, via Podman)       | `compose.yaml` |
| Frontend   | React 19.3, TypeScript, Vite 8, run by Deno 2.9  | `frontend/`  |

The app opens on a login page. After signing in you reach the Todos page, with your name in the top-right corner. Todos are only reachable with a session.

## Prerequisites

- JDK 25 (Maven is provided by the `./mvnw` wrapper), e.g. `brew install openjdk@25`
- Deno 2.9+ (no Node.js needed), e.g. `brew install deno`
- Podman, e.g. `brew install podman`, with a VM created once:

  ```bash
  podman machine init && podman machine start
  ```

## Run it

1. **Start CockroachDB** (creates the `app` database; DB Console at http://localhost:8081):

   ```bash
   podman compose up -d
   ```

2. **Start the backend** on http://localhost:8080 (Flyway applies migrations on startup):

   ```bash
   cd backend && ./mvnw spring-boot:run
   ```

3. **Start the frontend** on http://127.0.0.1:5173 (proxies `/api` to the backend):

   ```bash
   cd frontend && deno install && deno task dev
   ```

4. **Sign in** with one of the development users:

   | Username | Password | Name shown | Role |
   |---|---|---|---|
   | `admin` | `admin` | Administrator | ADMIN |
   | `david` | `david` | David Bejar | USER |

## Configuration

The backend reads its connection from environment variables, defaulting to the local Docker node:

| Variable      | Default                                                  |
|---------------|----------------------------------------------------------|
| `DB_URL`      | `jdbc:postgresql://localhost:26257/app?sslmode=disable`  |
| `DB_USER`     | `root`                                                   |
| `DB_PASSWORD` | *(empty)*                                                |

CockroachDB is PostgreSQL wire-compatible, so the standard Postgres JDBC driver is used; Hibernate auto-selects its `CockroachDialect`. The local node runs in `--insecure` mode, which is for local development only.

CockroachDB 26+ blocks `crdb_internal` by default, which Flyway needs for version detection. `spring.flyway.init-sqls` enables it on Flyway's connection only.

## Authentication

This is the standard pattern for a single-page app served from the same origin as its API: **server-side sessions** with Spring Security, not JWTs.

| Endpoint | Purpose |
|---|---|
| `GET /api/auth/csrf` | Issues the `XSRF-TOKEN` cookie (the frontend calls it before the first login) |
| `POST /api/auth/login` | Form fields `username`, `password`. Returns the current user, or 401 with `"Invalid username or password"` |
| `GET /api/auth/me` | The signed-in user, or 401 |
| `POST /api/auth/logout` | Ends the session (204) |

Every other `/api/**` endpoint answers **401** without a session.

**Security properties**

- **Session cookie:** the session id is in a `JSESSIONID` cookie that is `HttpOnly` (JavaScript can't read it, so XSS can't steal it) and `SameSite=Lax`. Sessions time out after 30 minutes.
- **No sessions before login:** anonymous requests never create a session, and Spring Security issues a new session id at login, so a session id planted before login can't be reused (session fixation).
- **CSRF:** the double-submit pattern. The readable `XSRF-TOKEN` cookie is echoed in an `X-XSRF-TOKEN` header on every change, and the token is replaced at login.
- **Passwords** are stored as bcrypt hashes (`{bcrypt}$2a$10$…`), never in plain text.
- **No username probing:** an unknown user and a wrong password get the same error.
- **Case-insensitive usernames:** they're stored lowercase, so `David` and `david` are the same account.

**Users**

- The schema is created in `db/migration/V3__create_app_user.sql`.
- The development users come from `db/dev-data/R__seed_dev_users.sql`, which loads by default.
- In production, set `FLYWAY_LOCATIONS=classpath:db/migration` so the seed users aren't created.
- Over HTTPS, set `SESSION_COOKIE_SECURE=true`.

**Frontend**

- `AuthProvider` asks `/api/auth/me` once at startup to learn whether a session exists.
- `RequireAuth` guards private routes and sends anonymous visitors to the login page, then back to where they were going after login.
- Any 401 from the API (for example an expired session) returns the user to the login page.

Sessions are kept in the backend's memory, so restarting the backend signs everyone out.

## Data contract

Each `Todo` field has the same type in every layer:

| Field | React (`api.ts`) | Java (`Todo.java`) | CockroachDB |
|---|---|---|---|
| `id` | `string` (UUID) | `UUID` | `UUID` |
| `title` | `string` | `String` | `VARCHAR(255)` |
| `completed` | `boolean` | `boolean` | `BOOLEAN` |
| `createdAt` | `string` (ISO-8601) | `OffsetDateTime` | `TIMESTAMPTZ` |

`title` is free text with the same rules everywhere: it must not be blank, and it can be at most 255 characters after trimming.

- **React:** `validateTitle()` plus `maxLength` on the input.
- **Java:** `@NotBlank`/`@Pattern` and `@Size` on create and update.
- **CockroachDB:** the `VARCHAR(255)` column and a `CHECK` constraint.

The limit lives in `TITLE_MAX_LENGTH` (`features/todos/api.ts` and `Todo.java`) and in the migration. `frontend/src/features/todos/contract.test.ts` fails if they drift apart.

Validation errors come back as RFC 9457 problem details, and the UI shows their `detail` text. If the backend is unreachable, the UI says so instead of showing a raw `Failed to fetch`.

## Project layout

Both sides are organized by feature, so a new feature gets its own folder instead of spreading across the code.

```
backend/src/main/java/com/example/backend/
  auth/            users, login/logout, SecurityConfig, /api/auth
  todo/            Todo entity, repository, REST controller
  common/web/      shared web code (error responses)
backend/src/main/resources/
  application.yml           datasource / JPA / session config
  db/migration/             Flyway schema migrations (all environments)
  db/dev-data/              development seed data (users)

frontend/src/
  main.tsx                  entry point: AuthProvider + router
  app/routes.tsx            route table (public vs. signed-in pages)
  lib/http.ts               shared API client: CSRF, errors, 401 handling
  components/AppLayout.tsx  header with user name and logout
  features/auth/            login page, AuthProvider, useAuth, RequireAuth
  features/todos/           Todos page and its API
  test/                     test setup and renderApp() helper
```

**Adding a feature.**

- **Backend:** create a new package next to `todo/`. Its endpoints are protected automatically.
- **Frontend:** create `features/<name>/` with an `api.ts` that uses `lib/http.ts`, and add its page as a child route under `AppLayout` in `app/routes.tsx`.

## Frontend and Deno

The frontend runs on [Deno](https://deno.com) instead of Node.js. Dependencies are npm packages declared in `frontend/deno.json` and installed into `node_modules/` by `deno install`.

| Task | What it does |
|---|---|
| `deno task dev` | Vite dev server |
| `deno task build` | Type-check, then production build into `dist/` |
| `deno task preview` | Serve the production build |
| `deno task check` | Type-check with Deno's built-in TypeScript |
| `deno task test` | Run unit tests once |
| `deno task test:watch` | Run unit tests in watch mode |

Each task runs inside Deno's permission sandbox:

- **Writes** are limited to the `frontend/` folder.
- **Network** is limited to localhost. The dev and preview servers bind to `127.0.0.1`; `0.0.0.0` and `[::]` on the server port are only allowed because Vite probes them to check whether the port is free.
- **No subprocesses**: Vitest uses worker threads (`pool: 'threads'`), so no task needs `--allow-run`.
- **Reads** are unrestricted, because Vite looks for `package.json` in every parent directory.
- **FFI** is allowed because Vite 8's bundler, Rolldown, is a native library.

Imports of local files use explicit extensions (`./TodosPage.tsx`), as Deno requires.

## Tests

Frontend unit tests (Vitest + React Testing Library + jsdom), covering the HTTP client, login and route guards, and Todos:

```bash
cd frontend && deno task test
```

Backend unit tests (no database needed):

```bash
cd backend && ./mvnw test
```

Unit + integration tests (`*IT.java`, run against the CockroachDB from `podman compose up -d`), including the real login flow over HTTP. Integration tests are annotated with `@IntegrationTest`, which activates the `it` profile ([`application-it.yml`](backend/src/test/resources/application-it.yml)): they use their own `app_test` database (created automatically, migrated and seeded with `admin` and `david` by Flyway), so your data in `app` is never touched:

```bash
cd backend && ./mvnw verify
```

If JDK 25 isn't your default `java`, prefix with `JAVA_HOME=/opt/homebrew/opt/openjdk@25`.

## Security scanning (DAST)

[`dast/run.sh`](dast/run.sh) attacks a running copy of the app with two scanners:

- **[OWASP ZAP](https://www.zaproxy.org)** follows the plan in [`dast/zap/zap-plan.yaml`](dast/zap/zap-plan.yaml):
  - It imports the REST API from the OpenAPI description at `/v3/api-docs` (served by springdoc).
  - It seeds the login endpoint and spiders the front end.
  - Then it runs an active scan, capped at 20 minutes.
- **[Nuclei](https://github.com/projectdiscovery/nuclei)** runs its community templates for known CVEs, misconfigurations and exposed files. Denial-of-service and fuzzing templates are skipped, and so are out-of-band callbacks.

```bash
dast/run.sh
```

Here's what the script does:

1. It builds the backend and frontend images and starts them with their own throwaway CockroachDB ([`compose.dast.yaml`](compose.dast.yaml)). Your `app` data is never touched. The front end is the production build served by nginx, which proxies `/api` to the backend. It's reachable at http://127.0.0.1:8088 while the scan runs.
2. It signs in as `david`. The session and CSRF cookies are added to every scanner request, so the protected API is scanned too. ZAP never calls logout, because that would end the session.
3. It writes the reports to `dast/reports/`:
   - `zap-report.html`, plus `.json` and `.sarif.json` versions
   - `nuclei.jsonl`, plus a Markdown report per finding in `nuclei/`
4. It stops the stack.

It **fails** on a ZAP High alert or a Nuclei high/critical finding. ZAP Medium alerts only print a warning. It uses `podman compose` locally and `docker compose` in CI (when `CI` is set); set `COMPOSE` to override. Set `DAST_USER`, `DAST_PASSWORD` or `DAST_WEB_PORT` to change the defaults.

`/v3/api-docs` is public so ZAP can read it. In production, set `API_DOCS_ENABLED=false`.

## Continuous integration

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on every push and pull request once the repo is on GitHub:

| Job | Runs |
|---|---|
| Frontend unit tests | `deno task check` and `deno task test` |
| Backend unit tests | `./mvnw test` |
| Backend integration tests | `./mvnw verify` against CockroachDB from `compose.yaml` |
| DAST (ZAP + Nuclei) | `dast/run.sh`, only after the three test jobs pass |

The DAST reports are uploaded as the `dast-reports` artifact of each run, including runs that fail.
