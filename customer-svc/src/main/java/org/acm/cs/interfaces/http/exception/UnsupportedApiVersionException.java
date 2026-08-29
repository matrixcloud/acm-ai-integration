package org.acm.cs.interfaces.http.exception;

public class UnsupportedApiVersionException extends RuntimeException {
  public UnsupportedApiVersionException(String message) {
    super(message);
  }
}
