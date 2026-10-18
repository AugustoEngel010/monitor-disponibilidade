package br.com.monitor.query;

import br.com.monitor.administration.MonitorErrors;
import br.com.monitor.administration.MonitorProblem;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = QueryController.class)
public class QueryErrors {
    @ExceptionHandler(MonitorProblem.class)
    public ResponseEntity<MonitorErrors.Error> problem(MonitorProblem problem) {
        return ResponseEntity.status(problem.status()).body(new MonitorErrors.Error(problem.code(), problem.errors()));
    }
}
