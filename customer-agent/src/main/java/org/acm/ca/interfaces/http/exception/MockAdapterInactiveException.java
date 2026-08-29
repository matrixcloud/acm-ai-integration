package org.acm.ca.interfaces.http.exception;

import org.acm.ca.domain.shared.BusinessException;

/** Thrown when a mock-only endpoint is called while the mock adapter is not the active one. */
public class MockAdapterInactiveException extends BusinessException {

  private static final String CODE = "MOCK_ADAPTER_INACTIVE";

  public MockAdapterInactiveException(String message) {
    super(CODE, message);
  }
}
