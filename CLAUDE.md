# Project notes for Claude

## Containers: Podman, not Docker

This project uses **Podman** locally. Docker Desktop is installed but its daemon isn't running.

- Use `podman` and `podman compose`, never `docker` / `docker compose`, in commands, scripts and docs meant for local use.
  - `podman compose` delegates to the `docker-compose` binary as its provider. That's expected, not a sign Docker is in use.
- Start the dev database with `podman compose up -d` (compose.yaml).
- `dast/run.sh` uses `podman compose` by default and `docker compose` only when `CI` is set, as on GitHub Actions runners. `COMPOSE` overrides both.
- GitHub Actions (`.github/workflows/ci.yml`) runs on Ubuntu runners, where `docker compose` is the right choice. Keep Docker there.

## JDK

The default `java` on this machine is 23. The backend needs JDK 25: run Maven with `JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./mvnw ...`.
