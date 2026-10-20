package de.elternstunden.common.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Renders errors that never reached Spring MVC (e.g. thrown in a filter) as problem responses instead of Spring
 * Boot's default error JSON.
 */
@RestController
public class ProblemErrorController implements ErrorController {

    private final ProblemDetailsFactory problems;

    public ProblemErrorController(ProblemDetailsFactory problems) {
        this.problems = problems;
    }

    @RequestMapping("${server.error.path:/error}")
    ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
        HttpStatusCode status = HttpStatusCode.valueOf(
                request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code ? code : 500);
        ErrorCode errorCode = ErrorCode.forStatus(status);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problems.create(status, errorCode, errorCode.defaultDetail()));
    }
}
