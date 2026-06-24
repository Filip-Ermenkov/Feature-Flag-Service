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
