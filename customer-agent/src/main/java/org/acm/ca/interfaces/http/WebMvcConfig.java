package org.acm.ca.interfaces.http;

import lombok.RequiredArgsConstructor;
import org.acm.common.logging.HttpRequestLoggingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
  private final ApiVersionInterceptor apiVersionInterceptor;

  @Bean
  FilterRegistrationBean<HttpRequestLoggingFilter> requestLoggingFilter() {
    FilterRegistrationBean<HttpRequestLoggingFilter> registration =
        new FilterRegistrationBean<>(new HttpRequestLoggingFilter());
    registration.setOrder(Ordered.LOWEST_PRECEDENCE);
    return registration;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(apiVersionInterceptor)
        .addPathPatterns("/conversations/**", "/quick-questions/**", "/agent/**");
  }
}
