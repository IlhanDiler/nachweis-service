package de.diler.nachweis.credential;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Wandelt fachliche Fehler in einheitliche Antworten nach RFC 9457 (ProblemDetail) um.
 */
@RestControllerAdvice
public class CredentialExceptionHandler {

    @ExceptionHandler(CredentialNotFoundException.class)
    public ProblemDetail handleNotFound(CredentialNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Nachweis nicht gefunden", e.getMessage());
    }

    @ExceptionHandler(CredentialAlreadyRevokedException.class)
    public ProblemDetail handleAlreadyRevoked(CredentialAlreadyRevokedException e) {
        return problem(HttpStatus.CONFLICT, "Nachweis bereits widerrufen", e.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleConcurrentUpdate(OptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "Gleichzeitige Aenderung",
                "Der Nachweis wurde parallel geaendert. Bitte erneut versuchen.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
