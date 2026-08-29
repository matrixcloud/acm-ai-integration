package org.acm.os.infra.client;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.acm.os.application.port.out.ExternalDependencyException;
import org.acm.os.domain.shared.InvalidRequestException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
public class MockFailureRegistry {
  public static final Set<String> CAPABILITIES =
      Set.of(
          "product",
          "inventory-reserve",
          "inventory-confirm",
          "inventory-release",
          "inventory-restore",
          "payment-create",
          "payment-refund",
          "logistics-create",
          "logistics-confirm");

  private final Set<String> nextFailures = ConcurrentHashMap.newKeySet();

  public void failNext(String capability) {
    if (!CAPABILITIES.contains(capability)) {
      throw new InvalidRequestException("Unknown Mock capability '%s'".formatted(capability));
    }
    nextFailures.add(capability);
  }

  public void check(String capability) {
    if (nextFailures.remove(capability)) {
      throw new ExternalDependencyException(
          "Mock capability '%s' failed as configured".formatted(capability));
    }
  }
}
