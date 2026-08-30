package org.acm.common.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class HttpRequestLoggingFilterTest {

  private ListAppender<ILoggingEvent> appender;
  private HttpRequestLoggingFilter filter;

  @BeforeEach
  void setUp() {
    filter = new HttpRequestLoggingFilter();
    Logger logger = (Logger) LoggerFactory.getLogger(HttpRequestLoggingFilter.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @Test
  void logsOneSummaryLinePerRequest() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
    request.setRemoteAddr("127.0.0.1");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(201));

    assertEquals(1, appender.list.size());
    String message = appender.list.get(0).getFormattedMessage();
    assertTrue(message.contains("method=GET"));
    assertTrue(message.contains("path=/orders"));
    assertTrue(message.contains("status=201"));
    assertTrue(message.contains("durationMs="));
    assertTrue(message.contains("clientIp=127.0.0.1"));
  }

  @Test
  void usesFirstForwardedForValueAsClientIp() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");
    request.setRemoteAddr("10.0.0.5");
    request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.5");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    String message = appender.list.get(0).getFormattedMessage();
    assertTrue(message.contains("clientIp=203.0.113.7"));
  }

  @Test
  void logsSummaryWhenChainThrowsAndRethrows() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
    MockHttpServletResponse response = new MockHttpServletResponse();
    ServletException expected = new ServletException("boom");

    assertThrows(
        ServletException.class,
        () ->
            filter.doFilter(
                request,
                response,
                (req, res) -> {
                  throw expected;
                }));

    assertEquals(1, appender.list.size());
    assertTrue(appender.list.get(0).getFormattedMessage().contains("path=/orders"));
  }

  @Test
  void doesNotMaskIOException() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThrows(
        IOException.class,
        () ->
            filter.doFilter(
                request,
                response,
                (req, res) -> {
                  throw new IOException("broken pipe");
                }));

    assertEquals(1, appender.list.size());
  }
}
