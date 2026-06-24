# Domain Model

## FeatureFlag entity

`com.example.featureflags.model.FeatureFlag` is the single JPA entity in the service.

### Table: `feature_flags`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `BIGINT` | PK, auto-increment | Generated via `IDENTITY` strategy |
| `name` | `VARCHAR(255)` | NOT NULL, UNIQUE | Human-readable identifier; case-sensitive |
| `description` | `TEXT` | nullable | Optional free-text description |
| `enabled` | `BOOLEAN` | NOT NULL | Current on/off state |
| `created_at` | `TIMESTAMP(6) WITH TIME ZONE` | NOT NULL | Set on `INSERT`, never changed |
| `updated_at` | `TIMESTAMP(6) WITH TIME ZONE` | NOT NULL | Updated on every `UPDATE` |

### Timestamps

Both timestamp columns are managed automatically by Hibernate:

- `created_at` — `@CreationTimestamp` (synonym for `@CurrentTimestamp(timing=INSERT, source=VM)`). Marked `updatable = false` at the column level so no accidental override is possible.
- `updated_at` — `@UpdateTimestamp` (synonym for `@CurrentTimestamp(timing=ALWAYS, source=VM)`).

Both fields use `java.time.Instant`, which Hibernate maps to `TIMESTAMP(6) WITH TIME ZONE` (confirmed by the DDL Hibernate generates at startup). Values are stored in UTC; conversion to a local timezone is left to the API response layer.

### equals / hashCode

The entity uses the ID-based equality pattern recommended by Vlad Mihalcea:

- `equals` — two instances are equal if both have a non-null `id` and the IDs match. A transient (unsaved) instance is equal only to itself.
- `hashCode` — returns `getClass().hashCode()` (a constant). This ensures that the same entity object remains stable in a `HashSet` or `HashMap` before and after it is first persisted (i.e. before and after its `id` is assigned by the database).

This pattern is safe under Hibernate proxying because the `instanceof` operator (used in the Java 21 pattern-matching form) matches the proxy subclass correctly.

## FeatureFlagRepository

`com.example.featureflags.repository.FeatureFlagRepository` extends `JpaRepository<FeatureFlag, Long>` and adds two derived query methods:

| Method | Returns | Purpose |
|---|---|---|
| `findByName(String name)` | `Optional<FeatureFlag>` | Load a flag by its unique name |
| `existsByName(String name)` | `boolean` | Duplicate-name check without loading the entity |

`existsByName` is preferred over `findByName` in the service's duplicate-check path because it issues `SELECT id ... LIMIT 1` rather than fetching and hydrating a full entity (confirmed by Hibernate SQL logs).

## Test coverage

`FeatureFlagRepositoryTest` is a `@DataJpaTest` slice test (JPA layer only, no web layer, in-memory H2). It covers:

- ID generation on `save`
- `createdAt` / `updatedAt` auto-population
- `createdAt` immutability after an update
- `findByName` – match, no match, case-sensitivity
- `existsByName` – match, no match
- Unique constraint enforcement (duplicate name → `DataIntegrityViolationException`)
- `findAll` and `deleteById`
- `equals` / `hashCode` contract for persisted and transient instances

---

## DTOs (`com.example.featureflags.dto`)

All DTOs are Java records — immutable, with compiler-generated constructor, getters, `equals`, `hashCode`, and `toString`. Bean Validation annotations are null-safe unless stated otherwise.

| Record | Used by | Fields | Validation |
|---|---|---|---|
| `CreateFlagRequest` | `POST /flags` | `name`, `description`, `enabled` | `name`: `@NotBlank @Size(max=255)`; `enabled`: `@NotNull` |
| `UpdateFlagRequest` | `PATCH /flags/{id}` | `name`, `description`, `enabled` | `name`: `@Size(min=1, max=255)` (null-safe — null means "leave unchanged") |
| `FlagResponse` | all read endpoints | `id`, `name`, `description`, `enabled`, `createdAt`, `updatedAt` | none; includes static factory `FlagResponse.from(FeatureFlag)` |
| `EvaluateResponse` | `GET /flags/{name}/evaluate` | `name`, `enabled` | none |

`Instant` fields (`createdAt`, `updatedAt`) are serialised as ISO-8601 strings by Jackson 3's `JavaTimeModule` (e.g. `"2026-06-24T10:00:00Z"`).

---

## Domain exceptions (`com.example.featureflags.exception`)

| Exception | Thrown when | HTTP status |
|---|---|---|
| `FlagNotFoundException(Long id)` | lookup by ID finds nothing | 404 |
| `FlagNotFoundException(String name)` | lookup by name finds nothing (evaluate endpoint) | 404 |
| `DuplicateFlagNameException(String name)` | create/update would produce a duplicate name | 409 |

### GlobalExceptionHandler

`@RestControllerAdvice` extending Spring's `ResponseEntityExceptionHandler`. Maps every exception to an RFC 9457 `ProblemDetail` body:

```json
{
  "type":      "urn:problem-type:feature-flag-not-found",
  "title":     "Feature Flag Not Found",
  "status":    404,
  "detail":    "Feature flag not found with id: 42",
  "timestamp": "2026-06-24T10:00:00Z"
}
```

Bean Validation failures (`@Valid` on a request body) are mapped to `422 Unprocessable Entity` with a per-field `errors` array. All other Spring MVC exceptions (missing parameter, type mismatch, etc.) are handled by the parent class and also return `ProblemDetail`.

### Test coverage

`GlobalExceptionHandlerTest` is a `@WebMvcTest` slice with an embedded stub `@RestController`. It covers:

- `FlagNotFoundException` by ID → 404 with correct `type`, `title`, `detail`
- `FlagNotFoundException` by name → 404 with name in `detail`
- `DuplicateFlagNameException` → 409 with correct `type`, `title`
- Missing required field (`name`) → 422 with `errors[0].field = "name"`
- Blank `name` field → 422
- Missing `enabled` field → 422 with entry for `enabled` in `errors`

**`@MockitoBean FeatureFlagService` required** — the test uses bare `@WebMvcTest` (no controller class specified), so Spring scans and loads all `@RestController` beans in the package, including `FeatureFlagController`. That controller requires a `FeatureFlagService` bean; without a mock, the application context fails to start. The `@MockitoBean` satisfies the dependency without involving the service's behaviour in these tests.

---

## Service layer (`com.example.featureflags.service`)

### Interface: `FeatureFlagService`

Defines the business contract consumed by the controller layer. Having a separate interface decouples callers from the implementation, making `@WebMvcTest` slices easy to write (the service is mocked via the interface) and the implementation swappable.

| Method | Returns | Throws |
|---|---|---|
| `create(CreateFlagRequest)` | `FlagResponse` | `DuplicateFlagNameException` |
| `findAll()` | `List<FlagResponse>` | — |
| `findById(Long id)` | `FlagResponse` | `FlagNotFoundException` |
| `update(Long id, UpdateFlagRequest)` | `FlagResponse` | `FlagNotFoundException`, `DuplicateFlagNameException` |
| `delete(Long id)` | `void` | `FlagNotFoundException` |
| `evaluate(String name)` | `EvaluateResponse` | `FlagNotFoundException` |

### Implementation: `FeatureFlagServiceImpl`

Annotated `@Service`. Transaction strategy:

- **Class level** `@Transactional(readOnly = true)` — default for every method; Hibernate skips dirty-checking and the JDBC driver may apply read-only optimisations.
- **Write methods** (`create`, `update`, `delete`) override with plain `@Transactional`, which opens a read-write transaction.

The annotation is placed on the implementation class (not the interface) so that Spring's CGLIB proxy intercepts calls reliably regardless of injection-point type.

**PATCH semantics** (`update`): a `null` field in `UpdateFlagRequest` means "leave this field unchanged". Name-change duplicate check is skipped if the submitted name is identical to the current name.

### Test coverage

`FeatureFlagServiceTest` uses `@ExtendWith(MockitoExtension.class)` — no Spring context, no database. The repository is mocked with `@Mock`; `@InjectMocks` wires it into `FeatureFlagServiceImpl`. `ReflectionTestUtils.setField` sets the entity `id` (which is otherwise JPA-managed and has no public setter).

15 tests across 6 `@Nested` groups:

| Group | Scenarios |
|---|---|
| `create()` | success; duplicate name → exception, `save` never called |
| `findAll()` | returns all; returns empty list |
| `findById()` | found; not found → exception |
| `update()` | partial fields (PATCH); rename success; same name → no duplicate check; rename to taken name → exception; id not found → exception |
| `delete()` | success; id not found → exception, `deleteById` never called |
| `evaluate()` | found; not found → exception |

---

## Controller layer (`com.example.featureflags.controller`)

### `FeatureFlagController`

`@RestController @RequestMapping("/flags")`. Handles HTTP concerns only — deserialises the request body, calls the service, serialises the response, and returns the correct status code. No business logic lives here.

| Method | Path | Handler | Success body | Notes |
|---|---|---|---|---|
| `POST` | `/flags` | `create` | `FlagResponse` | 201; `Location: /flags/{id}` header set via `ServletUriComponentsBuilder` |
| `GET` | `/flags` | `findAll` | `List<FlagResponse>` | 200; always a JSON array (empty if none) |
| `GET` | `/flags/{id}` | `findById` | `FlagResponse` | 200 |
| `PATCH` | `/flags/{id}` | `update` | `FlagResponse` | 200; delegates PATCH semantics to service |
| `DELETE` | `/flags/{id}` | `delete` | — | 204 No Content |
| `GET` | `/flags/{name}/evaluate` | `evaluate` | `EvaluateResponse` | 200; lookup by name, not id |

**Location header** — on `POST /flags`, the response carries a `Location` header pointing to the newly created resource (`/flags/{id}`). This follows RFC 7231 §6.3.2, which states that a 201 response *should* include a `Location` field.

**Bean Validation** — `@Valid` is applied to request bodies on `POST` and `PATCH`. Constraint violations trigger Spring's `MethodArgumentNotValidException`, which `GlobalExceptionHandler` maps to `422 Unprocessable Entity` with a field-level `errors` array.

**Dependency injection** — the constructor accepts `FeatureFlagService` (interface). This makes the controller testable with any `@MockitoBean` substitute in `@WebMvcTest` slices without loading the full application context.

### Test coverage

`FeatureFlagControllerTest` is a `@WebMvcTest(FeatureFlagController.class)` slice. It uses Spring Framework 7's `MockMvcTester` — an AssertJ-based wrapper around `MockMvc` introduced in Spring Framework 6.2 that eliminates checked exceptions and static-import noise. `MockMvcTester` is auto-configured by `@WebMvcTest` when AssertJ is on the classpath (it is, via `spring-boot-starter-test`).

`@MockitoBean FeatureFlagService service` provides a Mockito mock of the service interface. The `GlobalExceptionHandler` is picked up automatically because `@WebMvcTest` loads all `@RestControllerAdvice` beans.

15 tests across 6 `@Nested` groups:

| Group | Scenarios |
|---|---|
| `POST /flags` | 201 + Location header + body; 409 on duplicate name; 422 on blank name; 422 on missing `enabled` |
| `GET /flags` | 200 with flags array; 200 with empty array |
| `GET /flags/{id}` | 200 with body; 404 ProblemDetail |
| `PATCH /flags/{id}` | 200 with updated body; 404 ProblemDetail; 409 on duplicate name |
| `DELETE /flags/{id}` | 204 and verifies service call; 404 ProblemDetail |
| `GET /flags/{name}/evaluate` | 200 with name + enabled; 404 ProblemDetail with name in detail |

### Mockito agent configuration

Java 21 restricts dynamic agent loading, causing Mockito to emit a self-attaching warning during tests when it loads ByteBuddy without an explicit `-javaagent` flag. The `pom.xml` resolves this cleanly:

1. `maven-dependency-plugin:properties` goal runs before `test` and sets `${org.mockito:mockito-core:jar}` to the absolute path of the resolved Mockito JAR.
2. `maven-surefire-plugin` is configured with `argLine = @{argLine} -javaagent:${org.mockito:mockito-core:jar}`, attaching Mockito as a proper JVM agent for every test JVM.

The `@{argLine}` late-binding placeholder (rather than `${argLine}`) preserves any `argLine` value set by other plugins (e.g. JaCoCo for code coverage) so they can coexist without overwriting each other. Because no such plugin is currently configured, `argLine` is declared as an empty property in `<properties>` — without this default, `@{argLine}` resolves to the literal string `{argLine}`, which the JVM misreads as a `@file` argument and crashes immediately.
