package org.acm.os.infra.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.acm.os.application.port.out.ExternalDependencyException;
import org.acm.os.domain.shared.InvalidRequestException;
import org.junit.jupiter.api.Test;

class MockFailureRegistryTest {

  @Test
  void failureRuleAppliesExactlyOnce() {
    MockFailureRegistry registry = new MockFailureRegistry();
    registry.check("product");
    registry.failNext("product");

    assertThatThrownBy(() -> registry.check("product"))
        .isInstanceOf(ExternalDependencyException.class);
    registry.check("product");
  }

  @Test
  void rejectsUnknownCapability() {
    MockFailureRegistry registry = new MockFailureRegistry();
    assertThatThrownBy(() -> registry.failNext("unknown"))
        .isInstanceOf(InvalidRequestException.class);
  }
}
