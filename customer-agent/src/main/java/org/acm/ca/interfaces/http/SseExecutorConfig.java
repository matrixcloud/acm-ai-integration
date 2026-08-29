package org.acm.ca.interfaces.http;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Shared executor for SSE streaming requests so request threads return immediately. */
@Configuration
public class SseExecutorConfig {

  @Bean(destroyMethod = "shutdown")
  Executor sseExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(16);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("sse-");
    executor.initialize();
    return executor;
  }
}
