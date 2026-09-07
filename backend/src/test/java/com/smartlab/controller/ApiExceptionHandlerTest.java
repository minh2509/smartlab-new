package com.smartlab.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @RestController
    static class ThrowingController {
        @GetMapping("/public/failure")
        String fail() {
            throw new RuntimeException("JDBC SQL at /Users/private/secret.sql");
        }
    }
}
