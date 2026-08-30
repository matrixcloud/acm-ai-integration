package org.acm.ca.infra.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;
import org.acm.ca.application.port.out.KbSearchClient;
import org.acm.ca.application.port.out.KbSearchClient.KbChunk;
import org.acm.ca.application.port.out.KbSearchClient.SearchRequest;
import org.acm.ca.application.port.out.KbSearchUnavailableException;
import org.acm.ca.application.port.out.TransientKbSearchException;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

class KbSearchClientImplTest {

  private static final String REQUEST_URL = "http://kb-svc/kbs/KB-2026-0001/search";
  private static final SearchRequest REQUEST = new SearchRequest("KB-2026-0001", "退款政策", 5);
  private static final String CHUNKS_JSON =
      """
      {
        "chunks": [
          {
            "content": "退款审核通过后将原路退回。",
            "score": 0.92,
            "documentNo": "DOC-1",
            "documentName": "退款政策"
          }
        ]
      }
      """;

  @Test
  void searchCallsKbServiceAndMapsResponse() {
    Fixture fixture = fixture();
    fixture.server()
        .expect(requestTo(REQUEST_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("API-Version", "1"))
        .andExpect(content().string("{\"query\":\"退款政策\",\"topK\":5}"))
        .andRespond(withSuccess(CHUNKS_JSON, MediaType.APPLICATION_JSON));

    List<KbChunk> chunks = fixture.client().search(REQUEST);

    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0).content()).isEqualTo("退款审核通过后将原路退回。");
    assertThat(chunks.get(0).score()).isEqualTo(0.92);
    assertThat(chunks.get(0).documentNo()).isEqualTo("DOC-1");
    assertThat(chunks.get(0).documentName()).isEqualTo("退款政策");
    fixture.server().verify();
  }

  @Test
  void searchReturnsEmptyWhenNoChunkMatches() {
    Fixture fixture = fixture();
    fixture.server().expect(requestTo(REQUEST_URL))
        .andRespond(withSuccess("{\"chunks\":[]}", MediaType.APPLICATION_JSON));

    assertThat(fixture.client().search(REQUEST)).isEmpty();
    fixture.server().verify();
  }

  @Test
  void searchFailsFastOnMissingChunks() {
    Fixture fixture = fixture();
    fixture.server().expect(requestTo(REQUEST_URL))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> fixture.client().search(REQUEST))
        .isInstanceOf(KbSearchUnavailableException.class)
        .hasMessage("KB search response chunks must not be null");
    fixture.server().verify();
  }

  @Test
  void searchFailsFastOnMissingResponseBody() {
    Fixture fixture = fixture();
    fixture.server().expect(requestTo(REQUEST_URL))
        .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> fixture.client().search(REQUEST))
        .isInstanceOf(KbSearchUnavailableException.class)
        .hasMessage("KB search response body must not be null");
    fixture.server().verify();
  }

  @Test
  void searchRetriesOnceOnServiceUnavailableThenSucceeds() {
    try (RetryFixture fixture = retryFixture()) {
      fixture.server()
          .expect(once(), requestTo(REQUEST_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
      fixture.server()
          .expect(once(), requestTo(REQUEST_URL)).andRespond(withSuccess(CHUNKS_JSON, MediaType.APPLICATION_JSON));

      List<KbChunk> chunks = fixture.client().search(REQUEST);

      assertThat(chunks).hasSize(1);
      fixture.server().verify();
    }
  }

  @Test
  void searchDoesNotRetryOnBadRequest() {
    try (RetryFixture fixture = retryFixture()) {
      fixture.server()
          .expect(once(), requestTo(REQUEST_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

      assertThatThrownBy(() -> fixture.client().search(REQUEST))
          .isInstanceOf(KbSearchUnavailableException.class)
          .hasMessage("Unexpected kb-svc response status: HTTP 400 BAD_REQUEST");
      fixture.server().verify();
    }
  }

  @Test
  void searchThrowsStableExceptionAfterRetriesExhaustedWithinBudget() {
    try (RetryFixture fixture = retryFixture()) {
      fixture.server()
          .expect(once(), requestTo(REQUEST_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
      fixture.server()
          .expect(once(), requestTo(REQUEST_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

      long start = System.nanoTime();
      assertThatThrownBy(() -> fixture.client().search(REQUEST))
          .isInstanceOfSatisfying(
              TransientKbSearchException.class,
              e -> assertThat(e.code()).isEqualTo("EXTERNAL_DEPENDENCY_FAILED"))
          .hasMessage("KB service transient failure: HTTP 503 SERVICE_UNAVAILABLE");
      Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

      assertThat(elapsed).isLessThan(Duration.ofSeconds(8));
      assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(100));
      fixture.server().verify();
    }
  }

  private Fixture fixture() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://kb-svc");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    RestClientAdapter adapter = RestClientAdapter.create(builder.build());
    KbServiceHttpClient httpClient =
        HttpServiceProxyFactory.builderFor(adapter)
            .build()
            .createClient(KbServiceHttpClient.class);
    return new Fixture(new KbSearchClientImpl(httpClient), server);
  }

  private RetryFixture retryFixture() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://kb-svc");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    RestClientAdapter adapter = RestClientAdapter.create(builder.build());
    KbServiceHttpClient httpClient =
        HttpServiceProxyFactory.builderFor(adapter)
            .build()
            .createClient(KbServiceHttpClient.class);
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.register(HttpServiceResilienceConfiguration.class);
    context.registerBean(KbSearchClientImpl.class, () -> new KbSearchClientImpl(httpClient));
    context.refresh();
    return new RetryFixture(context.getBean(KbSearchClient.class), server, context);
  }

  private record Fixture(KbSearchClientImpl client, MockRestServiceServer server) {}

  private record RetryFixture(
      KbSearchClient client, MockRestServiceServer server, AnnotationConfigApplicationContext context)
      implements AutoCloseable {

    @Override
    public void close() {
      context.close();
    }
  }
}
