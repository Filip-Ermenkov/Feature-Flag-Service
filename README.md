# Feature Flag Service

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

> **Endpoints are not yet implemented.** This section will be populated as development progresses.

| Method | Path | Status | Description |
|---|---|---|---|
| `POST` | `/flags` | 🔜 | Create a flag |
| `GET` | `/flags` | 🔜 | List all flags |
| `GET` | `/flags/{id}` | 🔜 | Get a flag by ID |
| `PATCH` | `/flags/{id}` | 🔜 | Partially update a flag |
| `DELETE` | `/flags/{id}` | 🔜 | Delete a flag |
| `GET` | `/flags/{name}/evaluate` | 🔜 | Evaluate a flag by name |

Once running, the full interactive API reference is available at:
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`

## Current State

- [x] Maven project skeleton (Spring Boot 4.0.7, Java 21)
- [x] H2 file-mode datasource configured
- [x] Virtual threads enabled
- [x] SpringDoc OpenAPI configured
- [x] Application context smoke test passes
- [x] Domain model (`FeatureFlag` entity + `FeatureFlagRepository` with `@DataJpaTest` slice tests)
- [x] DTOs (Java records: `CreateFlagRequest`, `UpdateFlagRequest`, `FlagResponse`, `EvaluateResponse`) and domain exceptions (`FlagNotFoundException`, `DuplicateFlagNameException`) with RFC 9457 `ProblemDetail` error responses via `GlobalExceptionHandler` (`@WebMvcTest` slice tests)
- [ ] Service layer
- [ ] REST controller (all 6 endpoints)
- [ ] Automated tests (service unit tests + `@WebMvcTest` slice tests)
- [ ] Dockerfile (multi-stage)
- [ ] GitHub Actions CI

## Future Improvements

- Replace `ddl-auto: update` with **Flyway** migrations for safe schema evolution
- Add **authentication/authorisation** (e.g. API key header or OAuth2)
- **Percentage rollouts** — enable a flag for X% of requests
- **Environment scoping** — per-environment flag values (dev/staging/prod)
- **Caching** — a short-lived cache on the evaluate endpoint for high-throughput scenarios
