package org.acm.os.interfaces.http;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.acm.os.interfaces.http.exception.UnsupportedApiVersionException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ApiVersionInterceptor implements HandlerInterceptor {
  static final String SUPPORTED_VERSION = "1";

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    String version = request.getHeader("API-Version");
    if (!SUPPORTED_VERSION.equals(version)) {
      throw new UnsupportedApiVersionException(
          version == null
              ? "Required header 'API-Version' is missing"
              : "Unsupported API version '%s'".formatted(version));
    }
    return true;
  }
}
