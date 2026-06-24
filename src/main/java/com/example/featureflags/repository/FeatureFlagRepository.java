package com.example.featureflags.repository;

import com.example.featureflags.model.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link FeatureFlag}.
 *
 * <p>In addition to the full CRUD surface provided by {@link JpaRepository}, two
 * name-based query methods are declared:
 *
 * <ul>
 *   <li>{@link #findByName} – used by the lookup and evaluation endpoints.</li>
 *   <li>{@link #existsByName} – used for duplicate-name checks in the service layer
 *       without loading a full entity.</li>
 * </ul>
 */
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, Long> {

    /**
     * Returns the flag with the given name, or {@link Optional#empty()} if none exists.
     *
     * @param name the unique flag name (case-sensitive)
     * @return an {@link Optional} containing the flag, or empty
     */
    Optional<FeatureFlag> findByName(String name);

    /**
     * Returns {@code true} if a flag with the given name already exists.
     *
     * <p>Prefer this over {@link #findByName} when only existence needs to be checked,
     * as it avoids fetching and hydrating a full entity.
     *
     * @param name the flag name to check
     * @return {@code true} if a flag with this name exists
     */
    boolean existsByName(String name);
}
