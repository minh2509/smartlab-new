package com.smartlab.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void responseStatusKeepsStatusAndSafeReason() {
        var response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.FORBIDDEN, "File access denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("File access denied");
    }

    @Test
    void unexpectedServerFailureReturnsGenericSafeEnvelope() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(handler)
                .build();

        String body = mockMvc.perform(get("/public/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
                .contains("Internal server error")
                .doesNotContain("JdbcSQL", "SQL", "RuntimeException", "java.lang", "/Users/");
    }

    @Test
    void unreadableRequestBodiesReturnSafeBadRequestEnvelopeThroughMvc() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new BodyController())
                .setControllerAdvice(handler)
                .build();

        for (String body : new String[]{"{\"contentJson\":", "{\"contentJson\":[]}"}) {
            String response = mockMvc.perform(post("/public/body")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.error").value(true))
                    .andExpect(jsonPath("$.message").value("Invalid request body"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertThat(response).doesNotContain(
                    "HttpMessageNotReadableException",
                    "JsonMappingException",
                    "MismatchedInputException",
                    "Cannot deserialize",
                    "java.lang",
                    "com.smartlab",
                    "/Users/",
                    "SQL",
                    "JDBC",
                    "PostgreSQL",
                    "credential",
                    "token");
        }
    }

    @Test
    void validationAndPathTypeMismatchRemainBadRequestsThroughMvc() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new BodyController())
                .setControllerAdvice(handler)
                .build();

        mockMvc.perform(post("/public/validated-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/public/number/not-a-number"))
                .andExpect(status().isBadRequest());
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/public/failure")
        String fail() {
            throw new RuntimeException("JDBC SQL at /Users/private/secret.sql");
        }
    }

    @RestController
    static class BodyController {
        @PostMapping("/public/body")
        String accept(@RequestBody Body body) {
            return "accepted";
        }

        @PostMapping("/public/validated-body")
        String acceptValidated(@Valid @RequestBody ValidatedBody body) {
            return "accepted";
        }

        @GetMapping("/public/number/{value}")
        String acceptNumber(@PathVariable int value) {
            return String.valueOf(value);
        }
    }

    record Body(Map<String, Object> contentJson) {
    }

    record ValidatedBody(@NotBlank(message = "Name is required") String name) {
    }
}
