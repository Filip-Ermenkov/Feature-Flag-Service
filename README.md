# Feature Flag Service

[![CI](https://github.com/Filip-Ermenkov/Feature-Flag-Service/actions/workflows/ci.yml/badge.svg)](https://github.com/Filip-Ermenkov/Feature-Flag-Service/actions/workflows/ci.yml)

A REST API for managing application feature flags, built with Spring Boot 4.0.7 and Java 21.

## Build & Run

**Maven (no Docker required)**

```bash
mvn spring-boot:run
```

**Docker**

```bash
mvn package -DskipTests
docker build -t feature-flag-service .

# With persistence across restarts
docker run -p 8080:8080 -v featureflags-data:/application/data feature-flag-service

# Ephemeral (flags lost on stop)
docker run -p 8080:8080 feature-flag-service
```

**Run tests**

```bash
mvn test
```

Once running, the interactive API reference is at `http://localhost:8080/swagger-ui.html`.

---

## API Reference

| Method   | Path                    | Success      | Description                                          |
|----------|-------------------------|--------------|------------------------------------------------------|
| `POST`   | `/flags`                | 201          | Create a flag; 409 on duplicate name                 |
| `GET`    | `/flags`                | 200          | List all flags (always a JSON array)                 |
| `GET`    | `/flags/{id}`           | 200          | Get a flag by ID; 404 if not found                   |
| `PATCH`  | `/flags/{id}`           | 200          | Partial update — only supplied fields are changed    |
| `DELETE` | `/flags/{id}`           | 204          | Delete a flag; 404 if not found                      |
| `GET`    | `/flags/{name}/evaluate`| 200          | Returns `{ "name": "…", "enabled": true/false }`     |

Error responses use RFC 9457 `ProblemDetail` — machine-readable `type`, `title`, `status`, `detail`, and `timestamp` fields on every error.

### Examples

```bash
# Create
curl -s -X POST http://localhost:8080/flags \
  -H "Content-Type: application/json" \
  -d '{"name":"dark-mode","description":"Enable dark mode UI","enabled":false}' | jq

# List
curl -s http://localhost:8080/flags | jq

# Get by ID
curl -s http://localhost:8080/flags/1 | jq

# Toggle / partial update
curl -s -X PATCH http://localhost:8080/flags/1 \
  -H "Content-Type: application/json" \
  -d '{"enabled":true}' | jq

# Evaluate by name
curl -s http://localhost:8080/flags/dark-mode/evaluate | jq

# Delete
curl -s -o /dev/null -w "%{http_code}" -X DELETE http://localhost:8080/flags/1
```

---

## Design Decisions

**Spring Boot 4.0.7 / Java 21** — Spring Boot 4 is built on Spring Framework 7 and Jakarta EE 11, making it the current long-term-supported baseline. Java 21 is the minimum required by the task and unlocks virtual threads with a single configuration flag (`spring.threads.virtual.enabled=true`) and zero application code changes.

**H2 file mode for persistence** — `jdbc:h2:file:./data/featureflags` survives restarts with no external infrastructure to set up. Switching to PostgreSQL requires only a driver dependency and a connection URL change — no application code changes — because the service depends on the JPA interface, not H2 directly.

**`FeatureFlagService` as an interface** — the controller depends on the interface, not the implementation. This makes the persistence backend swappable and the web layer independently testable via `@WebMvcTest` with a Mockito mock.

**`@Transactional(readOnly = true)` at class level** — applied to `FeatureFlagServiceImpl` so every method defaults to a read-only transaction; write methods (`create`, `update`, `delete`) override with a plain `@Transactional`. This lets Hibernate skip dirty-checking on reads and allows the JDBC driver to apply read-only optimisations where supported.

**RFC 9457 `ProblemDetail` for errors** — built into Spring Framework 7. Returns a consistent, machine-readable envelope for every error (404, 409, 422) without any custom serialisation code. Bean Validation failures produce a 422 with a field-level `errors` array.

**`open-in-view: false`** — prevents lazy-loading SQL from silently executing during HTTP response serialisation.

**`ddl-auto: update`** — acceptable for a showcase. Production would use Flyway migrations.

---

## Test Strategy

50 tests across four test slices:

- **`@DataJpaTest`** — repository layer against in-memory H2: ID generation, timestamp auto-population, `createdAt` immutability, `findByName`, `existsByName`, unique-constraint enforcement.
- **`@WebMvcTest` (exception handler)** — standalone slice with a stub controller verifying that every exception maps to the correct ProblemDetail shape and HTTP status.
- **Mockito unit tests** — service layer in isolation (no Spring context, no database): all business rules across create / findAll / findById / update / delete / evaluate.
- **`@WebMvcTest` (controller)** — full HTTP stack for all six endpoints: status codes, response bodies, Location header on POST, and error paths.

Integration tests (full Spring context + real DB) were deliberately omitted. The `@WebMvcTest` slices exercise the complete HTTP-to-service path and the Mockito tests cover all logic branches; a third layer would duplicate coverage without adding confidence.

---

## Future Improvements

- **Flyway migrations** — replace `ddl-auto: update` for safe schema evolution in production
- **Authentication** — API key header or OAuth2 token validation
- **Environment scoping** — per-environment flag values (dev / staging / prod)
- **Percentage rollouts** — enable a flag for X% of requests (gradual rollout)
- **Caching** — short-lived cache on the `evaluate` endpoint for high-throughput clients
