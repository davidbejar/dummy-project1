# dummy-project1

Full-stack scaffold:

| Part       | Stack                                            | Location     |
|------------|--------------------------------------------------|--------------|
| Backend    | Java 25, Spring Boot 4.1, Spring Data JPA, Flyway | `backend/`   |
| Database   | CockroachDB 26.3 (single node, via Podman)       | `compose.yaml` |
| Frontend   | React 19.3, TypeScript, Vite 8, run by Deno 2.9  | `frontend/`  |

A sample `Todo` resource is wired end to end (`/api/todos`) so you can see each layer working.

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

## Configuration

The backend reads its connection from environment variables, defaulting to the local Docker node:

| Variable      | Default                                                  |
|---------------|----------------------------------------------------------|
| `DB_URL`      | `jdbc:postgresql://localhost:26257/app?sslmode=disable`  |
| `DB_USER`     | `root`                                                   |
| `DB_PASSWORD` | *(empty)*                                                |

CockroachDB is PostgreSQL wire-compatible, so the standard Postgres JDBC driver is used; Hibernate auto-selects its `CockroachDialect`. The local node runs in `--insecure` mode, which is for local development only.

CockroachDB 26+ blocks `crdb_internal` by default, which Flyway needs for version detection. `spring.flyway.init-sqls` enables it on Flyway's connection only.

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

The limit lives in `TITLE_MAX_LENGTH` (`api.ts` and `Todo.java`) and in the migration. `frontend/src/contract.test.ts` fails if they drift apart.

Validation errors come back as RFC 9457 problem details, and the UI shows their `detail` text. If the backend is unreachable, the UI says so instead of showing a raw `Failed to fetch`.

## Project layout

```
backend/
  src/main/java/com/example/backend/todo/   entity, repository, REST controller
  src/main/resources/application.yml        datasource / JPA config
  src/main/resources/db/migration/          Flyway SQL migrations
frontend/
  deno.json                                 dependencies, tasks, sandbox permissions
  src/api.ts                                typed API client
  src/App.tsx                               Todo UI
  src/*.test.ts(x)                          Vitest unit tests
  src/test/setup.ts                         test setup (jest-dom matchers, cleanup)
```

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

Imports of local files use explicit extensions (`./App.tsx`), as Deno requires.

## Tests

Frontend unit tests (Vitest + React Testing Library + jsdom):

```bash
cd frontend && deno task test
```

Backend unit tests (no database needed):

```bash
cd backend && ./mvnw test
```

Unit + integration tests (`*IT.java`, run against the CockroachDB from `podman compose up -d`):

```bash
cd backend && ./mvnw verify
```

If JDK 25 isn't your default `java`, prefix with `JAVA_HOME=/opt/homebrew/opt/openjdk@25`.
