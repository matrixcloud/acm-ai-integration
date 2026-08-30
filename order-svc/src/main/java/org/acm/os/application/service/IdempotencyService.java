package org.acm.os.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.acm.os.application.exception.IdempotencyKeyReuseException;
import org.acm.os.application.exception.PersistedRetryableFailureException;
import org.acm.os.application.exception.ReservedByConcurrentWriterException;
import org.acm.os.application.exception.RetryableOperationException;
import org.acm.os.application.idempotency.IdempotencyRecord;
import org.acm.os.application.idempotency.IdempotencyRecordRepository;
import org.acm.os.domain.shared.InvalidRequestException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Idempotency guard for command use cases (design §8.1, §9, §12.3).
 *
 * <p>Owns the full check → reserve → execute → complete protocol. Executes {@code action} inside a
 * single database transaction together with the guard record: a failed or crashed attempt rolls
 * back completely, leaving the key free for retry.
 *
 * <p>Replay semantics:
 *
 * <ul>
 *   <li>same key + same request → returns the stored result of the completed attempt
 *   <li>same key + different request → {@link IdempotencyKeyReuseException}
 *   <li>key held by a concurrent writer → {@link ReservedByConcurrentWriterException}
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {
  /**
   * Matches the {@code idempotency_records.idempotency_key} column width; longer keys would
   * otherwise fail at insert with a raw 500.
   */
  private static final int MAX_KEY_LENGTH = 128;

  private final IdempotencyRecordRepository repository;
  private final ObjectMapper objectMapper;

  /**
   * Description of one idempotent command invocation.
   *
   * @param responseType the result type; the cached payload is serialized/deserialized as this type
   *     on replay. Decoupled from transport DTOs — callers cache domain or application result
   *     types, never HTTP responses.
   */
  record IdempotentOperation<R>(
      String operation, String idempotencyKey, Object request, Class<R> responseType) {}

  @Transactional
  public <R> R execute(IdempotentOperation<R> operation, Supplier<R> action) {
    ReservedOperation<R> reserved = reserve(operation);
    if (reserved.replayedResult() != null) {
      return reserved.replayedResult();
    }
    R result = action.get();
    reserved.record().complete(toJson(result), null);
    return result;
  }

  @Transactional(noRollbackFor = RetryableOperationException.class)
  public <R> R executeRetryable(IdempotentOperation<R> operation, Supplier<R> action) {
    ReservedOperation<R> reserved = reserve(operation);
    if (reserved.replayedResult() != null) {
      return reserved.replayedResult();
    }
    try {
      R result = action.get();
      reserved.record().complete(toJson(result), null);
      return result;
    } catch (PersistedRetryableFailureException exception) {
      repository.delete(reserved.record());
      repository.flush();
      throw new RetryableOperationException(exception.original());
    }
  }

  private <R> ReservedOperation<R> reserve(IdempotentOperation<R> operation) {
    if (operation.idempotencyKey() == null || operation.idempotencyKey().isBlank()) {
      throw new InvalidRequestException("Idempotency key must not be blank");
    }
    if (operation.idempotencyKey().length() > MAX_KEY_LENGTH) {
      throw new InvalidRequestException(
          "Idempotency key exceeds %d characters".formatted(MAX_KEY_LENGTH));
    }
    String requestHash = hashRequest(toJson(canonicalize(operation.request())));

    Optional<IdempotencyRecord> existing =
        repository.findByOperationAndIdempotencyKey(
            operation.operation(), operation.idempotencyKey());
    if (existing.isPresent()) {
      return new ReservedOperation<>(
          null, replay(existing.get(), requestHash, operation.responseType()));
    }

    IdempotencyRecord record =
        new IdempotencyRecord(
            null,
            operation.operation(),
            operation.idempotencyKey(),
            requestHash,
            null,
            null,
            IdempotencyRecord.STATUS_PENDING);
    try {
      repository.saveAndFlush(record);
    } catch (DataIntegrityViolationException e) {
      // Lost the race against a concurrent writer. No re-query here: the failed insert may have
      // poisoned the persistence context; the caller's retry will replay via the pre-check above.
      throw new ReservedByConcurrentWriterException(
          "Idempotency key '%s' is being processed by a concurrent writer"
              .formatted(operation.idempotencyKey()),
          e);
    }

    return new ReservedOperation<>(record, null);
  }

  private record ReservedOperation<R>(IdempotencyRecord record, R replayedResult) {}

  private <R> R replay(IdempotencyRecord record, String requestHash, Class<R> responseType) {
    if (!record.getRequestHash().equals(requestHash)) {
      throw new IdempotencyKeyReuseException(
          "Idempotency key '%s' was already used with a different request body"
              .formatted(record.getIdempotencyKey()));
    }
    if (!IdempotencyRecord.STATUS_COMPLETED.equals(record.getStatus())) {
      throw new ReservedByConcurrentWriterException(
          "Idempotency key '%s' is being processed by a concurrent writer"
              .formatted(record.getIdempotencyKey()));
    }
    return fromJson(record, responseType);
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JacksonException e) {
      throw new IllegalStateException(
          "Failed to serialize idempotency payload of type '%s': %s"
              .formatted(value.getClass().getName(), e.getMessage()),
          e);
    }
  }

  private static Object canonicalize(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> sorted = new TreeMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (!(entry.getKey() instanceof String key)) {
          throw new IllegalStateException("Idempotency request maps must use string keys");
        }
        sorted.put(key, canonicalize(entry.getValue()));
      }
      return sorted;
    }
    if (value instanceof List<?> list) {
      return list.stream().map(IdempotencyService::canonicalize).toList();
    }
    return value;
  }

  /**
   * Deserializes a cached response body.
   *
   * <p>The cached JSON is persisted and replayed long after it was written, so a mismatch between
   * the stored wire format and the current mapper configuration is an operational failure, not a
   * caller error. The failure therefore names the record and target type explicitly instead of
   * surfacing as an anonymous 500.
   */
  private <R> R fromJson(IdempotencyRecord record, Class<R> type) {
    try {
      return objectMapper.readValue(record.getResponseBody(), type);
    } catch (JacksonException e) {
      throw new IllegalStateException(
          ("Failed to deserialize cached idempotency response: id=%s, operation='%s', key='%s', "
                  + "targetType=%s. The stored payload no longer matches the current JSON "
                  + "configuration — this record cannot be replayed.")
              .formatted(
                  record.getId(),
                  record.getOperation(),
                  record.getIdempotencyKey(),
                  type.getName()),
          e);
    }
  }

  /** SHA-256 hex digest of the request payload, used to detect payload drift on key reuse. */
  private static String hashRequest(String requestBody) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(requestBody.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is mandated by the JLS platform; unreachable.
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
