package dev.marcinromanowski.pspsim.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import dev.marcinromanowski.pspsim.ClaimRace;
import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliasGrammar;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliases;
import dev.marcinromanowski.pspsim.kernel.spi.ConfiguredAccount;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfiguration;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.ClaimedMeanwhileException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Import(ClaimRace.Wiring.class)
class ClaimedMeanwhileApiIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RACING = TestRunId.of("claimed-meanwhile-api-it");

  private static final TestRunId ELSEWHERE = TestRunId.of("claimed-meanwhile-api-it-elsewhere");

  private static final TestRunId EXPORTED = TestRunId.of("claimed-meanwhile-api-it-exported");

  private static final String ACCOUNT = "account-a";

  private static final String SOMEONE_ELSE = "someone-else";

  private static final String FROM =
      "https://claimed-meanwhile.provider.example/api/light/server/1.0/createPaymentProcess";

  private static final String TO = "/api/light/server/1.0/createPaymentProcess";

  private static final KeyPair MERCHANT_KEYS = rsa();

  private static final KeyPair GATEWAY_KEYS = rsa();

  private static final List<String> TABLES_OF_A_RUN =
      List.of(
          "simulator_provider_account",
          "simulator_webhook_target",
          "simulator_route_alias",
          "grow_account",
          "azericard_terminal",
          "stripe_account",
          "autra_merchant",
          "worldline_merchant",
          "simulator_test_run");

  @LocalServerPort private int port;

  @Autowired private ClaimRace race;
  @Autowired private ProviderRegistry providers;
  @Autowired private ProviderAccounts accounts;
  @Autowired private RouteAliases routeAliases;
  @Autowired private JdbcClient jdbc;

  private final ObjectMapper json = new ObjectMapper();

  private RestClient http;

  @BeforeEach
  void connect() {
    http = RestClient.create("http://localhost:" + port);
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

  static Stream<Arguments> everyCredentialAProviderClaims() {
    String merchantKey = pem("PUBLIC KEY", MERCHANT_KEYS.getPublic().getEncoded());
    String gatewayKey = pem("PRIVATE KEY", GATEWAY_KEYS.getPrivate().getEncoded());
    return Stream.of(
        arguments(
            "grow_account_by_user_id",
            "grow",
            Map.of("userId", "u-shared", "pageCode", "p-racing"),
            Map.of("userId", "u-shared", "pageCode", "p-claimer")),
        arguments(
            "grow_account_by_page_code",
            "grow",
            Map.of("userId", "u-racing", "pageCode", "p-shared"),
            Map.of("userId", "u-claimer", "pageCode", "p-shared")),
        arguments(
            "azericard_terminal_by_terminal_id",
            "azericard",
            Map.of("terminalId", "7777", "apiKey", merchantKey, "apiSecret", gatewayKey),
            Map.of("terminalId", "7777", "apiKey", merchantKey, "apiSecret", gatewayKey)),
        arguments(
            "stripe_account_by_secret_key",
            "stripe",
            Map.of("secretKey", "sk_test_claimedMeanwhileShared", "captureWindow", "P3D"),
            Map.of("secretKey", "sk_test_claimedMeanwhileShared", "captureWindow", "P3D")),
        arguments(
            "autra_merchant_by_client_id",
            "autra",
            Map.of("clientId", "c-shared", "clientSecret", "s-racing", "documentId", "111"),
            Map.of("clientId", "c-shared", "clientSecret", "s-claimer", "documentId", "222")),
        arguments(
            "autra_merchant_by_document_id",
            "autra",
            Map.of("clientId", "c-racing", "clientSecret", "s-racing", "documentId", "333"),
            Map.of("clientId", "c-claimer", "clientSecret", "s-claimer", "documentId", "333")),
        arguments(
            "worldline_merchant_by_merchant_id",
            "worldline",
            Map.of("merchantId", "m-shared", "apiKey", "k-racing", "apiSecret", "s-racing"),
            Map.of("merchantId", "m-shared", "apiKey", "k-claimer", "apiSecret", "s-claimer")),
        arguments(
            "worldline_merchant_by_api_key",
            "worldline",
            Map.of("merchantId", "m-racing", "apiKey", "k-shared", "apiSecret", "s-racing"),
            Map.of("merchantId", "m-claimer", "apiKey", "k-shared", "apiSecret", "s-claimer")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("everyCredentialAProviderClaims")
  void a_credential_another_account_stores_after_the_dry_run_is_answered_409_and_keeps_nothing(
      String claim,
      String provider,
      Map<String, String> racing,
      Map<String, String> claimedMeanwhile) {
    race.commitsBeforeTheNextWrite(() -> storeSomeoneElse(provider, claimedMeanwhile));

    ResponseEntity<String> answered =
        exchange(
            HttpMethod.PUT,
            "/__simulator/" + provider + "/config",
            Map.of(
                "testRunId",
                RACING.value(),
                "accounts",
                List.of(Map.of("accountId", ACCOUNT, "configuration", racing))));

    assertThat(answered.getStatusCode().value())
        .as(claim + ": " + answered.getBody())
        .isEqualTo(409);
    assertThat(answered.getBody())
        .contains(ClaimedMeanwhileException.A_VALUE_THIS_REQUEST_WRITES_WAS_CLAIMED_MEANWHILE);
    assertThat(accounts.identitiesOf(ProviderId.of(provider), RACING)).isEmpty();
  }

  @Test
  void a_route_alias_another_run_binds_after_the_dry_run_is_answered_409_under_its_property() {
    race.commitsBeforeTheNextWrite(this::bindTheAliasElsewhere);

    ResponseEntity<String> answered =
        exchange(
            HttpMethod.PUT,
            "/__simulator/grow/config",
            Map.of(
                "testRunId",
                RACING.value(),
                "routeAliases",
                List.of(Map.of("from", FROM, "to", TO))));

    assertThat(answered.getStatusCode().value()).isEqualTo(409);
    assertThat(answered.getBody()).contains("routeAliases[0].from names a URL");
    assertThat(routeAliases.of(ProviderId.of("grow"), RACING)).isEmpty();
  }

  @Test
  void a_bundle_whose_route_alias_another_run_binds_meanwhile_is_answered_409_naming_the_alias() {
    Map<String, Object> bundle = theBundleOfARunBindingTheAlias();
    race.commitsBeforeTheNextWrite(this::bindTheAliasElsewhere);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("testRunId", RACING.value());
    body.put("bundle", bundle);
    ResponseEntity<String> answered =
        exchange(HttpMethod.POST, "/__simulator/grow/run-bundle/import", body);

    assertThat(answered.getStatusCode().value()).isEqualTo(409);
    assertThat(json.readValue(answered.getBody(), new TypeReference<Map<String, Object>>() {}))
        .containsEntry("properties", List.of("routeAliases[0].from"));
    assertThat(routeAliases.of(ProviderId.of("grow"), RACING)).isEmpty();
  }

  private Map<String, Object> theBundleOfARunBindingTheAlias() {
    ResponseEntity<String> configured =
        exchange(
            HttpMethod.PUT,
            "/__simulator/grow/config",
            Map.of(
                "testRunId",
                EXPORTED.value(),
                "routeAliases",
                List.of(Map.of("from", FROM, "to", TO))));
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
    return json.readValue(exported.getBody(), new TypeReference<Map<String, Object>>() {});
  }

  private void storeSomeoneElse(String provider, Map<String, String> configuration) {
    providers
        .find(provider)
        .orElseThrow()
        .parse(
            new ProviderConfiguration(
                RACING,
                List.of(new ConfiguredAccount(SOMEONE_ELSE, configuration)),
                List.of(),
                List.of(),
                Map.of()))
        .store();
  }

  private void bindTheAliasElsewhere() {
    ProviderId grow = ProviderId.of("grow");
    routeAliases.merge(
        grow,
        ELSEWHERE,
        List.of(RouteAliasGrammar.aliasOf(grow, ELSEWHERE, "routeAliases[0]", FROM, TO)));
  }

  private ResponseEntity<String> exchange(HttpMethod method, String path, Object body) {
    return http.method(method)
        .uri(path)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .onStatus(status -> true, (request, response) -> {})
        .toEntity(String.class);
  }

  private static KeyPair rsa() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (NoSuchAlgorithmException missing) {
      throw new IllegalStateException(missing);
    }
  }

  private static String pem(String label, byte[] encoded) {
    return "-----BEGIN "
        + label
        + "-----\n"
        + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded)
        + "\n-----END "
        + label
        + "-----\n";
  }
}
