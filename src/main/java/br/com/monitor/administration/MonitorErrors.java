package br.com.monitor.administration;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = MonitorController.class)
public class MonitorErrors {
    @ExceptionHandler(MonitorProblem.class)
    public ResponseEntity<Error> problem(MonitorProblem problem) {
        return ResponseEntity.status(problem.status()).body(new Error(problem.code(), problem.errors()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Error> malformedBody() {
        return problem(MonitorProblem.invalid(Map.of("body", "must be a JSON object")));
    }

    public record Error(String code, Map<String, String> errors) { }
}
