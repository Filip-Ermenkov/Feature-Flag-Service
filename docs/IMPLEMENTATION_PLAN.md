# Feature Flag Service — Implementation Plan

## Technology Decisions

| Concern | Choice | Rationale |
|---|---|---|
| Framework | Spring Boot 4.0.7 (Spring Framework 7, Jakarta EE 11) | Industry standard; auto-configures web, JPA, validation |
| Java version | 21 (LTS) | Required minimum; unlocks virtual threads with zero code changes |
| Build | Maven | Wider familiarity; deterministic dependency resolution |
| Persistence | Spring Data JPA + H2 (file mode) | File-based H2 survives restarts; JPA repository interface makes the storage layer trivially swappable |
| API documentation | SpringDoc OpenAPI 3.0.3 (Swagger UI) | Zero-boilerplate; can explore the API interactively without curl |
| Tests | JUnit 6 + MockMvc (`@WebMvcTest` slice) | H2 in-memory for the test profile; full request-to-DB round-trip without spinning up a real server |
| Containerisation | Dockerfile (multi-stage, JRE 21 slim base) | Stretch goal; minimal final image; demonstrates production awareness |
| CI | GitHub Actions | Stretch goal; build + test on every push and pull request |

---

## Potential Project Structure

```
feature-flag-service/
├── src/
│   ├── main/
│   │   ├── java/com/example/featureflags/
│   │   │   ├── FeatureFlagApplication.java          # entry point
│   │   │   ├── controller/
│   │   │   │   └── FeatureFlagController.java       # REST layer
│   │   │   ├── service/
│   │   │   │   ├── FeatureFlagService.java          # interface (swappability boundary)
│   │   │   │   └── FeatureFlagServiceImpl.java      # business logic
│   │   │   ├── repository/
│   │   │   │   └── FeatureFlagRepository.java       # JpaRepository extension
│   │   │   ├── model/
│   │   │   │   └── FeatureFlag.java                 # JPA entity
│   │   │   ├── dto/
│   │   │   │   ├── CreateFlagRequest.java
│   │   │   │   ├── UpdateFlagRequest.java
│   │   │   │   ├── FlagResponse.java
│   │   │   │   └── EvaluateResponse.java
│   │   │   └── exception/
│   │   │       ├── FlagNotFoundException.java
│   │   │       ├── DuplicateFlagNameException.java
│   │   │       └── GlobalExceptionHandler.java      # @RestControllerAdvice
│   │   └── resources/
│   │       └── application.yml                      # datasource, JPA, virtual threads, OpenAPI
│   └── test/
│       └── java/com/example/featureflags/
│           ├── controller/
│           │   └── FeatureFlagControllerTest.java   # @WebMvcTest slice
│           └── service/
│               └── FeatureFlagServiceTest.java      # unit tests with Mockito
├── .github/workflows/
│   └── ci.yml
├── Dockerfile
├── .dockerignore
├── pom.xml
└── README.md
```

---

## Data Model

The `FeatureFlag` entity has: auto-generated `id`, `name` (unique, non-null), optional `description`, `enabled` (non-null boolean), and `createdAt` / `updatedAt` timestamps maintained automatically by Hibernate. A unique constraint on `name` is enforced at the DB level.

---

## API Endpoints

| Method | Path | Success | Error | Notes |
|---|---|---|---|---|
| `POST` | `/flags` | 201 | 409 | 409 on duplicate name |
| `GET` | `/flags` | 200 | — | always returns an array |
| `GET` | `/flags/{id}` | 200 | 404 | |
| `PATCH` | `/flags/{id}` | 200 | 404 | partial update; only supplied fields mutated |
| `DELETE` | `/flags/{id}` | 204 | 404 | |
| `GET` | `/flags/{name}/evaluate` | 200 | 404 | lookup by name, not id |

Error responses use Spring Framework 7's `ProblemDetail` (RFC 9457) — `type`, `title`, `status`, `detail`, `timestamp` fields — making errors machine-readable.

---

## Layer Responsibilities

- **Controller** — HTTP concerns only: deserialise request, call service, serialise response, return status code.
- **Service interface** — defines the contract; isolates business logic from transport and storage.
- **ServiceImpl** — business rules: duplicate name check, not-found guard, partial update logic.
- **Repository** — `JpaRepository` with one custom finder: `findByName(String name)`.
- **GlobalExceptionHandler** — maps domain exceptions to `ProblemDetail` responses; keeps controller clean.
- **DTOs** — request and response objects are separate from the entity; prevents accidental field exposure and decouples the API contract from the persistence model.

---

## Persistence

H2 in file mode (`jdbc:h2:file:./data/featureflags`) provides durable storage with no external infrastructure. `ddl-auto: update` is used for simplicity — in production this would be replaced by Flyway migrations. Virtual threads are enabled via a single configuration flag; no application code changes required.

---

## Test Strategy

**Service unit tests** — cover all business rules with Mockito-mocked repository: create (happy path + duplicate name → exception), list, getById (found + not found → exception), update, delete, evaluate.

**Controller slice tests** — `@WebMvcTest` with `@MockitoBean` service: verify correct HTTP status codes, JSON response shapes, and error body structure for every endpoint including error paths.

**Deliberate omissions** — no separate integration tests; the `@WebMvcTest` slice exercises the full HTTP stack and the service unit tests cover all logic paths, making a third layer redundant at this scope.

---

## Dockerfile Strategy

Two-stage build. Stage 1 (`eclipse-temurin:21-jdk-noble`) receives the pre-built fat JAR and uses Spring Boot's `jarmode=tools` to split it into four ordered layers: `dependencies`, `spring-boot-loader`, `snapshot-dependencies`, `application`. Stage 2 (`eclipse-temurin:21-jre-noble`) copies those layers individually, so Docker can cache the large dependency layer and only re-push the tiny application layer on rebuilds. The runtime image runs as a non-root system user. H2 data is stored under `/application/data`; mount a named volume there for persistence across container restarts.

---

## CI Strategy

GitHub Actions workflow triggers on every push and pull request. Single job: checkout → setup JDK 21 with Maven cache → `mvn verify`. This compiles, runs all tests, and packages the artifact in one command.

---

## Implementation Order

1. `pom.xml` with all dependencies
2. `application.yml` — datasource, JPA, virtual threads, SpringDoc
3. `FeatureFlag` entity + `FeatureFlagRepository`
4. DTOs + domain exceptions
5. `GlobalExceptionHandler`
6. `FeatureFlagService` interface + `FeatureFlagServiceImpl`
7. `FeatureFlagController`
8. Service unit tests → controller slice tests
9. `Dockerfile` + `.dockerignore`
10. `.github/workflows/ci.yml`
11. `README.md`

---

## README Structure

1. **Quick start** — one command to run with Maven, one to build and run with Docker
2. **API reference** — one curl example per endpoint
3. **Design decisions** — framework choice, H2 file mode, layered architecture, ProblemDetail errors, virtual threads
4. **Trade-offs and future improvements** — Flyway, auth/authz, flag rollout percentages, environment-scoped values, caching for high-volume evaluate calls
