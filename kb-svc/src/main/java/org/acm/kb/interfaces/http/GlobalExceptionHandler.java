package org.acm.kb.interfaces.http;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.acm.kb.domain.shared.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps business and transport exceptions to Problem Details (RFC 9457) responses.
 *
 * <p>Business exceptions carry a stable {@code code}; the mapping from code to HTTP status is
 * centralized here. Any code without an explicit mapping fails loudly instead of degrading to a
 * generic status, so adding a new code without registering it can never silently mislead clients.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(Exception.class)
  public void handleUnexpected(Exception exception, HttpServletRequest request) throws Exception {
    log.error(
        "http.error method={} path={}", request.getMethod(), request.getRequestURI(), exception);
    throw exception;
  }

  /**
   * Handles request body validation failures.
   *
   * @param exception validation failure
   * @return 400 Problem Detail with field-level errors
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
    List<Map<String, String>> errors =
        exception.getBindingResult().getFieldErrors().stream()
            .map(
                error ->
                    Map.of(
                        "field", error.getField(),
                        "message", safeMessage(error.getDefaultMessage())))
            .toList();
    ProblemDetail problem =
        problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed");
    problem.setProperty("errors", errors);
    return ResponseEntity.badRequest().body(problem);
  }

  /**
   * Handles method parameter validation failures.
   *
   * @param exception validation failure
   * @return 400 Problem Detail with field-level errors
   */
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

  /**
   * Handles malformed request payloads.
   *
   * @param exception malformed request failure
   * @return 400 Problem Detail
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleBadRequest(HttpMessageNotReadableException exception) {
    return ResponseEntity.badRequest()
        .body(problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request"));
  }

  /**
   * Handles business rule violations. The exception's stable {@code code} drives the HTTP status
   * via {@link #httpStatusFor(String)}.
   *
   * @param exception business rule violation
   * @return Problem Detail with the appropriate HTTP status
   */
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ProblemDetail> handleBusiness(BusinessException exception) {
    HttpStatus status = httpStatusFor(exception.code());
    return ResponseEntity.status(status)
        .body(problem(status, exception.code(), safeMessage(exception.getMessage())));
  }

  private static HttpStatus httpStatusFor(String code) {
    return switch (code) {
      case "INVALID_REQUEST" -> HttpStatus.BAD_REQUEST;
      case "KB_NOT_FOUND", "DOCUMENT_NOT_FOUND", "EVAL_SUITE_NOT_FOUND", "EVAL_RUN_NOT_FOUND" ->
          HttpStatus.NOT_FOUND;
      case "KB_NOT_ACTIVE" -> HttpStatus.CONFLICT;
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
