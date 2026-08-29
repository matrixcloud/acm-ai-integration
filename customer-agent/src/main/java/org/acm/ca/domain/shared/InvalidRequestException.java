package org.acm.ca.domain.shared;

/** Thrown for semantically invalid input that passes structural validation but violates a business rule. */
public class InvalidRequestException extends BusinessException {
  public InvalidRequestException(String message) {
    super("INVALID_REQUEST", message);
  }
}