package org.acm.os.interfaces.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.acm.os.interfaces.http.exception.UnsupportedApiVersionException;
import org.junit.jupiter.api.Test;

class ApiVersionInterceptorTest {
  private final ApiVersionInterceptor interceptor = new ApiVersionInterceptor();
  private final HttpServletRequest request = mock(HttpServletRequest.class);

  @Test
  void acceptsSupportedVersion() {
    when(request.getHeader("API-Version")).thenReturn("1");
    assertThat(interceptor.preHandle(request, mock(HttpServletResponse.class), new Object()))
        .isTrue();
  }

  @Test
  void rejectsMissingAndUnsupportedVersions() {
    assertThatThrownBy(
            () -> interceptor.preHandle(request, mock(HttpServletResponse.class), new Object()))
        .isInstanceOf(UnsupportedApiVersionException.class)
        .hasMessage("Required header 'API-Version' is missing");

    when(request.getHeader("API-Version")).thenReturn("2");
    assertThatThrownBy(
            () -> interceptor.preHandle(request, mock(HttpServletResponse.class), new Object()))
        .isInstanceOf(UnsupportedApiVersionException.class)
        .hasMessage("Unsupported API version '2'");
  }
}
