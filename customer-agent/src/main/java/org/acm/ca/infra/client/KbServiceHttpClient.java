package org.acm.ca.infra.client;

import java.util.List;
import org.acm.ca.infra.client.KbSearchClientImpl.KbSearchRequest;
import org.acm.ca.infra.client.KbSearchClientImpl.KbSearchResponse;
import org.acm.ca.infra.client.KbSearchClientImpl.KbSummaryResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface KbServiceHttpClient {

  @PostExchange("/kbs/{kbNo}/search")
  KbSearchResponse search(
      @RequestHeader("API-Version") String apiVersion,
      @PathVariable("kbNo") String kbNo,
      @RequestBody KbSearchRequest request);

  @GetExchange("/kbs")
  List<KbSummaryResponse> listActive(@RequestHeader("API-Version") String apiVersion);
}
