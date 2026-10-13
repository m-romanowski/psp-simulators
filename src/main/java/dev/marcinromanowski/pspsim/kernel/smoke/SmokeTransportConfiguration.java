package dev.marcinromanowski.pspsim.kernel.smoke;

import dev.marcinromanowski.pspsim.kernel.smoke.internal.LocalHttpCheckTransport;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.IntSupplier;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class SmokeTransportConfiguration {

  private static final Duration A_CHECK_WAITS_NO_LONGER = Duration.ofSeconds(5);

  private static final int NO_WEB_SERVER_IS_LISTENING = -1;

  @Bean
  CheckJson checkJson() {
    return new CheckJson();
  }

  @Bean
  SmokeResults smokeResults() {
    return new SmokeResults();
  }

  @Bean
  IntSupplier smokeLocalPort(ApplicationContext context) {
    return () ->
        context instanceof WebServerApplicationContext web && web.getWebServer() != null
            ? web.getWebServer().getPort()
            : NO_WEB_SERVER_IS_LISTENING;
  }

  @Bean
  CheckTransport smokeTransport(IntSupplier smokeLocalPort) {
    HttpClient client =
        HttpClient.newBuilder()
            .connectTimeout(A_CHECK_WAITS_NO_LONGER)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    return new SmokeNamespace(
        new LocalHttpCheckTransport(client, smokeLocalPort, A_CHECK_WAITS_NO_LONGER));
  }
}
