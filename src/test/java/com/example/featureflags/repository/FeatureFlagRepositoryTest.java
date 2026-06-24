package com.example.featureflags.repository;

import com.example.featureflags.model.FeatureFlag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JPA slice tests for {@link FeatureFlagRepository}.
 *
 * <p>Uses {@code @DataJpaTest} which boots only the JPA layer with an in-memory H2
 * database and wraps each test in a transaction that is rolled back after the test,
 * keeping tests independent.
 */
@DataJpaTest
class FeatureFlagRepositoryTest {

    @Autowired
    private FeatureFlagRepository repository;

    @Autowired
    private TestEntityManager em;

    // -------------------------------------------------------------------------
    // save / findById
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("persists a flag and assigns a generated ID")
    void save_persistsEntityAndAssignsId() {
        FeatureFlag flag = new FeatureFlag("dark-mode", "Enable dark UI theme", false);

        FeatureFlag saved = repository.save(flag);

        assertThat(saved.getId()).isNotNull().isPositive();
    }

    @Test
    @DisplayName("auto-populates createdAt and updatedAt on first save")
    void save_populatesAuditTimestamps() {
        FeatureFlag flag = new FeatureFlag("new-checkout", null, true);
        repository.save(flag);
        em.flush(); // ensure Hibernate writes to DB and triggers @CreationTimestamp

        FeatureFlag loaded = em.find(FeatureFlag.class, flag.getId());

        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("createdAt is immutable after update")
    void save_createdAtDoesNotChangeOnUpdate() {
        FeatureFlag flag = new FeatureFlag("beta-ui", "Beta UI rollout", false);
        repository.save(flag);
        em.flush();
        em.clear(); // detach so next load is a fresh DB read

        FeatureFlag loaded = repository.findById(flag.getId()).orElseThrow();
        var originalCreatedAt = loaded.getCreatedAt();

        loaded.setEnabled(true);
        repository.save(loaded);
        em.flush();
        em.clear();

        FeatureFlag reloaded = repository.findById(flag.getId()).orElseThrow();
        assertThat(reloaded.getCreatedAt()).isEqualTo(originalCreatedAt);
    }

    // -------------------------------------------------------------------------
    // findByName
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findByName returns the flag when name matches")
    void findByName_returnsFlag_whenNameMatches() {
        FeatureFlag flag = new FeatureFlag("payment-v2", "New payment flow", true);
        em.persistAndFlush(flag);

        Optional<FeatureFlag> result = repository.findByName("payment-v2");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("payment-v2");
        assertThat(result.get().isEnabled()).isTrue();
    }

    @Test
    @DisplayName("findByName returns empty when name does not match")
    void findByName_returnsEmpty_whenNameAbsent() {
        Optional<FeatureFlag> result = repository.findByName("nonexistent-flag");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByName is case-sensitive")
    void findByName_isCaseSensitive() {
        em.persistAndFlush(new FeatureFlag("CasedFlag", null, false));

        assertThat(repository.findByName("casedflag")).isEmpty();
        assertThat(repository.findByName("CASEDFLAG")).isEmpty();
        assertThat(repository.findByName("CasedFlag")).isPresent();
    }

    // -------------------------------------------------------------------------
    // existsByName
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("existsByName returns true when flag exists")
    void existsByName_returnsTrue_whenFlagExists() {
        em.persistAndFlush(new FeatureFlag("feature-x", null, false));

        assertThat(repository.existsByName("feature-x")).isTrue();
    }

    @Test
    @DisplayName("existsByName returns false when flag absent")
    void existsByName_returnsFalse_whenFlagAbsent() {
        assertThat(repository.existsByName("ghost-flag")).isFalse();
    }

    // -------------------------------------------------------------------------
    // unique constraint
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("saving two flags with the same name throws DataIntegrityViolationException")
    void save_throwsOnDuplicateName() {
        em.persistAndFlush(new FeatureFlag("unique-flag", null, true));

        FeatureFlag duplicate = new FeatureFlag("unique-flag", "Different description", false);

        assertThatThrownBy(() -> {
            repository.saveAndFlush(duplicate);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    // -------------------------------------------------------------------------
    // findAll / deleteById
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findAll returns all persisted flags")
    void findAll_returnsAllFlags() {
        em.persistAndFlush(new FeatureFlag("flag-a", null, true));
        em.persistAndFlush(new FeatureFlag("flag-b", null, false));

        List<FeatureFlag> all = repository.findAll();

        assertThat(all).hasSize(2)
                .extracting(FeatureFlag::getName)
                .containsExactlyInAnyOrder("flag-a", "flag-b");
    }

    @Test
    @DisplayName("deleteById removes the flag")
    void deleteById_removesFlag() {
        FeatureFlag flag = em.persistAndFlush(new FeatureFlag("to-delete", null, true));
        Long id = flag.getId();

        repository.deleteById(id);
        em.flush();

        assertThat(repository.findById(id)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // equals / hashCode contract
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("two references to the same persisted flag are equal")
    void equals_twoRefsToSamePersistedFlag_areEqual() {
        FeatureFlag flag = em.persistAndFlush(new FeatureFlag("eq-test", null, false));
        em.clear();

        FeatureFlag a = repository.findById(flag.getId()).orElseThrow();
        FeatureFlag b = repository.findById(flag.getId()).orElseThrow();

        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("two unpersisted flags with different data are not equal to each other")
    void equals_twoTransientFlags_areOnlyEqualToThemselves() {
        FeatureFlag a = new FeatureFlag("flag-1", null, true);
        FeatureFlag b = new FeatureFlag("flag-2", null, false);

        assertThat(a).isNotEqualTo(b);
        assertThat(a).isEqualTo(a); // reflexive
    }
}
