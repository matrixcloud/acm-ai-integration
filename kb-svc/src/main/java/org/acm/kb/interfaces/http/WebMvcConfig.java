package org.acm.kb.interfaces.http;

import org.acm.common.logging.HttpRequestLoggingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  @Bean
  FilterRegistrationBean<HttpRequestLoggingFilter> requestLoggingFilter() {
    FilterRegistrationBean<HttpRequestLoggingFilter> registration =
        new FilterRegistrationBean<>(new HttpRequestLoggingFilter());
    registration.setOrder(Ordered.LOWEST_PRECEDENCE);
    return registration;
  }
}
