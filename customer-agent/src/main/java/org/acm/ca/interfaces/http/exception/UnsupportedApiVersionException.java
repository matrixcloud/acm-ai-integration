package org.acm.ca.interfaces.http.exception;

/** Thrown when a request carries an unsupported or missing {@code API-Version} header. */
public class UnsupportedApiVersionException extends RuntimeException {

  public UnsupportedApiVersionException(String message) {
    super(message);
  }
}