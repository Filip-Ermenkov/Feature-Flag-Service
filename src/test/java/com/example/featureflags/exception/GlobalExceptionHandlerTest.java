package com.example.featureflags.exception;

import com.example.featureflags.dto.CreateFlagRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
@org.springframework.context.annotation.Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @RestController
    static class TestController {

        @GetMapping("/test/flags/{id}")
        void notFoundById(@PathVariable Long id) {
            throw new FlagNotFoundException(id);
        }

        @GetMapping("/test/flags/name/{name}")
        void notFoundByName(@PathVariable String name) {
            throw new FlagNotFoundException(name);
        }

        @GetMapping("/test/flags/conflict")
        void duplicate() {
            throw new DuplicateFlagNameException("my-flag");
        }

        @PostMapping("/test/flags")
        void create(@Valid @RequestBody CreateFlagRequest req) {
            
        }
    }

    @Test
    void givenUnknownId_whenGet_then404ProblemDetail() throws Exception {
        mockMvc.perform(get("/test/flags/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Feature Flag Not Found"))
                .andExpect(jsonPath("$.detail").value("Feature flag not found with id: 99"))
                .andExpect(jsonPath("$.type").value("urn:problem-type:feature-flag-not-found"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void givenUnknownName_whenGet_then404ProblemDetail() throws Exception {
        mockMvc.perform(get("/test/flags/name/ghost-flag"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Feature flag not found with name: ghost-flag"));
    }

    @Test
    void givenDuplicateName_whenGet_then409ProblemDetail() throws Exception {
        mockMvc.perform(get("/test/flags/conflict"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Duplicate Flag Name"))
                .andExpect(jsonPath("$.detail").value("A feature flag with name 'my-flag' already exists"))
                .andExpect(jsonPath("$.type").value("urn:problem-type:duplicate-flag-name"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void givenMissingName_whenPost_then422WithFieldErrors() throws Exception {
        String body = """
                {"description":"test","enabled":true}
                """;

        mockMvc.perform(post("/test/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.type").value("urn:problem-type:validation-error"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void givenBlankName_whenPost_then422WithFieldErrors() throws Exception {
        String body = """
                {"name":"   ","description":"test","enabled":true}
                """;

        mockMvc.perform(post("/test/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void givenMissingEnabled_whenPost_then422WithFieldErrors() throws Exception {
        String body = """
                {"name":"my-flag"}
                """;

        mockMvc.perform(post("/test/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field=='enabled')]").exists());
    }
}
