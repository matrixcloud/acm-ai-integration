package org.acm.os.application.service;

import java.math.BigDecimal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.acm.os.application.port.in.MockConfigurationUseCase;
import org.acm.os.application.port.out.MockControlPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("demo")
@RequiredArgsConstructor
public class MockConfigurationService implements MockConfigurationUseCase {
  private final MockControlPort mockControlPort;
  private final IdempotencyService idempotencyService;

  @Override
  public void setProduct(
      String skuId,
      String productName,
      BigDecimal unitPrice,
      String currency,
      boolean saleable,
      String idempotencyKey) {
    execute(
        "mock-set-product",
        idempotencyKey,
        Map.of(
            "skuId", skuId,
            "productName", productName,
            "unitPrice", unitPrice,
            "currency", currency,
            "saleable", saleable),
        () -> mockControlPort.setProduct(skuId, productName, unitPrice, currency, saleable));
  }

  @Override
  public void setInventory(String skuId, int quantity, String idempotencyKey) {
    execute(
        "mock-set-inventory",
        idempotencyKey,
        Map.of("skuId", skuId, "quantity", quantity),
        () -> mockControlPort.setInventory(skuId, quantity));
  }

  @Override
  public void failNext(String capability, String idempotencyKey) {
    execute(
        "mock-fail-next",
        idempotencyKey,
        Map.of("capability", capability),
        () -> mockControlPort.failNext(capability));
  }

  private void execute(
      String operation, String idempotencyKey, Object request, Runnable action) {
    idempotencyService.execute(
        new IdempotencyService.IdempotentOperation<>(
            operation, idempotencyKey, request, Boolean.class),
        () -> {
          action.run();
          return Boolean.TRUE;
        });
  }
}
