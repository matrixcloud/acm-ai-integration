package org.acm.os.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.acm.os.application.exception.IdempotencyKeyReuseException;
import org.acm.os.application.exception.ReservedByConcurrentWriterException;
import org.acm.os.application.exception.RetryableOperationException;
import org.acm.os.application.exception.PersistedRetryableFailureException;
import org.acm.os.application.idempotency.IdempotencyRecord;
import org.acm.os.application.idempotency.IdempotencyRecordRepository;
import org.acm.os.domain.shared.InvalidRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

  private static final String OPERATION = "create-order";
  private static final String KEY = "key-1";
  private static final Object REQUEST = new Object();
  private static final String REQUEST_JSON = "{\"request\":1}";

  @Mock private IdempotencyRecordRepository repository;
  @Mock private ObjectMapper objectMapper;
  @Mock private Supplier<String> action;

  private IdempotencyService service;

  @BeforeEach
  void setUp() {
    service = new IdempotencyService(repository, objectMapper);
  }

  @Test
  void executeRejectsBlankAndOversizedKeysBeforeSerialization() {
    assertThatThrownBy(() -> service.execute(operation(" "), action))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Idempotency key must not be blank");
    assertThatThrownBy(() -> service.execute(operation("k".repeat(129)), action))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Idempotency key exceeds 128 characters");

    verifyNoInteractions(repository, objectMapper, action);
  }

  @Test
  void executeReservesRunsAndCompletesNewOperation() {
    when(objectMapper.writeValueAsString(REQUEST)).thenReturn(REQUEST_JSON);
    when(repository.findByOperationAndIdempotencyKey(OPERATION, KEY)).thenReturn(Optional.empty());
    when(action.get()).thenReturn("created");
    when(objectMapper.writeValueAsString("created")).thenReturn("\"created\"");
    ArgumentCaptor<IdempotencyRecord> recordCaptor =
        ArgumentCaptor.forClass(IdempotencyRecord.class);

    String result = service.execute(operation(KEY), action);

    assertThat(result).isEqualTo("created");
    verify(repository).saveAndFlush(recordCaptor.capture());
    IdempotencyRecord record = recordCaptor.getValue();
    assertThat(record.getOperation()).isEqualTo(OPERATION);
    assertThat(record.getIdempotencyKey()).isEqualTo(KEY);
    assertThat(record.getRequestHash()).isEqualTo(sha256(REQUEST_JSON));
    assertThat(record.getStatus()).isEqualTo(IdempotencyRecord.STATUS_COMPLETED);
    assertThat(record.getResponseBody()).isEqualTo("\"created\"");
  }

  @Test
  void executeReplaysCompletedOperationWithoutRunningAction() {
    IdempotencyRecord existing = completedRecord(sha256(REQUEST_JSON));
    when(objectMapper.writeValueAsString(REQUEST)).thenReturn(REQUEST_JSON);
    when(repository.findByOperationAndIdempotencyKey(OPERATION, KEY))
        .thenReturn(Optional.of(existing));
    when(objectMapper.readValue("\"created\"", String.class)).thenReturn("created");

    assertThat(service.execute(operation(KEY), action)).isEqualTo("created");

    verify(action, never()).get();
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void executeRejectsKeyReusedForDifferentRequest() {
    when(objectMapper.writeValueAsString(REQUEST)).thenReturn(REQUEST_JSON);
    when(repository.findByOperationAndIdempotencyKey(OPERATION, KEY))
        .thenReturn(Optional.of(completedRecord(sha256("different"))));

    assertThatThrownBy(() -> service.execute(operation(KEY), action))
        .isInstanceOf(IdempotencyKeyReuseException.class)
        .hasMessage("Idempotency key 'key-1' was already used with a different request body");

    verify(action, never()).get();
  }

  @Test
  void executeRejectsPendingOperation() {
    IdempotencyRecord pending =
        new IdempotencyRecord(
            1L,
            OPERATION,
            KEY,
            sha256(REQUEST_JSON),
            null,
            null,
            IdempotencyRecord.STATUS_PENDING);
    when(objectMapper.writeValueAsString(REQUEST)).thenReturn(REQUEST_JSON);
    when(repository.findByOperationAndIdempotencyKey(OPERATION, KEY))
        .thenReturn(Optional.of(pending));

    assertThatThrownBy(() -> service.execute(operation(KEY), action))
        .isInstanceOf(ReservedByConcurrentWriterException.class)
        .hasMessage("Idempotency key 'key-1' is being processed by a concurrent writer");

    verify(action, never()).get();
  }

  @Test
  void executeTranslatesUniqueKeyRace() {
    DataIntegrityViolationException conflict = new DataIntegrityViolationException("duplicate");
    when(objectMapper.writeValueAsString(REQUEST)).thenReturn(REQUEST_JSON);
    when(repository.findByOperationAndIdempotencyKey(OPERATION, KEY)).thenReturn(Optional.empty());
    when(repository.saveAndFlush(any())).thenThrow(conflict);

    assertThatThrownBy(() -> service.execute(operation(KEY), action))
        .isInstanceOf(ReservedByConcurrentWriterException.class)
        .hasCause(conflict);

    verify(action, never()).get();
  }

  @Test
  void executeNamesRequestTypeWhenSerializationFails() {
    JacksonException failure = jacksonFailure("serialize");
    when(objectMapper.writeValueAsString(REQUEST)).thenThrow(failure);

    assertThatThrownBy(() -> service.execute(operation(KEY), action))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to serialize idempotency payload of type 'java.lang.Object'")
        .hasCause(failure);
  }

  @Test
  void executeNamesRecordWhenReplayDeserializationFails() {
    IdempotencyRecord existing = completedRecord(sha256(REQUEST_JSON));
    JacksonException failure = jacksonFailure("deserialize");
    when(objectMapper.writeValueAsString(REQUEST)).thenReturn(REQUEST_JSON);
    when(repository.findByOperationAndIdempotencyKey(OPERATION, KEY))
        .thenReturn(Optional.of(existing));
    when(objectMapper.readValue("\"created\"", String.class)).thenThrow(failure);

    assertThatThrownBy(() -> service.execute(operation(KEY), action))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("id=1, operation='create-order', key='key-1'")
        .hasMessageContaining("targetType=java.lang.String")
        .hasCause(failure);
  }

  @Test
  void retryableFailureDeletesReservationAndPreservesOriginalCause() {
    RuntimeException failure = new RuntimeException("external failed");
    when(objectMapper.writeValueAsString(REQUEST)).thenReturn(REQUEST_JSON);
    when(repository.findByOperationAndIdempotencyKey(OPERATION, KEY)).thenReturn(Optional.empty());
    when(action.get()).thenThrow(new PersistedRetryableFailureException(failure));
    ArgumentCaptor<IdempotencyRecord> recordCaptor =
        ArgumentCaptor.forClass(IdempotencyRecord.class);

    assertThatThrownBy(() -> service.executeRetryable(operation(KEY), action))
        .isInstanceOf(RetryableOperationException.class)
        .hasCause(failure);

    verify(repository).saveAndFlush(recordCaptor.capture());
    verify(repository).delete(recordCaptor.getValue());
    verify(repository).flush();
  }

  @Test
  void retryableOperationDoesNotCommitOnResponseSerializationFailure() {
    JacksonException failure = jacksonFailure("serialize response");
    when(objectMapper.writeValueAsString(REQUEST)).thenReturn(REQUEST_JSON);
    when(repository.findByOperationAndIdempotencyKey(OPERATION, KEY)).thenReturn(Optional.empty());
    when(action.get()).thenReturn("created");
    when(objectMapper.writeValueAsString("created")).thenThrow(failure);

    assertThatThrownBy(() -> service.executeRetryable(operation(KEY), action))
        .isInstanceOf(IllegalStateException.class)
        .hasCause(failure);

    verify(repository, never()).delete(any());
    verify(repository, never()).flush();
  }

  @Test
  void requestHashCanonicalizesMapKeyOrder() {
    IdempotencyService canonicalService = new IdempotencyService(repository, new ObjectMapper());
    when(repository.findByOperationAndIdempotencyKey(OPERATION, "first"))
        .thenReturn(Optional.empty());
    when(repository.findByOperationAndIdempotencyKey(OPERATION, "second"))
        .thenReturn(Optional.empty());
    Map<String, Object> firstRequest = new LinkedHashMap<>();
    firstRequest.put("skuId", "SKU-1");
    firstRequest.put("quantity", 2);
    Map<String, Object> secondRequest = new LinkedHashMap<>();
    secondRequest.put("quantity", 2);
    secondRequest.put("skuId", "SKU-1");
    ArgumentCaptor<IdempotencyRecord> records =
        ArgumentCaptor.forClass(IdempotencyRecord.class);

    canonicalService.execute(
        new IdempotencyService.IdempotentOperation<>(
            OPERATION, "first", firstRequest, String.class),
        () -> "ok");
    canonicalService.execute(
        new IdempotencyService.IdempotentOperation<>(
            OPERATION, "second", secondRequest, String.class),
        () -> "ok");

    verify(repository, org.mockito.Mockito.times(2)).saveAndFlush(records.capture());
    assertThat(records.getAllValues().get(0).getRequestHash())
        .isEqualTo(records.getAllValues().get(1).getRequestHash());
  }

  private static IdempotencyService.IdempotentOperation<String> operation(String key) {
    return new IdempotencyService.IdempotentOperation<>(OPERATION, key, REQUEST, String.class);
  }

  private static IdempotencyRecord completedRecord(String requestHash) {
    return new IdempotencyRecord(
        1L,
        OPERATION,
        KEY,
        requestHash,
        "\"created\"",
        null,
        IdempotencyRecord.STATUS_COMPLETED);
  }

  private static String sha256(String value) {
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder(hash.length * 2);
      for (byte current : hash) {
        result.append(String.format("%02x", current));
      }
      return result.toString();
    } catch (Exception exception) {
      throw new AssertionError(exception);
    }
  }

  private static JacksonException jacksonFailure(String message) {
    return new JacksonException(message) {};
  }
}
