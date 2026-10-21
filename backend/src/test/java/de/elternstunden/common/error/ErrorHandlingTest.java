package de.elternstunden.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.elternstunden.auth.repo.AppUserRepository;
import de.elternstunden.common.config.AppConfig;
import de.elternstunden.common.security.ApiSecurityConfig;
import de.elternstunden.common.security.ProblemSecurityHandler;
import de.elternstunden.common.security.SecurityBeans;
import de.elternstunden.common.security.SessionLogin;
import de.elternstunden.common.time.TimeConfig;
import de.elternstunden.common.web.TraceIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The unified error format (Lastenheft 8.1) for the typical error classes. */
@WebMvcTest(controllers = ErrorHandlingTest.ProbeController.class)
@ActiveProfiles("test")
@Import({
    AppConfig.class,
    TimeConfig.class,
    SecurityBeans.class,
    SessionLogin.class,
    ApiSecurityConfig.class,
    ProblemSecurityHandler.class,
    ProblemDetailsFactory.class,
    ErrorHandlingTest.ProbeController.class,
    ErrorHandlingTest.ProbeSecurity.class
})
@ExtendWith(OutputCaptureExtension.class)
class ErrorHandlingTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AppUserRepository users;

    @Test
    void apiExceptionKeepsItsCodeAndDetail() throws Exception {
        expectProblem(mvc.perform(get("/api/v1/test/api-exception")), 404, "NOT_FOUND")
                .andExpect(jsonPath("$.detail").value("Resource not found."));
    }

    @Test
    void beanValidationListsErrorsWithoutTheRejectedValue() throws Exception {
        MvcResult result = expectProblem(
                        mvc.perform(post("/api/v1/test/validated")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"description\":\"zq\"}")),
                        400,
                        "VALIDATION_FAILED")
                .andExpect(jsonPath("$.errors[0].field").value("description"))
                .andExpect(jsonPath("$.errors[0].code").value("Size"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("zq");
    }

    @Test
    void unknownFieldsAreRejected() throws Exception {
        expectProblem(
                        mvc.perform(post("/api/v1/test/validated")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"description\":\"Garten\",\"status\":\"CONFIRMED\"}")),
                        400,
                        "VALIDATION_FAILED")
                .andExpect(jsonPath("$.errors[0].field").value("status"));
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        expectProblem(
                mvc.perform(post("/api/v1/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":")),
                400,
                "MALFORMED_REQUEST");
    }

    @Test
    void wrongMethodIsRejected() throws Exception {
        expectProblem(mvc.perform(delete("/api/v1/test/validated")), 405, "METHOD_NOT_ALLOWED")
                .andExpect(header().exists("Allow"));
    }

    @Test
    void wrongMediaTypeIsRejected() throws Exception {
        expectProblem(
                mvc.perform(post("/api/v1/test/validated")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Garten")),
                415,
                "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void unexpectedErrorsRevealNothingAndAreLoggedWithoutPersonalData(CapturedOutput output) throws Exception {
        MvcResult result = expectProblem(mvc.perform(get("/api/v1/test/boom")), 500, "INTERNAL_ERROR")
                .andExpect(jsonPath("$.detail").value("Unexpected error."))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("anna@example.org");
        assertThat(output.getAll())
                .contains(IllegalStateException.class.getName())
                .doesNotContain("anna@example.org");
    }

    @Test
    void unauthenticatedRequestGets401ProblemWithoutRedirect() throws Exception {
        expectProblem(mvc.perform(get("/api/v1/unknown")), 401, "UNAUTHORIZED")
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void postWithoutCsrfTokenGets403Problem() throws Exception {
        expectProblem(mvc.perform(post("/api/v1/status")), 403, "CSRF_INVALID");
    }

    private ResultActions expectProblem(ResultActions actions, int status, String code) throws Exception {
        MvcResult result = actions.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.code").value(code))
                .andReturn();
        String traceId = result.getResponse().getHeader(TraceIdFilter.HEADER);
        assertThat(traceId).isNotBlank();
        return actions.andExpect(jsonPath("$.traceId").value(traceId));
    }

    @RestController
    static class ProbeController {

        record Payload(@NotBlank @Size(min = 3, max = 500) String description) {}

        @GetMapping("/api/v1/test/api-exception")
        void apiException() {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }

        @PostMapping(value = "/api/v1/test/validated", consumes = MediaType.APPLICATION_JSON_VALUE)
        Payload validated(@Valid @RequestBody Payload payload) {
            return payload;
        }

        @GetMapping("/api/v1/test/boom")
        void boom() {
            throw new IllegalStateException("Key (email)=(anna@example.org) already exists");
        }
    }

    /** Opens the probe endpoints only; all other requests go through the real API security. */
    @TestConfiguration
    static class ProbeSecurity {

        @Bean
        @Order(0)
        SecurityFilterChain probeSecurity(HttpSecurity http) throws Exception {
            http.securityMatcher("/api/v1/test/**")
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .csrf(AbstractHttpConfigurer::disable);
            return http.build();
        }
    }
}
