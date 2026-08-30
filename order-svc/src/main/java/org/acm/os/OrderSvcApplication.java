package org.acm.os;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OrderSvcApplication {

  /**
   * Starts the order service.
   *
   * <p>Entities and repositories are discovered by Boot's default scan rooted at {@code
   * org.acm.os}, so no {@code @EntityScan} / {@code @EnableJpaRepositories} narrowing is needed —
   * and narrowing would silently exclude any entity moved out of {@code org.acm.os.domain}.
   *
   * @param args application arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(OrderSvcApplication.class, args);
  }
}
