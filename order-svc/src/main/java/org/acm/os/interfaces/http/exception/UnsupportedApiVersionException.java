package org.acm.os.interfaces.http.exception;

/**
 * Thrown when a request carries an unsupported or missing {@code API-Version} header.
 */
public class UnsupportedApiVersionException extends RuntimeException {

  /**
   * Constructs the exception.
   *
   * @param message detail message naming the unsupported version
   */
  public UnsupportedApiVersionException(String message) {
    super(message);
  }
}
