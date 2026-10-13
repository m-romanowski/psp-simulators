package dev.marcinromanowski.pspsim;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Tag("docker")
@TestPropertySource(properties = "simulator.webhook.poll-interval=PT1H")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class PostgresBackedTest {

  @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(DockerImageName.parse("postgres:18.6-alpine"));

  static {
    POSTGRES.start();
  }

  @LocalServerPort private int simulatorPort;

  @Autowired private JdbcClient configuration;

  private record ConfiguredAccount(String provider, String testRunId, String accountId) {}

  private record BoundRouteAlias(String provider, String testRunId, String fromUrl) {}

  @AfterEach
  void removeEverythingTheTestLeftConfiguredThroughTheControlApi() {
    RestClient control = RestClient.create("http://localhost:" + simulatorPort);
    List<BoundRouteAlias> bound =
        configuration
            .sql("select provider, test_run_id, from_url from simulator_route_alias")
            .query(BoundRouteAlias.class)
            .list();
    for (BoundRouteAlias alias : bound) {
      assertThat(
              answered(
                  control
                      .delete()
                      .uri(
                          "/__simulator/{provider}/config/route-aliases"
                              + "?testRunId={testRunId}&from={from}",
                          alias.provider(),
                          alias.testRunId(),
                          alias.fromUrl())))
          .as(
              "the cleanup after the test failed, not the test itself: it could not release the"
                  + " route alias %s of provider %s in test run %s",
              alias.fromUrl(), alias.provider(), alias.testRunId())
          .isEqualTo(HttpStatus.OK);
    }
    List<ConfiguredAccount> left =
        configuration
            .sql("select provider, test_run_id, account_id from simulator_provider_account")
            .query(ConfiguredAccount.class)
            .list();
    for (ConfiguredAccount account : left) {
      assertThat(
              answered(
                  control
                      .delete()
                      .uri(
                          "/__simulator/{provider}/config/accounts/{accountId}"
                              + "?testRunId={testRunId}",
                          account.provider(),
                          account.accountId(),
                          account.testRunId())))
          .as(
              "the cleanup after the test failed, not the test itself: it could not remove the"
                  + " account %s of provider %s in test run %s",
              account.accountId(), account.provider(), account.testRunId())
          .isEqualTo(HttpStatus.OK);
    }
  }

  private static HttpStatus answered(RestClient.RequestHeadersSpec<?> request) {
    return HttpStatus.valueOf(
        request
            .retrieve()
            .onStatus(status -> true, (sent, response) -> {})
            .toBodilessEntity()
            .getStatusCode()
            .value());
  }
}
