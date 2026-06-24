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
