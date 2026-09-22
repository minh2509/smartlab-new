package com.smartlab.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
    void malformedJsonReturnsBadRequestWithoutParserDetails() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(handler)
                .build();

        String body = mockMvc.perform(post("/public/json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
                .contains("Malformed request body")
                .doesNotContain("Json", "Jackson", "Unexpected character", "Source:");
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/public/failure")
        String fail() {
            throw new RuntimeException("JDBC SQL at /Users/private/secret.sql");
        }

        @PostMapping("/public/json")
        Map<String, Object> json(@RequestBody Map<String, Object> body) {
            return body;
        }
    }
}
