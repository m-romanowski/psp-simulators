package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAlias;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliasKey;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import dev.marcinromanowski.pspsim.kernel.spi.DisclosedAccount;
import dev.marcinromanowski.pspsim.kernel.spi.PairingSetting;
import dev.marcinromanowski.pspsim.kernel.spi.PairingValue;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderAccountFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPairing;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PairingScreenTest {

  private static final ProviderId PROVIDER = ProviderId.of("grow");

  private static final String BASE_URL = "https://simulator.internal";

  private static final String RUN = "e2e run 7";

  private static final String ACCOUNT = "merchant-1";

  private static final String THE_MERCHANT_ID_A_TESTER_MAY_READ = "PSPID-4412";

  private static final String THE_SECRET_NOBODY_MAY_READ = "sk_test_never_on_a_monitor";

  private static final ProviderAccountFields FIELDS =
      new ProviderAccountFields(
          PROVIDER,
          List.of(
              new ConfigurableField(
                  "merchantId",
                  "The merchant id",
                  "a sentence a test does not read",
                  true,
                  false,
                  List.of(),
                  "a value a test does not read"),
              new ConfigurableField(
                  "apiSecret",
                  "The signing secret",
                  "a sentence a test does not read",
                  true,
                  true,
                  List.of(),
                  "a value a test does not read"),
              new ConfigurableField(
                  "captureWindow",
                  "How long a hold lasts",
                  "a sentence a test does not read",
                  false,
                  false,
                  List.of(),
                  "a value a test does not read")));

  @Test
  void a_setting_drawn_from_a_field_the_provider_declares_plain_carries_its_configured_value() {
    PairingScreen.Setting setting = settingOf("WORLDLINE_MERCHANT_ID", "merchantId");

    assertThat(setting.shown()).isEqualTo(PairingScreen.Shown.VALUE);
    assertThat(setting.value()).isEqualTo("PSPID-4412");
    assertThat(setting.name()).isEqualTo("WORLDLINE_MERCHANT_ID");
    assertThat(setting.fromField()).isEqualTo("merchantId");
  }

  @Test
  void a_setting_drawn_from_a_field_the_provider_declares_secret_names_it_and_carries_no_value() {
    PairingScreen.Setting setting = settingOf("WORLDLINE_API_SECRET", "apiSecret");

    assertThat(setting.shown()).isEqualTo(PairingScreen.Shown.WITHHELD_AS_SECRET);
    assertThat(setting.value()).isEqualTo("");
    assertThat(setting.fromField()).isEqualTo("apiSecret");
  }

  @Test
  void a_setting_drawn_from_a_field_the_provider_declares_nowhere_is_withheld_rather_than_shown() {
    PairingScreen.Setting setting = settingOf("A_KEY_NO_PROVIDER_DECLARES", "smugglingKey");

    assertThat(setting.shown()).isEqualTo(PairingScreen.Shown.WITHHELD_AS_UNDECLARED);
    assertThat(setting.value()).isEqualTo("");
    assertThat(setting.fromField()).isEqualTo("smugglingKey");
  }

  @Test
  void a_declared_plain_field_this_account_never_configured_is_reported_as_not_configured() {
    PairingScreen.Setting setting = settingOf("THE_CAPTURE_WINDOW", "captureWindow");

    assertThat(setting.shown()).isEqualTo(PairingScreen.Shown.NOT_CONFIGURED);
    assertThat(setting.value()).isEqualTo("");
    assertThat(setting.fromField()).isEqualTo("captureWindow");
  }

  @Test
  void the_settings_this_screen_resolves_itself_carry_no_field_of_any_provider() {
    PairingScreen.Setting base = resolved(new PairingValue.TheProviderBaseUrl());
    PairingScreen.Setting run = resolved(new PairingValue.TheTestRunId());
    PairingScreen.Setting header = resolved(new PairingValue.TheTestRunHeader());
    PairingScreen.Setting account = resolved(new PairingValue.TheAccountId());
    PairingScreen.Setting given = resolved(new PairingValue.Given("application/json"));

    assertThat(base.value()).isEqualTo("https://simulator.internal/v2");
    assertThat(run.value()).isEqualTo("e2e run 7");
    assertThat(header.value()).isEqualTo("X-Simulator-Test-Run-Id");
    assertThat(account.value()).isEqualTo("merchant-1");
    assertThat(given.value()).isEqualTo("application/json");
    assertThat(List.of(base, run, header, account, given))
        .allSatisfy(setting -> assertThat(setting.fromField()).isEqualTo(""))
        .allSatisfy(setting -> assertThat(setting.shown()).isEqualTo(PairingScreen.Shown.VALUE));
  }

  @Test
  void an_address_of_this_simulator_is_its_public_base_url_at_the_path_the_provider_names() {
    PairingScreen.Setting host =
        resolved(new PairingValue.AnAddressOfThisSimulator("/tranzila/directng"));

    assertThat(host.value()).isEqualTo("https://simulator.internal/tranzila/directng");
    assertThat(host.shown()).isEqualTo(PairingScreen.Shown.VALUE);
    assertThat(host.fromField()).isEqualTo("");
  }

  @Test
  void
      an_address_of_this_simulator_is_refused_unless_its_path_is_absolute_with_no_trailing_slash() {
    assertThatThrownBy(() -> new PairingValue.AnAddressOfThisSimulator("tranzila/directng"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tranzila/directng is not one");
    assertThatThrownBy(() -> new PairingValue.AnAddressOfThisSimulator("/tranzila/directng/"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("/tranzila/directng/ is not one");
  }

  @Test
  void the_pairing_payload_is_this_screens_own_url_and_names_the_provider_and_the_test_run() {
    assertThat(PairingScreen.qrPayloadOf("https://simulator.internal", "grow", "e2e-run-123"))
        .isEqualTo("https://simulator.internal/ui/pairing?provider=grow&testRunId=e2e-run-123");
  }

  @Test
  void a_test_run_carrying_a_character_a_query_string_reserves_is_encoded_into_the_payload() {
    assertThat(PairingScreen.qrPayloadOf("https://simulator.internal/", "grow", "a&b c"))
        .isEqualTo("https://simulator.internal/ui/pairing?provider=grow&testRunId=a%26b+c");
  }

  @Test
  void the_arriving_traffic_link_asks_the_trail_for_the_origin_an_api_request_is_filed_under() {
    assertThat(PairingScreen.arrivingTrafficOf("worldline", "e2e-run-123"))
        .isEqualTo("/ui/audit?provider=worldline&testRunId=e2e-run-123&origin=PROVIDER_API");
    assertThat(PairingScreen.TRAFFIC_THAT_ARRIVED_FROM_AN_APPLICATION.name())
        .isEqualTo("PROVIDER_API");
  }

  @Test
  void the_view_carries_one_snippet_for_every_account_of_the_run_in_the_order_it_was_given() {
    PairingScreen.View view =
        PairingScreen.of(
            "worldline",
            RUN,
            BASE_URL,
            pairing(new PairingSetting("WORLDLINE_MERCHANT_ID", field("merchantId"))),
            FIELDS,
            List.of("merchant-1", "merchant-2"),
            PairingScreenTest::disclosureOf,
            List.of(),
            List.of());

    assertThat(view.snippets()).hasSize(2);
    assertThat(view.snippets().get(0).accountId()).isEqualTo("merchant-1");
    assertThat(view.snippets().get(1).accountId()).isEqualTo("merchant-2");
    assertThat(view.snippets().get(0).settings().get(0).value()).isEqualTo("PSPID-4412");
    assertThat(view.snippets().get(1).settings().get(0).shown())
        .isEqualTo(PairingScreen.Shown.NOT_CONFIGURED);
  }

  @Test
  void the_view_publishes_the_base_url_the_path_the_header_and_the_sentence_the_provider_wrote() {
    PairingScreen.View view = view(List.of(), List.of());

    assertThat(view.provider()).isEqualTo("worldline");
    assertThat(view.testRunId()).isEqualTo("e2e run 7");
    assertThat(view.publicBaseUrl()).isEqualTo("https://simulator.internal");
    assertThat(view.providerBaseUrl()).isEqualTo("https://simulator.internal/v2");
    assertThat(view.testRunHeader()).isEqualTo("X-Simulator-Test-Run-Id");
    assertThat(view.howARequestNamesItsAccount()).isEqualTo("it carries a signed header");
    assertThat(view.suggestedRouteAliasTarget()).isEqualTo("/v2");
    assertThat(view.qrPayload())
        .isEqualTo("https://simulator.internal/ui/pairing?provider=worldline&testRunId=e2e+run+7");
    assertThat(view.arrivingTraffic())
        .isEqualTo("/ui/audit?provider=worldline&testRunId=e2e+run+7&origin=PROVIDER_API");
  }

  @Test
  void every_callback_target_and_every_url_the_run_answers_on_reaches_the_view() {
    PairingScreen.View view =
        view(
            List.of(new WebhookTarget("merchant-1", "https://merchant.test/callbacks")),
            List.of(
                new RouteAlias(
                    PROVIDER,
                    TestRunId.of("e2e-run-123"),
                    new RouteAliasKey("api.provider.example", "/v2/PSPID-4412/payments"),
                    "https://api.provider.example/v2/PSPID-4412/payments",
                    "/v2/PSPID-4412/payments")));

    assertThat(view.callbackTargets())
        .containsExactly(new PairingScreen.Target("merchant-1", "https://merchant.test/callbacks"));
    assertThat(view.routeAliases())
        .containsExactly(
            new PairingScreen.Alias(
                "https://api.provider.example/v2/PSPID-4412/payments", "/v2/PSPID-4412/payments"));
  }

  @Test
  void an_account_the_provider_holds_no_row_for_still_names_every_setting_it_could_not_fill() {
    PairingScreen.View view =
        PairingScreen.of(
            "worldline",
            RUN,
            BASE_URL,
            pairing(
                new PairingSetting("WORLDLINE_MERCHANT_ID", field("merchantId")),
                new PairingSetting("WORLDLINE_API_SECRET", field("apiSecret"))),
            FIELDS,
            List.of("forgotten"),
            accountId -> Optional.empty(),
            List.of(),
            List.of());

    PairingScreen.Snippet snippet = view.snippets().get(0);
    assertThat(snippet.settings().get(0).shown()).isEqualTo(PairingScreen.Shown.NOT_CONFIGURED);
    assertThat(snippet.settings().get(1).shown()).isEqualTo(PairingScreen.Shown.WITHHELD_AS_SECRET);
    assertThat(snippet.settings()).allSatisfy(setting -> assertThat(setting.value()).isEqualTo(""));
  }

  private PairingScreen.View view(List<WebhookTarget> targets, List<RouteAlias> aliases) {
    return PairingScreen.of(
        "worldline",
        RUN,
        BASE_URL,
        pairing(new PairingSetting("WORLDLINE_BASE_URL", new PairingValue.TheProviderBaseUrl())),
        FIELDS,
        List.of(ACCOUNT),
        PairingScreenTest::disclosureOf,
        targets,
        aliases);
  }

  private static PairingScreen.Setting settingOf(String name, String key) {
    return PairingScreen.settingOf(
        new PairingSetting(name, field(key)),
        FIELDS,
        "https://simulator.internal/",
        "https://simulator.internal/v2",
        RUN,
        ACCOUNT,
        configured());
  }

  private static PairingScreen.Setting resolved(PairingValue value) {
    return PairingScreen.settingOf(
        new PairingSetting("A_SETTING", value),
        FIELDS,
        "https://simulator.internal/",
        "https://simulator.internal/v2",
        RUN,
        ACCOUNT,
        configured());
  }

  private static PairingValue field(String key) {
    return new PairingValue.FromAccountField(key);
  }

  private static ProviderPairing pairing(PairingSetting... settings) {
    return new ProviderPairing(PROVIDER, "/v2", "it carries a signed header", List.of(settings));
  }

  private static Optional<DisclosedAccount> disclosureOf(String accountId) {
    if (!ACCOUNT.equals(accountId)) {
      return Optional.of(new DisclosedAccount(accountId, Map.of()));
    }
    return Optional.of(new DisclosedAccount(accountId, configured()));
  }

  private static Map<String, List<String>> configured() {
    Map<String, List<String>> disclosed = new LinkedHashMap<>();
    disclosed.put("merchantId", List.of(THE_MERCHANT_ID_A_TESTER_MAY_READ));
    disclosed.put("apiSecret", List.of(THE_SECRET_NOBODY_MAY_READ));
    disclosed.put("smugglingKey", List.of(THE_SECRET_NOBODY_MAY_READ));
    disclosed.put("captureWindow", List.of());
    return Map.copyOf(disclosed);
  }
}
