# Feature Flag Service

[![CI](https://github.com/Filip-Ermenkov/Feature-Flag-Service/actions/workflows/ci.yml/badge.svg)](https://github.com/Filip-Ermenkov/Feature-Flag-Service/actions/workflows/ci.yml)

A REST API for managing application feature flags, built with Spring Boot 4.0.7 and Java 21.

## Requirements

- Java 21+
- Maven 3.9+

## Build & Run

```bash
# Run (downloads dependencies on first run)
mvn spring-boot:run

# Run tests
mvn test

# Build executable JAR
mvn package
java -jar target/feature-flag-service-0.0.1-SNAPSHOT.jar
```

## Docker

```bash
# Build the image (requires the JAR to be built first)
mvn package -DskipTests
docker build -t feature-flag-service .

# Run — flags persist in a named volume across container restarts
docker run -p 8080:8080 -v featureflags-data:/application/data feature-flag-service

# Run without persistence (ephemeral — flags are lost on container stop)
docker run -p 8080:8080 feature-flag-service
```

The image uses a multi-stage build: a JDK builder stage extracts the Spring Boot layered JAR, and a JRE-only runtime stage assembles the final image (~350 MB). H2 flag data is stored under `/application/data` — mount a named volume there for persistence.

## Tech Stack

| Concern | Choice |
|---|---|
| Framework | Spring Boot 4.0.7 (Spring Framework 7, Jakarta EE 11) |
| Language | Java 21 LTS |
| Persistence | Spring Data JPA + H2 (file mode — survives restarts) |
| API Docs | SpringDoc OpenAPI 3.0.3 (Swagger UI) |
| Concurrency | Virtual threads (`spring.threads.virtual.enabled=true`) |

## Design Decisions

**H2 file mode** — `jdbc:h2:file:./data/featureflags` persists across restarts with no external infrastructure. Swapping to PostgreSQL requires only a driver dependency and a URL change; no application code changes.

**Storage layer interface** — `FeatureFlagService` is defined as an interface. `FeatureFlagServiceImpl` is the sole implementation. Consumers depend on the interface, so the persistence backend is swappable without touching any other layer.

**`open-in-view: false`** — disabled explicitly to prevent lazy-loading queries from silently executing inside HTTP response serialisation.

**`ddl-auto: update`** — acceptable for a showcase project. Production would use Flyway migrations instead.

**Virtual threads** — enabled via a single configuration flag; zero application code changes required. Effective only on Java 21+.

**Error responses** — all error responses use `ProblemDetail` (RFC 9457), built into Spring Framework 7, making them machine-readable (`type`, `title`, `status`, `detail`, `timestamp`).

## API Reference

| Method | Path | Success | Description |
|---|---|---|---|
| `POST` | `/flags` | 201 + Location | Create a flag |
| `GET` | `/flags` | 200 | List all flags |
| `GET` | `/flags/{id}` | 200 | Get a flag by ID |
| `PATCH` | `/flags/{id}` | 200 | Partially update a flag (only supplied fields mutated) |
| `DELETE` | `/flags/{id}` | 204 | Delete a flag |
| `GET` | `/flags/{name}/evaluate` | 200 | Evaluate a flag by name |

Error responses use RFC 9457 `ProblemDetail` — 404 for unknown IDs/names, 409 for duplicate names, 422 for validation failures.

Once running, the full interactive API reference is available at:
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`

### curl Examples

```bash
# Create a flag
curl -s -X POST http://localhost:8080/flags \
  -H "Content-Type: application/json" \
  -d '{"name":"dark-mode","description":"Enable dark mode UI","enabled":false}' | jq

# List all flags
curl -s http://localhost:8080/flags | jq

# Get a flag by ID
curl -s http://localhost:8080/flags/1 | jq

# Partially update a flag (toggle enabled, change description)
curl -s -X PATCH http://localhost:8080/flags/1 \
  -H "Content-Type: application/json" \
  -d '{"enabled":true,"description":"Updated description"}' | jq

# Evaluate a flag by name (lightweight — returns only name + enabled)
curl -s http://localhost:8080/flags/dark-mode/evaluate | jq

# Delete a flag
curl -s -o /dev/null -w "%{http_code}" -X DELETE http://localhost:8080/flags/1
```

## Current State

- [x] Maven project skeleton (Spring Boot 4.0.7, Java 21)
- [x] H2 file-mode datasource configured
- [x] Virtual threads enabled
- [x] SpringDoc OpenAPI configured
- [x] Application context smoke test passes
- [x] Domain model (`FeatureFlag` entity + `FeatureFlagRepository` with `@DataJpaTest` slice tests)
- [x] DTOs (Java records: `CreateFlagRequest`, `UpdateFlagRequest`, `FlagResponse`, `EvaluateResponse`) and domain exceptions (`FlagNotFoundException`, `DuplicateFlagNameException`) with RFC 9457 `ProblemDetail` error responses via `GlobalExceptionHandler` (`@WebMvcTest` slice tests)
- [x] Service layer (`FeatureFlagService` interface + `FeatureFlagServiceImpl`) with `@Transactional(readOnly=true)` class-level default, write-method overrides, and full Mockito unit test coverage (15 tests across create/findAll/findById/update/delete/evaluate)
- [x] REST controller (`FeatureFlagController`) — all 6 endpoints with `@WebMvcTest` slice tests using Spring Framework 7's `MockMvcTester` (AssertJ-based); Mockito self-attaching agent configured in `maven-surefire-plugin`
- [x] Dockerfile — multi-stage (JDK builder + JRE runtime), Spring Boot `jarmode=tools` layer extraction, non-root user, `VOLUME` for H2 persistence, container-aware JVM flags
- [x] GitHub Actions CI — triggers on push/PR to `main`; Temurin JDK 21 with Maven cache; `mvn verify`; Surefire report artifact on failure; least-privilege `contents: read` + concurrency cancellation

## Future Improvements

- Replace `ddl-auto: update` with **Flyway** migrations for safe schema evolution
- Add **authentication/authorisation** (e.g. API key header or OAuth2)
- **Percentage rollouts** — enable a flag for X% of requests
- **Environment scoping** — per-environment flag values (dev/staging/prod)
- **Caching** — a short-lived cache on the evaluate endpoint for high-throughput scenarios
