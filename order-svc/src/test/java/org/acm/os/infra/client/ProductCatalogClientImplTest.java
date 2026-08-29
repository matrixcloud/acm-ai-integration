package org.acm.os.infra.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.acm.os.application.port.out.ProductNotFoundException;
import org.junit.jupiter.api.Test;

class ProductCatalogClientImplTest {

  private final ProductCatalogClientImpl client = new ProductCatalogClientImpl();

  @Test
  void returnsEveryRequestedProductSnapshot() {
    assertThat(client.getSaleableProducts(Set.of("SKU-001", "SKU-003")))
        .extracting(snapshot -> snapshot.skuId())
        .containsExactlyInAnyOrder("SKU-001", "SKU-003");
  }

  @Test
  void rejectsUnknownSku() {
    assertThatThrownBy(() -> client.getSaleableProducts(Set.of("SKU-404")))
        .isInstanceOf(ProductNotFoundException.class)
        .hasMessage("SKU 'SKU-404' is not available");
  }
}
