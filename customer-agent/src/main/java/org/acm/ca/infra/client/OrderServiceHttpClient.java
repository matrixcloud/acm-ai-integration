package org.acm.ca.infra.client;

import org.acm.ca.infra.client.OrderQueryClientImpl.OrderDetailResponse;
import org.acm.ca.infra.client.OrderQueryClientImpl.OrderPageResponse;
import org.springframework.web.bind.annotation.PathVariable;
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

  @GetExchange("/orders")
  OrderPageResponse searchByPhone(
      @RequestHeader("API-Version") String apiVersion,
      @RequestParam("recipientPhone") String recipientPhone,
      @RequestParam("page") int page,
      @RequestParam("size") int size,
      @RequestParam("sortBy") String sortBy,
      @RequestParam("direction") String direction);

  @GetExchange("/orders/{orderNo}")
  OrderDetailResponse getOrder(
      @RequestHeader("API-Version") String apiVersion, @PathVariable("orderNo") String orderNo);
}
