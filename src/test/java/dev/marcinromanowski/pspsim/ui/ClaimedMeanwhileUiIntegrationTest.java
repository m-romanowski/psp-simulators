package dev.marcinromanowski.pspsim.ui;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.ClaimRace;
import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliasGrammar;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliases;
import dev.marcinromanowski.pspsim.kernel.spi.ConfiguredAccount;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfiguration;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.TheParameterATestersValueArrivesUnder;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

@Import(ClaimRace.Wiring.class)
class ClaimedMeanwhileUiIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RACING = TestRunId.of("claimed-meanwhile-ui-it");

  private static final TestRunId ELSEWHERE = TestRunId.of("claimed-meanwhile-ui-it-elsewhere");

  private static final TestRunId EXPORTED = TestRunId.of("claimed-meanwhile-ui-it-exported");

  private static final ProviderId GROW = ProviderId.of("grow");

  private static final String FROM =
      "https://claimed-meanwhile-ui.provider.example/api/light/server/1.0/createPaymentProcess";

  private static final String TO = "/api/light/server/1.0/createPaymentProcess";

  private static final String UI_ROUTE_ALIAS = "/ui/configuration/route-alias";

  private static final String UI_ACCOUNT = "/ui/configuration/account";

  private static final String UI_IMPORT = "/ui/run-bundle/import";

  private static final List<String> TABLES_OF_A_RUN =
      List.of(
          "simulator_provider_account",
          "simulator_webhook_target",
          "simulator_route_alias",
          "grow_account",
          "simulator_test_run");

  @LocalServerPort private int port;

  @Autowired private ClaimRace race;
  @Autowired private ProviderRegistry providers;
  @Autowired private RouteAliases routeAliases;
  @Autowired private JdbcClient jdbc;

  private RestClient http;
  private HttpClient wire;

  @BeforeEach
  void connect() {
    http = RestClient.create("http://localhost:" + port);
    wire =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    forgetEveryRun();
  }

  @AfterEach
  void forgetEveryRun() {
    race.disarm();
    for (TestRunId run : List.of(RACING, ELSEWHERE, EXPORTED)) {
      for (String table : TABLES_OF_A_RUN) {
        jdbc.sql("delete from " + table + " where test_run_id = ?").param(run.value()).update();
      }
    }
  }

  @Test
  void a_route_alias_another_run_binds_while_the_form_is_written_is_recorded_as_a_409() {
    race.commitsBeforeTheNextWrite(this::bindTheAliasElsewhere);

    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "grow");
    form.put("testRunId", RACING.value());
    form.put("from", FROM);
    form.put("to", TO);
    int answered = submit(UI_ROUTE_ALIAS, form);

    assertThat(answered).isEqualTo(200);
    assertThat(theRowAt(theTrailOf(), UI_ROUTE_ALIAS)).containsEntry("status", 409);
    assertThat(routeAliases.of(GROW, RACING)).isEmpty();
  }

  @Test
  void a_credential_another_account_stores_while_the_form_is_written_is_recorded_as_a_409() {
    race.commitsBeforeTheNextWrite(this::storeSomeoneElseHoldingTheUser);

    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "grow");
    form.put("testRunId", RACING.value());
    form.put("accountId", "account-a");
    form.put(TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "userId", "u-shared");
    form.put(TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "pageCode", "p-racing");
    int answered = submit(UI_ACCOUNT, form);

    assertThat(answered).isEqualTo(200);
    assertThat(theRowAt(theTrailOf(), UI_ACCOUNT)).containsEntry("status", 409);
  }

  @Test
  void a_bundle_whose_route_alias_another_run_binds_meanwhile_is_recorded_as_a_409() {
    String document = theBundleOfARunBindingTheAlias();
    race.commitsBeforeTheNextWrite(this::bindTheAliasElsewhere);

    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "grow");
    form.put("testRunId", RACING.value());
    form.put("into", RACING.value());
    form.put("document", document);
    form.put("secrets", "");
    int answered = submit(UI_IMPORT, form);

    assertThat(answered).isEqualTo(200);
    Map<String, Object> row = theRowAt(theTrailOf(), UI_IMPORT);
    assertThat(row).containsEntry("status", 409);
    assertThat(row)
        .extractingByKey("sanitizedBody")
        .asInstanceOf(InstanceOfAssertFactories.MAP)
        .containsEntry(ActionFacts.REFUSED_PROPERTIES, List.of("routeAliases[0].from"));
    assertThat(routeAliases.of(GROW, RACING)).isEmpty();
  }

  private String theBundleOfARunBindingTheAlias() {
    ResponseEntity<String> configured =
        http.method(HttpMethod.PUT)
            .uri("/__simulator/grow/config")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                Map.of(
                    "testRunId",
                    EXPORTED.value(),
                    "routeAliases",
                    List.of(Map.of("from", FROM, "to", TO))))
            .retrieve()
            .onStatus(status -> true, (request, response) -> {})
            .toEntity(String.class);
    assertThat(configured.getStatusCode().value()).isEqualTo(200);
    ResponseEntity<String> exported =
        http.get()
            .uri("/__simulator/grow/run-bundle?testRunId=" + EXPORTED.value())
            .retrieve()
            .onStatus(status -> true, (request, response) -> {})
            .toEntity(String.class);
    assertThat(exported.getStatusCode().value()).isEqualTo(200);
    jdbc.sql("delete from simulator_route_alias where test_run_id = ?")
        .param(EXPORTED.value())
        .update();
    return String.valueOf(exported.getBody());
  }

  private void bindTheAliasElsewhere() {
    routeAliases.merge(
        GROW,
        ELSEWHERE,
        List.of(RouteAliasGrammar.aliasOf(GROW, ELSEWHERE, "routeAliases[0]", FROM, TO)));
  }

  private void storeSomeoneElseHoldingTheUser() {
    providers
        .find("grow")
        .orElseThrow()
        .parse(
            new ProviderConfiguration(
                RACING,
                List.of(
                    new ConfiguredAccount(
                        "someone-else", Map.of("userId", "u-shared", "pageCode", "p-claimer"))),
                List.of(),
                List.of(),
                Map.of()))
        .store();
  }

  private List<Map<String, Object>> theTrailOf() {
    List<Map<String, Object>> rows =
        http.get()
            .uri("/__simulator/grow/audit?testRunId=" + RACING.value() + "&origin=UI")
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    return rows == null ? List.of() : rows;
  }

  private static Map<String, Object> theRowAt(List<Map<String, Object>> rows, String path) {
    List<Map<String, Object>> found = new ArrayList<>();
    List<String> paths = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      paths.add(String.valueOf(row.get("path")));
      if (path.equals(row.get("path"))) {
        found.add(row);
      }
    }
    if (found.isEmpty()) {
      throw new AssertionError("no row of the trail names " + path + ", only " + paths);
    }
    return found.get(found.size() - 1);
  }

  private int submit(String path, Map<String, String> form) {
    List<String> pairs = new ArrayList<>();
    form.forEach(
        (name, value) ->
            pairs.add(URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)));
    try {
      return wire.send(
              HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                  .header("Content-Type", "application/x-www-form-urlencoded")
                  .timeout(Duration.ofSeconds(20))
                  .POST(HttpRequest.BodyPublishers.ofString(String.join("&", pairs)))
                  .build(),
              HttpResponse.BodyHandlers.discarding())
          .statusCode();
    } catch (IOException unreachable) {
      throw new IllegalStateException(unreachable);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }
}
