package org.acm.ca.infra.llm;

import org.acm.ca.application.rule.ReplyRulesConfig;
import org.acm.ca.infra.observability.ToolCallObservingAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}