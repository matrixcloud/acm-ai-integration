package org.acm.cs.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.acm.cs.application.exception.IdempotencyKeyReuseException;
import org.acm.cs.application.exception.ReservedByConcurrentWriterException;
import org.acm.cs.application.idempotency.IdempotencyRecord;
import org.acm.cs.application.idempotency.IdempotencyRecordRepository;
import org.acm.cs.domain.shared.InvalidRequestException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class IdempotencyService {
  private static final int MAX_KEY_LENGTH = 128;

  private final IdempotencyRecordRepository repository;
  private final ObjectMapper objectMapper;

  record IdempotentOperation<R>(
      String operation, String idempotencyKey, Object request, Class<R> responseType) {}

  @Transactional
  public <R> R execute(IdempotentOperation<R> operation, Supplier<R> action) {
    if (operation.idempotencyKey() == null || operation.idempotencyKey().isBlank()) {
      throw new InvalidRequestException("Idempotency key must not be blank");
    }
    if (operation.idempotencyKey().length() > MAX_KEY_LENGTH) {
      throw new InvalidRequestException(
          "Idempotency key exceeds %d characters".formatted(MAX_KEY_LENGTH));
    }
    String requestHash = hashRequest(toJson(operation.request()));

    Optional<IdempotencyRecord> existing =
        repository.findByOperationAndIdempotencyKey(
            operation.operation(), operation.idempotencyKey());
    if (existing.isPresent()) {
      return replay(existing.get(), requestHash, operation.responseType());
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
      throw new ReservedByConcurrentWriterException(
          "Idempotency key '%s' is being processed by a concurrent writer"
              .formatted(operation.idempotencyKey()),
          e);
    }

    R result = action.get();
    record.complete(toJson(result), null);
    return result;
  }

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

  private <R> R fromJson(IdempotencyRecord record, Class<R> type) {
    try {
      return objectMapper.readValue(record.getResponseBody(), type);
    } catch (JacksonException e) {
      throw new IllegalStateException(
          "Failed to deserialize cached idempotency response: id=%s, operation='%s', key='%s', "
              + "targetType=%s. The stored payload no longer matches the current JSON "
              + "configuration — this record cannot be replayed."
              .formatted(
                  record.getId(),
                  record.getOperation(),
                  record.getIdempotencyKey(),
                  type.getName()),
          e);
    }
  }

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
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
