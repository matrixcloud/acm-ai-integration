package org.acm.ca.infra.client;

import org.acm.ca.infra.client.OrderQueryClientImpl.OrderPageResponse;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange
public interface OrderServiceHttpClient {

  @GetExchange("/orders")
  OrderPageResponse search(
      @RequestHeader("API-Version") String apiVersion,
      @RequestParam("customerId") String customerId,
      @RequestParam("page") int page,
      @RequestParam("size") int size,
      @RequestParam("sortBy") String sortBy,
      @RequestParam("direction") String direction);
}
