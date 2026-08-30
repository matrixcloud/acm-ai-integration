package org.acm.ca.infra.client;

import java.time.Duration;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(
    group = KbServiceHttpClientConfiguration.KB_SERVICE_GROUP,
    types = KbServiceHttpClient.class)
class KbServiceHttpClientConfiguration {

  static final String KB_SERVICE_GROUP = "kb-svc";
  static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
  static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

  @Bean
  RestClientHttpServiceGroupConfigurer kbServiceHttpClientTimeouts() {
    return groups ->
        groups
            .filterByName(KB_SERVICE_GROUP)
            .forEachClient(
                (group, builder) ->
                    builder.requestFactory(
                        ClientHttpRequestFactoryBuilder.detect()
                            .build(
                                HttpClientSettings.defaults()
                                    .withTimeouts(CONNECT_TIMEOUT, READ_TIMEOUT))));
  }
}
