package com.example.featureflags.service;

import com.example.featureflags.dto.CreateFlagRequest;
import com.example.featureflags.dto.EvaluateResponse;
import com.example.featureflags.dto.FlagResponse;
import com.example.featureflags.dto.UpdateFlagRequest;
import com.example.featureflags.exception.DuplicateFlagNameException;
import com.example.featureflags.exception.FlagNotFoundException;
import com.example.featureflags.model.FeatureFlag;
import com.example.featureflags.repository.FeatureFlagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeatureFlagServiceTest {

    @Mock
    private FeatureFlagRepository repository;

    @InjectMocks
    private FeatureFlagServiceImpl service;

    private FeatureFlag existingFlag;

    @BeforeEach
    void setUp() {
        existingFlag = new FeatureFlag("dark-mode", "Enable dark mode UI", true);
        ReflectionTestUtils.setField(existingFlag, "id", 1L);
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("persists and returns a FlagResponse when name is unique")
        void create_success() {
            when(repository.existsByName("dark-mode")).thenReturn(false);
            when(repository.save(any(FeatureFlag.class))).thenReturn(existingFlag);

            FlagResponse result = service.create(
                    new CreateFlagRequest("dark-mode", "Enable dark mode UI", true));

            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.name()).isEqualTo("dark-mode");
            assertThat(result.enabled()).isTrue();
            verify(repository).save(any(FeatureFlag.class));
        }

        @Test
        @DisplayName("throws DuplicateFlagNameException when name already exists")
        void create_duplicateName_throws() {
            when(repository.existsByName("dark-mode")).thenReturn(true);

            assertThatThrownBy(() ->
                    service.create(new CreateFlagRequest("dark-mode", null, true)))
                    .isInstanceOf(DuplicateFlagNameException.class)
                    .hasMessageContaining("dark-mode");

            verify(repository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("returns a list of FlagResponses for every persisted flag")
        void findAll_returnsAll() {
            FeatureFlag second = new FeatureFlag("beta-ui", null, false);
            ReflectionTestUtils.setField(second, "id", 2L);

            when(repository.findAll()).thenReturn(List.of(existingFlag, second));

            List<FlagResponse> results = service.findAll();

            assertThat(results).hasSize(2);
            assertThat(results).extracting(FlagResponse::name)
                    .containsExactly("dark-mode", "beta-ui");
        }

        @Test
        @DisplayName("returns an empty list when no flags exist")
        void findAll_empty() {
            when(repository.findAll()).thenReturn(List.of());

            assertThat(service.findAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("returns a FlagResponse when the flag exists")
        void findById_found() {
            when(repository.findById(1L)).thenReturn(Optional.of(existingFlag));

            FlagResponse result = service.findById(1L);

            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.name()).isEqualTo("dark-mode");
        }

        @Test
        @DisplayName("throws FlagNotFoundException when the id does not exist")
        void findById_notFound_throws() {
            when(repository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(99L))
                    .isInstanceOf(FlagNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("applies only non-null fields and returns updated FlagResponse")
        void update_partialFields() {
            when(repository.findById(1L)).thenReturn(Optional.of(existingFlag));
            when(repository.save(existingFlag)).thenReturn(existingFlag);

            FlagResponse result = service.update(1L, new UpdateFlagRequest(null, null, false));

            assertThat(result.enabled()).isFalse();
            assertThat(result.name()).isEqualTo("dark-mode");
        }

        @Test
        @DisplayName("renames the flag when the new name is unique")
        void update_rename_success() {
            when(repository.findById(1L)).thenReturn(Optional.of(existingFlag));
            when(repository.existsByName("light-mode")).thenReturn(false);
            when(repository.save(existingFlag)).thenReturn(existingFlag);

            FlagResponse result = service.update(1L, new UpdateFlagRequest("light-mode", null, null));

            assertThat(result.name()).isEqualTo("light-mode");
        }

        @Test
        @DisplayName("skips duplicate check when name is unchanged")
        void update_sameName_noDuplicateCheck() {
            when(repository.findById(1L)).thenReturn(Optional.of(existingFlag));
            when(repository.save(existingFlag)).thenReturn(existingFlag);

            service.update(1L, new UpdateFlagRequest("dark-mode", null, null));

            verify(repository, never()).existsByName(any());
        }

        @Test
        @DisplayName("throws DuplicateFlagNameException when the new name is already taken")
        void update_rename_duplicate_throws() {
            when(repository.findById(1L)).thenReturn(Optional.of(existingFlag));
            when(repository.existsByName("beta-ui")).thenReturn(true);

            assertThatThrownBy(() ->
                    service.update(1L, new UpdateFlagRequest("beta-ui", null, null)))
                    .isInstanceOf(DuplicateFlagNameException.class)
                    .hasMessageContaining("beta-ui");

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("throws FlagNotFoundException when the id does not exist")
        void update_notFound_throws() {
            when(repository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    service.update(99L, new UpdateFlagRequest(null, null, false)))
                    .isInstanceOf(FlagNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("deletes the flag when it exists")
        void delete_success() {
            when(repository.existsById(1L)).thenReturn(true);

            service.delete(1L);

            verify(repository).deleteById(1L);
        }

        @Test
        @DisplayName("throws FlagNotFoundException when the id does not exist")
        void delete_notFound_throws() {
            when(repository.existsById(99L)).thenReturn(false);

            assertThatThrownBy(() -> service.delete(99L))
                    .isInstanceOf(FlagNotFoundException.class)
                    .hasMessageContaining("99");

            verify(repository, never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("evaluate()")
    class Evaluate {

        @Test
        @DisplayName("returns EvaluateResponse with name and enabled state when flag exists")
        void evaluate_found() {
            when(repository.findByName("dark-mode")).thenReturn(Optional.of(existingFlag));

            EvaluateResponse result = service.evaluate("dark-mode");

            assertThat(result.name()).isEqualTo("dark-mode");
            assertThat(result.enabled()).isTrue();
        }

        @Test
        @DisplayName("throws FlagNotFoundException when no flag matches the name")
        void evaluate_notFound_throws() {
            when(repository.findByName("unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.evaluate("unknown"))
                    .isInstanceOf(FlagNotFoundException.class)
                    .hasMessageContaining("unknown");
        }
    }
}
