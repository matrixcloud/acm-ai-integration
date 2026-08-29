package org.acm.cs.domain.shared;

public class InvalidRequestException extends BusinessException {
  public InvalidRequestException(String message) {
    super("INVALID_REQUEST", message);
  }
}
