package org.acm.ca;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "eureka.client.enabled=false",
      "spring.ai.openai.api-key=test-key"
    })
class CustomerAgentApplicationTests {

  @Test
  void contextLoads() {}
}