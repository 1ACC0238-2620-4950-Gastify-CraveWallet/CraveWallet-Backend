package pe.edu.upc.gastify.cravewallet.shared.interfaces.rest;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pe.edu.upc.gastify.cravewallet.iam.application.AuthFailure;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(pe.edu.upc.gastify.cravewallet.subscriptions.application.SubscriptionFailure.class)
    ProblemDetail subscriptionFailure(pe.edu.upc.gastify.cravewallet.subscriptions.application.SubscriptionFailure failure) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(failure.status()), failure.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidValue() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Datos de la solicitud no válidos.");
    }
    @ExceptionHandler(AuthFailure.class)
    ProblemDetail authenticationFailure(AuthFailure failure) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(failure.status()), failure.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidRequest(MethodArgumentNotValidException failure) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Revisa los campos de la solicitud.");
        problem.setProperty("errors", failure.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.getDefaultMessage())).toList());
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail invalidJson() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "El cuerpo JSON no es válido.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflictingData() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "El registro entra en conflicto con datos existentes.");
    }

    private record FieldError(String field, String message) { }
}
