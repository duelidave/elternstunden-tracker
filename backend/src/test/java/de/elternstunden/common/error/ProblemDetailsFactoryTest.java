package de.elternstunden.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import de.elternstunden.api.model.ValidationErrorDto;
import de.elternstunden.common.web.TraceIdFilter;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class ProblemDetailsFactoryTest {

    private final ProblemDetailsFactory factory = new ProblemDetailsFactory();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void createsProblemInContractFormat() {
        MDC.put(TraceIdFilter.MDC_KEY, "abc123");

        ProblemDetail problem = factory.create(ErrorCode.NOT_FOUND);

        assertThat(problem.getType()).isEqualTo(URI.create("about:blank"));
        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getTitle()).isEqualTo("Not Found");
        assertThat(problem.getDetail()).isEqualTo("Resource not found.");
        assertThat(problem.getProperties()).containsEntry("code", "NOT_FOUND").containsEntry("traceId", "abc123");
    }

    @Test
    void validationProblemCarriesErrors() {
        ValidationErrorDto error = new ValidationErrorDto("description", "Size", "size must be between 3 and 500");

        ProblemDetail problem = factory.validation(List.of(error));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getProperties())
                .containsEntry("code", "VALIDATION_FAILED")
                .containsEntry("errors", List.of(error));
    }

    @Test
    void recognisesOwnProblems() {
        assertThat(ProblemDetailsFactory.isOwn(factory.create(ErrorCode.FORBIDDEN)))
                .isTrue();
        assertThat(ProblemDetailsFactory.isOwn(ProblemDetail.forStatus(HttpStatus.BAD_REQUEST)))
                .isFalse();
        assertThat(ProblemDetailsFactory.isOwn(null)).isFalse();
    }

    @Test
    void mapsStatusToFallbackCode() {
        assertThat(ErrorCode.forStatus(HttpStatus.NOT_FOUND)).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(ErrorCode.forStatus(HttpStatus.CONFLICT)).isEqualTo(ErrorCode.REQUEST_REJECTED);
        assertThat(ErrorCode.forStatus(HttpStatus.SERVICE_UNAVAILABLE)).isEqualTo(ErrorCode.INTERNAL_ERROR);
    }
}
