package com.example.featureflags.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Persistent entity representing a feature flag.
 *
 * <p>Timestamps are populated automatically by Hibernate via {@code @CreationTimestamp}
 * and {@code @UpdateTimestamp}, both sourced from the JVM clock and stored as UTC
 * {@link Instant} values.
 *
 * <p>Equality is ID-based using the pattern recommended by Vlad Mihalcea: two detached
 * instances with the same non-null ID are equal; a transient (unsaved) instance is only
 * equal to itself. The {@code hashCode} is constant so that entities can safely be
 * moved between {@code Set}s before and after persistence.
 */
@Entity
@Table(name = "feature_flags")
public class FeatureFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, unique = true)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private boolean enabled;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA – not for direct use by application code. */
    protected FeatureFlag() {}

    /**
     * Creates a new (unpersisted) feature flag.
     *
     * @param name        unique, human-readable identifier
     * @param description optional free-text description
     * @param enabled     initial enabled state
     */
    public FeatureFlag(String name, String description, boolean enabled) {
        this.name = name;
        this.description = description;
        this.enabled = enabled;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // -------------------------------------------------------------------------
    // Setters (only mutable fields are exposed)
    // -------------------------------------------------------------------------

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    // -------------------------------------------------------------------------
    // equals / hashCode (ID-based, proxy-safe)
    // -------------------------------------------------------------------------

    /**
     * Two {@code FeatureFlag} instances are equal when both have a non-null ID and
     * the IDs match. A transient instance (id == null) is only equal to itself.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FeatureFlag other)) return false;
        return id != null && id.equals(other.id);
    }

    /**
     * Constant hash code so that the same entity can safely live in a {@link java.util.HashSet}
     * before and after being persisted (i.e., before and after its ID is assigned).
     */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "FeatureFlag{id=%d, name='%s', enabled=%b}".formatted(id, name, enabled);
    }
}
