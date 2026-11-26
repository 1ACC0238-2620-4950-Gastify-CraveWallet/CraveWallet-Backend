package pe.edu.upc.gastify.cravewallet.shared.interfaces.rest;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pe.edu.upc.gastify.cravewallet.delivery.application.DeliveryFailure;
import pe.edu.upc.gastify.cravewallet.iam.application.AuthFailure;
import pe.edu.upc.gastify.cravewallet.premium.application.PremiumFailure;
import pe.edu.upc.gastify.cravewallet.subscriptions.application.SubscriptionFailure;

@RestControllerAdvice
public class ApiExceptionHandler {
    private final MessageSource messageSource;

    public ApiExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(PremiumFailure.class)
    ProblemDetail premiumFailure(PremiumFailure failure) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(failure.status()),
                resolve(failure.messageKey(), failure.getMessage()));
    }

    @ExceptionHandler(DeliveryFailure.class)
    ProblemDetail deliveryFailure(DeliveryFailure failure) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(failure.status()),
                resolve(failure.messageKey(), failure.getMessage()));
    }

    @ExceptionHandler(SubscriptionFailure.class)
    ProblemDetail subscriptionFailure(SubscriptionFailure failure) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(failure.status()),
                resolve(failure.messageKey(), failure.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidValue() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                resolve("error.request.invalid_data", "Invalid request data."));
    }

    @ExceptionHandler(AuthFailure.class)
    ProblemDetail authenticationFailure(AuthFailure failure) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(failure.status()),
                resolve(failure.messageKey(), failure.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidRequest(MethodArgumentNotValidException failure) {
        var locale = LocaleContextHolder.getLocale();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                resolve("error.request.validation", "Check the request fields."));
        problem.setProperty("errors", failure.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), messageSource.getMessage(error, locale))).toList());
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail invalidJson() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                resolve("error.request.invalid_json", "Invalid JSON body."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflictingData() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                resolve("error.data.conflict", "The record conflicts with existing data."));
    }

    private String resolve(String key, String defaultMessage) {
        return messageSource.getMessage(key, null, defaultMessage, LocaleContextHolder.getLocale());
    }

    private record FieldError(String field, String message) { }
}
