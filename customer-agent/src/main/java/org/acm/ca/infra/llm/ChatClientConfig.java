package org.acm.ca.infra.llm;

import java.util.concurrent.Executor;
import org.acm.ca.application.rule.ReplyRulesConfig;
import org.acm.ca.infra.observability.ToolCallObservingAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(ReplyRulesConfig.class)
public class ChatClientConfig {

  @Bean
  ChatClient chatClient(
      ChatClient.Builder builder,
      ReplyRulesConfig config,
      ToolCallObservingAdvisor observingAdvisor) {
    return builder
        .defaultAdvisors(observingAdvisor)
        .defaultSystem(config.defaultSystemPrompt())
        .build();
  }

  @Bean(destroyMethod = "shutdown")
  Executor agentExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(16);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("agent-");
    executor.initialize();
    return executor;
  }
}