package dev.marcinromanowski.pspsim.control;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

class ConfigurationAtomicityIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "config-atomicity-it";

  private static final String FIRST_PIN = "2026-09-20T08:00:00Z";

  private static final String SECOND_PIN = "2026-09-21T09:30:00Z";

  private static final KeyPair AZERICARD_KEYS = rsa();

  private static final List<String> TABLES_OF_THE_RUN =
      List.of(
          "simulator_provider_account",
          "simulator_webhook_target",
          "grow_account",
          "azericard_terminal",
          "stripe_account",
          "autra_merchant",
          "worldline_merchant",
          "simulator_test_run");

  @LocalServerPort private int port;

  @Autowired private JdbcClient jdbc;

  private RestClient http;

  @BeforeEach
  void connect() {
    http = RestClient.create("http://localhost:" + port);
    forgetTheRun();
  }

  @AfterEach
  void forgetTheRun() {
    for (String table : TABLES_OF_THE_RUN) {
      jdbc.sql("delete from " + table + " where test_run_id = ?").param(RUN).update();
    }
  }

  @Test
  void an_autra_account_taking_a_client_id_a_stored_account_holds_is_refused_and_changes_nothing() {
    assertThat(
            configure("autra", FIRST_PIN, List.of(autraAccount("account-a", "client-a", "111")))
                .getStatusCode()
                .value())
        .isEqualTo(200);

    ResponseEntity<Map<String, Object>> refused =
        configure("autra", SECOND_PIN, List.of(autraAccount("account-b", "client-a", "222")));

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(String.valueOf(refused.getBody().get("message")))
        .isEqualTo(
            "accounts[0].configuration.clientId is already configured for another account in this"
                + " test run");
    assertThat(thePinOfTheRun()).contains(OffsetDateTime.parse(FIRST_PIN));
    assertThat(theCredentialsIn("autra_merchant", "client_id"))
        .containsExactly(Map.entry("account-a", "client-a"));
    assertThat(theKernelAccounts()).containsExactly("account-a");
  }

  @Test
  void
      a_worldline_account_taking_an_api_key_a_stored_account_holds_is_refused_and_changes_nothing() {
    assertThat(
            configure(
                    "worldline",
                    FIRST_PIN,
                    List.of(worldlineAccount("account-a", "PSPID-A", "key-a")))
                .getStatusCode()
                .value())
        .isEqualTo(200);

    ResponseEntity<Map<String, Object>> refused =
        configure(
            "worldline", SECOND_PIN, List.of(worldlineAccount("account-b", "PSPID-B", "key-a")));

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(String.valueOf(refused.getBody().get("message")))
        .isEqualTo(
            "accounts[0].configuration.apiKey is already configured for another account in this"
                + " test run");
    assertThat(thePinOfTheRun()).contains(OffsetDateTime.parse(FIRST_PIN));
    assertThat(theCredentialsIn("worldline_merchant", "api_key"))
        .containsExactly(Map.entry("account-a", "key-a"));
    assertThat(theKernelAccounts()).containsExactly("account-a");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("everyProvider")
  void two_accounts_swapping_their_credentials_in_one_call_each_end_up_holding_the_other_one(
      Swap swap) {
    assertThat(
            configure(
                    swap.provider(),
                    FIRST_PIN,
                    List.of(
                        swap.holding().apply(new Held("account-a", "a")),
                        swap.holding().apply(new Held("account-b", "b"))))
                .getStatusCode()
                .value())
        .isEqualTo(200);

    ResponseEntity<Map<String, Object>> swapped =
        configure(
            swap.provider(),
            SECOND_PIN,
            List.of(
                swap.holding().apply(new Held("account-a", "b")),
                swap.holding().apply(new Held("account-b", "a"))));

    assertThat(swapped.getStatusCode().value())
        .as(String.valueOf(swapped.getBody()))
        .isEqualTo(200);
    assertThat(theCredentialsIn(swap.table(), swap.column()))
        .containsExactly(
            Map.entry("account-a", swap.credentialOf("b")),
            Map.entry("account-b", swap.credentialOf("a")));
    assertThat(thePinOfTheRun()).contains(OffsetDateTime.parse(SECOND_PIN));
  }

  record Held(String accountId, String suffix) {}

  record Swap(
      String provider,
      String table,
      String column,
      String credentialPrefix,
      Function<Held, Map<String, Object>> holding) {

    String credentialOf(String suffix) {
      return credentialPrefix + suffix;
    }

    @Override
    public String toString() {
      return provider;
    }
  }

  static Stream<Swap> everyProvider() {
    return Stream.of(
        new Swap(
            "grow",
            "grow_account",
            "user_id",
            "grow-user-",
            held ->
                account(
                    held.accountId(),
                    Map.of(
                        "userId", "grow-user-" + held.suffix(),
                        "pageCode", "grow-page-" + held.suffix()))),
        new Swap(
            "azericard",
            "azericard_terminal",
            "terminal_id",
            "term-",
            held ->
                account(
                    held.accountId(),
                    Map.of(
                        "terminalId",
                        "term-" + held.suffix(),
                        "apiKey",
                        pem("PUBLIC KEY", AZERICARD_KEYS.getPublic().getEncoded()),
                        "apiSecret",
                        pem("PRIVATE KEY", AZERICARD_KEYS.getPrivate().getEncoded())))),
        new Swap(
            "stripe",
            "stripe_account",
            "secret_key",
            "sk_test_swap_",
            held ->
                account(
                    held.accountId(),
                    Map.of("secretKey", "sk_test_swap_" + held.suffix(), "captureWindow", "P7D"))),
        new Swap(
            "autra",
            "autra_merchant",
            "client_id",
            "client-",
            held ->
                autraAccount(
                    held.accountId(),
                    "client-" + held.suffix(),
                    "a".equals(held.suffix()) ? "111" : "222")),
        new Swap(
            "worldline",
            "worldline_merchant",
            "api_key",
            "key-",
            held ->
                worldlineAccount(
                    held.accountId(), "PSPID-" + held.suffix(), "key-" + held.suffix())));
  }

  private static Map<String, Object> autraAccount(
      String accountId, String clientId, String documentId) {
    return account(
        accountId,
        Map.of(
            "clientId",
            clientId,
            "clientSecret",
            "secret-of-" + clientId,
            "documentId",
            documentId));
  }

  private static Map<String, Object> worldlineAccount(
      String accountId, String merchantId, String apiKey) {
    return account(
        accountId,
        Map.of("merchantId", merchantId, "apiKey", apiKey, "apiSecret", "secret-of-" + apiKey));
  }

  private static Map<String, Object> account(String accountId, Map<String, String> configuration) {
    return Map.of("accountId", accountId, "configuration", configuration);
  }

  private Optional<OffsetDateTime> thePinOfTheRun() {
    return jdbc.sql("select fixed_now from simulator_test_run where test_run_id = ?")
        .param(RUN)
        .query(OffsetDateTime.class)
        .optional();
  }

  private Map<String, String> theCredentialsIn(String table, String column) {
    Map<String, String> held = new LinkedHashMap<>();
    jdbc.sql(
            "select account_id, "
                + column
                + " from "
                + table
                + " where test_run_id = ? order by account_id")
        .param(RUN)
        .query((rs, row) -> held.put(rs.getString("account_id"), rs.getString(column)))
        .list();
    return held;
  }

  private List<String> theKernelAccounts() {
    return jdbc.sql(
            "select account_id from simulator_provider_account where test_run_id = ?"
                + " order by account_id")
        .param(RUN)
        .query(String.class)
        .list();
  }

  private ResponseEntity<Map<String, Object>> configure(
      String provider, String fixedNow, List<Map<String, Object>> accounts) {
    return http.method(HttpMethod.PUT)
        .uri("/__simulator/" + provider + "/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId", RUN,
                "clock", Map.of("fixedNow", fixedNow),
                "accounts", accounts))
        .retrieve()
        .onStatus(status -> true, (request, response) -> {})
        .toEntity(new ParameterizedTypeReference<>() {});
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
