package org.acm.os.interfaces.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.acm.os.application.exception.IdempotencyKeyReuseException;
import org.acm.os.application.port.out.InsufficientInventoryException;
import org.acm.os.application.port.out.ProductNotFoundException;
import org.acm.os.domain.shared.BusinessException;
import org.acm.os.domain.shared.InvalidRequestException;
import org.acm.os.interfaces.http.exception.UnsupportedApiVersionException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void mapsBodyValidationErrorsWithSafeMessage() {
    MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
    BindingResult bindingResult = mock(BindingResult.class);
    when(exception.getBindingResult()).thenReturn(bindingResult);
    when(bindingResult.getFieldErrors())
        .thenReturn(
            List.of(
                new FieldError("request", "customerId", null, false, null, null, null)));

    ResponseEntity<ProblemDetail> response = handler.handleValidation(exception);

    assertProblem(response, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
    assertThat(response.getBody().getProperties().get("errors"))
        .isEqualTo(List.of(Map.of("field", "customerId", "message", "Invalid request")));
  }

  @Test
  void mapsAndSortsConstraintViolationsByLeafField() {
    ConstraintViolation<Object> second = violation("search.<list element>.size", "must be valid");
    ConstraintViolation<Object> first = violation("search.customerId", "must not be blank");

    ResponseEntity<ProblemDetail> response =
        handler.handleValidation(new ConstraintViolationException(Set.of(second, first)));

    assertProblem(response, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
    assertThat(response.getBody().getProperties().get("errors"))
        .isEqualTo(
            List.of(
                Map.of("field", "customerId", "message", "must not be blank"),
                Map.of("field", "size", "message", "must be valid")));
  }

  @Test
  void mapsTransportFailures() {
    ResponseEntity<ProblemDetail> malformed =
        handler.handleBadRequest(mock(HttpMessageNotReadableException.class));
    MissingRequestHeaderException missingException = mock(MissingRequestHeaderException.class);
    when(missingException.getHeaderName()).thenReturn("Idempotency-Key");
    ResponseEntity<ProblemDetail> missing = handler.handleMissingHeader(missingException);
    ResponseEntity<ProblemDetail> unsupported =
        handler.handleUnsupportedApiVersion(new UnsupportedApiVersionException(" "));

    assertProblem(malformed, HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    assertThat(malformed.getBody().getDetail()).isEqualTo("Invalid request");
    assertProblem(missing, HttpStatus.BAD_REQUEST, "MISSING_REQUEST_HEADER");
    assertThat(missing.getBody().getDetail())
        .isEqualTo("Required header 'Idempotency-Key' is missing");
    assertProblem(unsupported, HttpStatus.BAD_REQUEST, "UNSUPPORTED_API_VERSION");
    assertThat(unsupported.getBody().getDetail()).isEqualTo("Invalid request");
  }

  @Test
  void mapsRegisteredBusinessCodes() {
    assertProblem(
        handler.handleBusiness(new InvalidRequestException("invalid")),
        HttpStatus.BAD_REQUEST,
        "INVALID_REQUEST");
    assertProblem(
        handler.handleBusiness(new ProductNotFoundException("missing")),
        HttpStatus.NOT_FOUND,
        "PRODUCT_NOT_AVAILABLE");
    assertProblem(
        handler.handleBusiness(new InsufficientInventoryException("insufficient")),
        HttpStatus.CONFLICT,
        "INSUFFICIENT_INVENTORY");
    assertProblem(
        handler.handleBusiness(new IdempotencyKeyReuseException("reused")),
        HttpStatus.CONFLICT,
        "IDEMPOTENCY_KEY_REUSED");
    assertProblem(
        handler.handleBusiness(businessException("EXTERNAL_DEPENDENCY_FAILED")),
        HttpStatus.BAD_GATEWAY,
        "EXTERNAL_DEPENDENCY_FAILED");
  }

  @Test
  void rejectsUnregisteredBusinessCode() {
    assertThatThrownBy(() -> handler.handleBusiness(businessException("UNREGISTERED")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Business error code 'UNREGISTERED' has no HTTP status mapping");
  }

  private static ConstraintViolation<Object> violation(String pathValue, String message) {
    @SuppressWarnings("unchecked")
    ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
    Path path = mock(Path.class);
    when(path.toString()).thenReturn(pathValue);
    when(violation.getPropertyPath()).thenReturn(path);
    when(violation.getMessage()).thenReturn(message);
    return violation;
  }

  private static BusinessException businessException(String code) {
    return new BusinessException(code, "failure") {};
  }

  private static void assertProblem(
      ResponseEntity<ProblemDetail> response, HttpStatus status, String code) {
    assertThat(response.getStatusCode()).isEqualTo(status);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getProperties()).containsEntry("code", code);
  }
}
