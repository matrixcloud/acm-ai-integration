package org.acm.ca.interfaces.http;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.acm.ca.domain.shared.BusinessException;
import org.acm.ca.interfaces.http.exception.UnsupportedApiVersionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps business and transport exceptions to Problem Details (RFC 9457). Any business code without
 * an explicit HTTP mapping fails loudly so an unregistered code can never silently mislead clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
    List<Map<String, String>> errors =
        exception.getBindingResult().getFieldErrors().stream()
            .map(
                error ->
                    Map.of(
                        "field", error.getField(), "message", safeMessage(error.getDefaultMessage())))
            .toList();
    ProblemDetail problem =
        problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed");
    problem.setProperty("errors", errors);
    return ResponseEntity.badRequest().body(problem);
  }

  @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
  public ResponseEntity<ProblemDetail> handleValidation(
      jakarta.validation.ConstraintViolationException exception) {
    List<Map<String, String>> errors =
        exception.getConstraintViolations().stream()
            .map(GlobalExceptionHandler::toError)
            .sorted(
                Comparator.comparing((Map<String, String> left) -> left.get("field"))
                    .thenComparing(left -> left.get("message")))
            .toList();
    ProblemDetail problem =
        problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed");
    problem.setProperty("errors", errors);
    return ResponseEntity.badRequest().body(problem);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleBadRequest(HttpMessageNotReadableException exception) {
    return ResponseEntity.badRequest()
        .body(problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request"));
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<ProblemDetail> handleMissingHeader(MissingRequestHeaderException exception) {
    String detail = "Required header '%s' is missing".formatted(exception.getHeaderName());
    return ResponseEntity.badRequest()
        .body(problem(HttpStatus.BAD_REQUEST, "MISSING_REQUEST_HEADER", detail));
  }

  @ExceptionHandler(UnsupportedApiVersionException.class)
  public ResponseEntity<ProblemDetail> handleUnsupportedApiVersion(
      UnsupportedApiVersionException exception) {
    return ResponseEntity.badRequest()
        .body(
            problem(
                HttpStatus.BAD_REQUEST, "UNSUPPORTED_API_VERSION", safeMessage(exception.getMessage())));
  }

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ProblemDetail> handleBusiness(BusinessException exception) {
    HttpStatus status = httpStatusFor(exception.code());
    return ResponseEntity.status(status)
        .body(problem(status, exception.code(), safeMessage(exception.getMessage())));
  }

  private static HttpStatus httpStatusFor(String code) {
    return switch (code) {
      case "INVALID_REQUEST" -> HttpStatus.BAD_REQUEST;
      case "CONVERSATION_NOT_FOUND" -> HttpStatus.NOT_FOUND;
      case "CONVERSATION_NOT_ACTIVE",
          "CONVERSATION_STATE_CONFLICT",
          "FEEDBACK_ALREADY_SUBMITTED",
          "IDEMPOTENCY_KEY_REUSED",
          "CONVERSATION_CONCURRENTLY_MODIFIED" -> HttpStatus.CONFLICT;
      case "LLM_UNAVAILABLE", "EXTERNAL_DEPENDENCY_FAILED" -> HttpStatus.BAD_GATEWAY;
      default ->
          throw new IllegalStateException(
              "Business error code '%s' has no HTTP status mapping".formatted(code));
    };
  }

  private static ProblemDetail problem(HttpStatus status, String code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(status.getReasonPhrase());
    problem.setProperty("code", code);
    return problem;
  }

  private static String safeMessage(String message) {
    return message == null || message.isBlank() ? "Invalid request" : message;
  }

  private static Map<String, String> toError(jakarta.validation.ConstraintViolation<?> violation) {
    String path = violation.getPropertyPath().toString();
    String field =
        Stream.of(path.split("\\."))
            .filter(segment -> !segment.startsWith("<"))
            .reduce((first, second) -> second)
            .orElse(path);
    return Map.of("field", field, "message", safeMessage(violation.getMessage()));
  }
}
