package com.example.featureflags.controller;

import com.example.featureflags.dto.CreateFlagRequest;
import com.example.featureflags.dto.EvaluateResponse;
import com.example.featureflags.dto.FlagResponse;
import com.example.featureflags.dto.UpdateFlagRequest;
import com.example.featureflags.exception.DuplicateFlagNameException;
import com.example.featureflags.exception.FlagNotFoundException;
import com.example.featureflags.service.FeatureFlagService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebMvcTest(FeatureFlagController.class)
class FeatureFlagControllerTest {

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    FeatureFlagService service;

    private static final Instant FIXED = Instant.parse("2024-01-01T00:00:00Z");

    private static final FlagResponse SAMPLE =
            new FlagResponse(1L, "dark-mode", "Enable dark mode UI", true, FIXED, FIXED);

    @Nested
    @DisplayName("POST /flags")
    class Create {

        @Test
        @DisplayName("returns 201 Created with Location header and flag body on success")
        void create_returns201WithLocationAndBody() {
            when(service.create(any(CreateFlagRequest.class))).thenReturn(SAMPLE);

            MvcTestResult result = mockMvc.post().uri("/flags")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name":"dark-mode","description":"Enable dark mode UI","enabled":true}
                            """)
                    .exchange();

            assertThat(result).hasStatus(HttpStatus.CREATED);
            assertThat(result).containsHeader(HttpHeaders.LOCATION);
            assertThat(result).bodyJson()
                    .convertTo(FlagResponse.class)
                    .satisfies(r -> {
                        assertThat(r.id()).isEqualTo(1L);
                        assertThat(r.name()).isEqualTo("dark-mode");
                        assertThat(r.enabled()).isTrue();
                    });
        }

        @Test
        @DisplayName("returns 409 Conflict with ProblemDetail when flag name already exists")
        void create_returns409WhenDuplicate() {
            when(service.create(any(CreateFlagRequest.class)))
                    .thenThrow(new DuplicateFlagNameException("dark-mode"));

            assertThat(mockMvc.post().uri("/flags")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name":"dark-mode","description":null,"enabled":true}
                            """))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            {
                              "status": 409,
                              "title": "Duplicate Flag Name",
                              "type": "urn:problem-type:duplicate-flag-name"
                            }
                            """);
        }

        @Test
        @DisplayName("returns 422 Unprocessable Entity with field errors when name is blank")
        void create_returns422WhenNameBlank() {
            MvcTestResult result = mockMvc.post().uri("/flags")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name":"  ","description":"test","enabled":true}
                            """)
                    .exchange();

            assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(result).bodyJson()
                    .isLenientlyEqualTo("""
                            {
                              "status": 422,
                              "title": "Validation Error",
                              "type": "urn:problem-type:validation-error",
                              "errors": [{"field": "name"}]
                            }
                            """);
        }

        @Test
        @DisplayName("returns 422 Unprocessable Entity with field errors when enabled field is missing")
        void create_returns422WhenEnabledMissing() {
            assertThat(mockMvc.post().uri("/flags")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name":"dark-mode"}
                            """))
                    .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            {"status": 422, "errors": [{"field": "enabled"}]}
                            """);
        }
    }

    @Nested
    @DisplayName("GET /flags")
    class FindAll {

        @Test
        @DisplayName("returns 200 with array of all flags")
        void findAll_returns200WithFlags() {
            FlagResponse second =
                    new FlagResponse(2L, "beta-ui", null, false, FIXED, FIXED);
            when(service.findAll()).thenReturn(List.of(SAMPLE, second));

            assertThat(mockMvc.get().uri("/flags"))
                    .hasStatusOk()
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            [
                              {"id":1,"name":"dark-mode","enabled":true},
                              {"id":2,"name":"beta-ui","enabled":false}
                            ]
                            """);
        }

        @Test
        @DisplayName("returns 200 with empty JSON array when no flags exist")
        void findAll_returns200EmptyArray() {
            when(service.findAll()).thenReturn(List.of());

            assertThat(mockMvc.get().uri("/flags"))
                    .hasStatusOk()
                    .bodyJson()
                    .isLenientlyEqualTo("[]");
        }
    }

    @Nested
    @DisplayName("GET /flags/{id}")
    class FindById {

        @Test
        @DisplayName("returns 200 with flag body when flag exists")
        void findById_returns200WhenFound() {
            when(service.findById(1L)).thenReturn(SAMPLE);

            assertThat(mockMvc.get().uri("/flags/{id}", 1L))
                    .hasStatusOk()
                    .bodyJson()
                    .convertTo(FlagResponse.class)
                    .satisfies(r -> {
                        assertThat(r.id()).isEqualTo(1L);
                        assertThat(r.name()).isEqualTo("dark-mode");
                        assertThat(r.enabled()).isTrue();
                    });
        }

        @Test
        @DisplayName("returns 404 ProblemDetail when flag id does not exist")
        void findById_returns404WhenNotFound() {
            when(service.findById(99L)).thenThrow(new FlagNotFoundException(99L));

            assertThat(mockMvc.get().uri("/flags/{id}", 99L))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            {
                              "status": 404,
                              "title": "Feature Flag Not Found",
                              "type": "urn:problem-type:feature-flag-not-found",
                              "detail": "Feature flag not found with id: 99"
                            }
                            """);
        }
    }

    @Nested
    @DisplayName("PATCH /flags/{id}")
    class Update {

        @Test
        @DisplayName("returns 200 with updated flag body")
        void update_returns200WhenFound() {
            FlagResponse updated =
                    new FlagResponse(1L, "dark-mode", "Updated desc", false, FIXED, Instant.now());
            when(service.update(eq(1L), any(UpdateFlagRequest.class))).thenReturn(updated);

            assertThat(mockMvc.patch().uri("/flags/{id}", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"enabled":false}
                            """))
                    .hasStatusOk()
                    .bodyJson()
                    .convertTo(FlagResponse.class)
                    .satisfies(r -> {
                        assertThat(r.id()).isEqualTo(1L);
                        assertThat(r.enabled()).isFalse();
                    });
        }

        @Test
        @DisplayName("returns 404 ProblemDetail when flag id does not exist")
        void update_returns404WhenNotFound() {
            when(service.update(eq(99L), any(UpdateFlagRequest.class)))
                    .thenThrow(new FlagNotFoundException(99L));

            assertThat(mockMvc.patch().uri("/flags/{id}", 99L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"enabled":false}
                            """))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            {"status": 404, "title": "Feature Flag Not Found"}
                            """);
        }

        @Test
        @DisplayName("returns 409 Conflict when the new name is already taken")
        void update_returns409WhenNewNameDuplicate() {
            when(service.update(eq(1L), any(UpdateFlagRequest.class)))
                    .thenThrow(new DuplicateFlagNameException("beta-ui"));

            assertThat(mockMvc.patch().uri("/flags/{id}", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name":"beta-ui"}
                            """))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            {"status": 409, "title": "Duplicate Flag Name"}
                            """);
        }
    }

    @Nested
    @DisplayName("DELETE /flags/{id}")
    class Delete {

        @Test
        @DisplayName("returns 204 No Content and delegates to service when flag exists")
        void delete_returns204WhenFound() {
            assertThat(mockMvc.delete().uri("/flags/{id}", 1L))
                    .hasStatus(HttpStatus.NO_CONTENT);

            verify(service).delete(1L);
        }

        @Test
        @DisplayName("returns 404 ProblemDetail when flag id does not exist")
        void delete_returns404WhenNotFound() {
            doThrow(new FlagNotFoundException(99L)).when(service).delete(99L);

            assertThat(mockMvc.delete().uri("/flags/{id}", 99L))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            {"status": 404, "title": "Feature Flag Not Found"}
                            """);
        }
    }

    @Nested
    @DisplayName("GET /flags/{name}/evaluate")
    class Evaluate {

        @Test
        @DisplayName("returns 200 with name and enabled state when flag exists")
        void evaluate_returns200WhenFound() {
            when(service.evaluate("dark-mode"))
                    .thenReturn(new EvaluateResponse("dark-mode", true));

            assertThat(mockMvc.get().uri("/flags/{name}/evaluate", "dark-mode"))
                    .hasStatusOk()
                    .bodyJson()
                    .convertTo(EvaluateResponse.class)
                    .satisfies(r -> {
                        assertThat(r.name()).isEqualTo("dark-mode");
                        assertThat(r.enabled()).isTrue();
                    });
        }

        @Test
        @DisplayName("returns 404 ProblemDetail when no flag matches the name")
        void evaluate_returns404WhenNotFound() {
            when(service.evaluate("ghost")).thenThrow(new FlagNotFoundException("ghost"));

            assertThat(mockMvc.get().uri("/flags/{name}/evaluate", "ghost"))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            {
                              "status": 404,
                              "title": "Feature Flag Not Found",
                              "detail": "Feature flag not found with name: ghost"
                            }
                            """);
        }
    }
}
