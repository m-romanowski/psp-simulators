package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class ProviderAccountFieldsIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("provider-account-fields-it");

  private static final String ACCOUNT = "merchant-1";

  private static final KeyPair MERCHANT_KEYS = rsa();
  private static final KeyPair GATEWAY_KEYS = rsa();

  private static final Map<String, Map<String, String>> A_VALUE_FOR_EVERY_FIELD =
      Map.of(
          "autra",
          Map.ofEntries(
              Map.entry("clientId", "autra-fields-client"),
              Map.entry("clientSecret", "autra-fields-secret"),
              Map.entry("documentId", "25055662000190"),
              Map.entry("autra.oauth-failure", "BAD_REQUEST_WITH_ERROR_STRING"),
              Map.entry("autra.oauth-field-casing", "CAMEL_CASE"),
              Map.entry("autra.pre-authorization-request-shape", "PUBLISHED_SCHEMA"),
              Map.entry("autra.void-request-shape", "PUBLISHED_SCHEMA"),
              Map.entry("autra.authorizer-rejected-message", "05 - Do not honor"),
              Map.entry("autra.merchant-not-found-status", "NOT_FOUND"),
              Map.entry("autra.sale-decline-status", "TWO_HUNDRED_WITH_ERRORS"),
              Map.entry("autra.acquirer-transaction-identifier", "NOT_REPORTED"),
              Map.entry("webhookSecret", "autra-webhook-secret-of-thirty-two-plus")),
          "grow",
          Map.ofEntries(
              Map.entry("userId", "fields-user"),
              Map.entry("pageCode", "fields-page"),
              Map.entry("callbackAcknowledgement", "HTTP_2XX"),
              Map.entry("settlementCeiling", "FRAME_CAPTURE"),
              Map.entry("refundAllowance", "ONE_REFUND"),
              Map.entry("refundExcessRejection", "ORIGINAL_AMOUNT"),
              Map.entry("merchantBalance", "AVAILABLE"),
              Map.entry("processLookupRejection", "PUBLISHED_EXAMPLE"),
              Map.entry("wireForm", "LIVE_WIRE"),
              Map.entry("captureWindow", "PT168H"),
              Map.entry("automaticVoidAfter", "PT240H")),
          "azericard",
          Map.of(
              "terminalId",
              "7777",
              "apiKey",
              pem("PUBLIC KEY", MERCHANT_KEYS.getPublic().getEncoded()),
              "apiSecret",
              pem("PRIVATE KEY", GATEWAY_KEYS.getPrivate().getEncoded()),
              "azericard.duplicate-response-code",
              "-1",
              "azericard.partial-completion",
              "CLOSES_THE_AUTHORIZATION",
              "azericard.callback-signature",
              "VALID",
              "storedCardSourceTerminalIds",
              "7778",
              "azericard.settlement-mac",
              "LIVE_WIRE"),
          "stripe",
          Map.of(
              "secretKey",
              "sk_test_provider_account_fields",
              "captureWindow",
              "P3D",
              "refundStatus",
              "SUCCEEDED",
              "webhookSecret",
              "whsec_provider_account_fields",
              "webhookSignature",
              "VALID",
              "webhookTimestampAge",
              "PT0S"),
          "worldline",
          Map.of(
              "merchantId",
              "pspid-fields",
              "apiKey",
              "worldline-fields-key",
              "apiSecret",
              "worldline-fields-secret",
              "worldline.signed-path-version",
              "V2_AS_THE_SCHEMA_AND_THE_PROPERTY_TABLE_PUBLISH_IT",
              "worldline.capture-landing",
              "CAPTURED_9",
              "worldline.authentication",
              "THE_SIGNATURE_IS_VERIFIED"));

  private static final Map<String, Map.Entry<String, String>> A_VALUE_NO_ACCOUNT_MAY_HOLD =
      Map.of(
          "autra",
          Map.entry("autra.oauth-failure", "WHEN_THE_MOON_IS_FULL"),
          "grow",
          Map.entry("callbackAcknowledgement", "WHEN_THE_MOON_IS_FULL"),
          "azericard",
          Map.entry("azericard.partial-completion", "WHEN_THE_MOON_IS_FULL"),
          "stripe",
          Map.entry("refundStatus", "WHEN_THE_MOON_IS_FULL"),
          "worldline",
          Map.entry("worldline.capture-landing", "WHEN_THE_MOON_IS_FULL"));

  private static final Map<String, Integer> FIELDS_EACH_PROVIDER_REQUIRES =
      Map.of("autra", 3, "grow", 2, "azericard", 3, "stripe", 2, "worldline", 3);

  @Autowired private ProviderRegistry providers;
  @Autowired private ConfigurationAdmission configurations;
  @Autowired private JdbcClient jdbc;

  static Stream<String> everyProviderThisTestCarriesValuesFor() {
    return A_VALUE_FOR_EVERY_FIELD.keySet().stream().sorted();
  }

  @AfterEach
  void clearWhatThisTestWrote() {
    jdbc.sql("delete from simulator_provider_account where test_run_id = ?")
        .param(RUN.value())
        .update();
    jdbc.sql("delete from simulator_webhook_target where test_run_id = ?")
        .param(RUN.value())
        .update();
    for (String table :
        List.of(
            "autra_merchant",
            "grow_account",
            "azericard_terminal",
            "stripe_account",
            "worldline_merchant")) {
      jdbc.sql("delete from " + table + " where test_run_id = ?").param(RUN.value()).update();
    }
  }

  @Test
  void every_module_this_simulator_serves_is_one_this_test_configures_an_account_of() {
    assertThat(providers.names())
        .containsExactlyInAnyOrderElementsOf(A_VALUE_FOR_EVERY_FIELD.keySet());
    assertThat(providers.names())
        .containsExactlyInAnyOrderElementsOf(A_VALUE_NO_ACCOUNT_MAY_HOLD.keySet());
  }

  @Test
  void the_field_this_test_spoils_is_one_its_provider_declares_and_never_a_secret_one() {
    for (Map.Entry<String, Map.Entry<String, String>> spoiled :
        A_VALUE_NO_ACCOUNT_MAY_HOLD.entrySet()) {
      ProviderAccountFields declared = moduleNamed(spoiled.getKey()).accountFields();
      assertThat(declared.keys()).contains(spoiled.getValue().getKey());
      assertThat(declared.secretKeys()).doesNotContain(spoiled.getValue().getKey());
    }
  }

  @ParameterizedTest(name = "{0} declares every field this test configures")
  @MethodSource("everyProviderThisTestCarriesValuesFor")
  void every_field_a_provider_publishes_is_one_this_test_configures_a_value_for(String provider) {
    assertThat(A_VALUE_FOR_EVERY_FIELD.get(provider).keySet())
        .containsExactlyInAnyOrderElementsOf(moduleNamed(provider).accountFields().keys());
  }

  @ParameterizedTest(name = "{0} redacts every field it calls secret")
  @MethodSource("everyProviderThisTestCarriesValuesFor")
  void every_field_a_provider_calls_secret_is_redacted_by_its_own_disclosure(String provider) {
    SimulatorProvider module = moduleNamed(provider);

    assertThat(theKeysItRedacts(module)).containsAll(module.accountFields().secretKeys());
  }

  @ParameterizedTest(name = "{0} calls every field it redacts secret")
  @MethodSource("everyProviderThisTestCarriesValuesFor")
  void every_field_a_providers_disclosure_redacts_is_one_it_calls_secret(String provider) {
    SimulatorProvider module = moduleNamed(provider);

    assertThat(module.accountFields().secretKeys()).containsAll(theKeysItRedacts(module));
  }

  @ParameterizedTest(name = "{0} discloses a value for every field it declares")
  @MethodSource("everyProviderThisTestCarriesValuesFor")
  void a_field_a_provider_publishes_is_one_its_disclosure_answers_for(String provider) {
    SimulatorProvider module = moduleNamed(provider);

    assertThat(configureTheAccountOf(module).disclosed().keySet())
        .containsExactlyInAnyOrderElementsOf(module.accountFields().keys());
  }

  @ParameterizedTest(name = "{0} names the provider its fields belong to")
  @MethodSource("everyProviderThisTestCarriesValuesFor")
  void the_fields_a_provider_publishes_name_that_provider_and_not_another(String provider) {
    SimulatorProvider module = moduleNamed(provider);

    assertThat(module.accountFields().provider()).isEqualTo(module.id());
  }

  @ParameterizedTest(name = "{0} labels every field it declares")
  @MethodSource("everyProviderThisTestCarriesValuesFor")
  void no_field_a_provider_publishes_asks_a_tester_to_read_its_key(String provider) {
    assertThat(moduleNamed(provider).accountFields().fields())
        .allSatisfy(field -> assertThat(field.label()).isNotEqualTo(field.key()));
  }

  @ParameterizedTest(name = "{0} hints a value it would take in every box it draws")
  @MethodSource("everyProviderThisTestCarriesValuesFor")
  void every_box_a_provider_draws_hints_a_value_that_provider_itself_takes(String provider) {
    SimulatorProvider module = moduleNamed(provider);
    Map<String, String> configuration = new LinkedHashMap<>(A_VALUE_FOR_EVERY_FIELD.get(provider));
    List<String> boxes = new ArrayList<>();

    for (ConfigurableField field : module.accountFields().fields()) {
      if (!field.acceptedValues().isEmpty()) {
        continue;
      }
      boxes.add(field.key());
      String hint = field.example();
      assertThat(hint)
          .as(
              "provider %s draws %s as a box a tester types a value into, and a box with no"
                  + " example of its own could hint nothing but that key",
              provider, field.key())
          .isNotNull()
          .isNotEqualTo(field.key());
      if (field.holdsABlockOfManyLines()) {
        String whole = A_VALUE_FOR_EVERY_FIELD.get(provider).get(field.key());
        assertThat(whole)
            .as(
                "%s of provider %s holds a block of many lines, so what the box hints is the line"
                    + " that block begins with and the whole value is what this test pastes in",
                field.key(), provider)
            .startsWith(String.valueOf(hint));
        continue;
      }
      configuration.put(field.key(), String.valueOf(hint));
    }

    assertThat(boxes)
        .as("provider %s draws no box at all, so this test measured nothing", provider)
        .isNotEmpty();
    assertThat(configure(module, configuration))
        .as(
            "an account of provider %s configured from the hints its own boxes %s show; a hint"
                + " this provider would refuse is a value a tester reads off the screen, types"
                + " in and is refused for",
            provider, boxes)
        .isInstanceOf(ConfigurationAdmission.Verdict.Applied.class);
  }

  @ParameterizedTest(name = "{0} requires the fields a request of it cannot be made without")
  @MethodSource("everyProviderThisTestCarriesValuesFor")
  void a_provider_requires_only_the_fields_a_request_of_it_cannot_be_made_without(String provider) {
    List<ConfigurableField> fields = moduleNamed(provider).accountFields().fields();
    long required = fields.stream().filter(ConfigurableField::required).count();

    assertThat(required)
        .as(
            "provider %s requires %s of its %s account fields; a form marks the required ones and"
                + " leaves the rest unmarked, so a count that moves without anyone saying why"
                + " changes what a tester reads on every one of them",
            provider, required, fields.size())
        .isEqualTo(FIELDS_EACH_PROVIDER_REQUIRES.get(provider).longValue());
  }

  @ParameterizedTest(name = "{0} gives every field a name and a sentence of its own")
  @MethodSource("everyProviderThisTestCarriesValuesFor")
  void no_two_fields_of_a_provider_share_a_name_or_a_sentence(String provider) {
    List<ConfigurableField> fields = moduleNamed(provider).accountFields().fields();

    assertThat(fields).extracting(ConfigurableField::label).doesNotHaveDuplicates();
    assertThat(fields).extracting(ConfigurableField::whatItIsFor).doesNotHaveDuplicates();
  }

  @ParameterizedTest(name = "{0} repeats no secret in any refusal of one configuration")
  @MethodSource("everyProviderThisTestCarriesValuesFor")
  void a_refusal_repeats_no_secret_the_same_configuration_carried(String provider) {
    SimulatorProvider module = moduleNamed(provider);
    Map<String, String> spoiled = new LinkedHashMap<>(markedSecretsOf(module));
    Map.Entry<String, String> refusable = A_VALUE_NO_ACCOUNT_MAY_HOLD.get(provider);
    spoiled.put(refusable.getKey(), refusable.getValue());

    ConfigurationAdmission.Verdict verdict = configure(module, spoiled);

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ConfigurationAdmission.Verdict.Refused.class,
            refused -> {
              assertThat(module.accountFields().keys())
                  .as("a refusal of a configured value names a field the provider declares")
                  .contains(refused.refusal().field());
              assertThat(refused.refusal().message()).doesNotContain(theMarkersOf(module));
            });
  }

  @ParameterizedTest(name = "{0} refuses a value no account may hold by naming that field")
  @MethodSource("everyProviderThisTestCarriesValuesFor")
  void the_value_this_test_spoils_a_field_with_is_one_that_provider_refuses(String provider) {
    SimulatorProvider module = moduleNamed(provider);
    Map<String, String> spoiled = new LinkedHashMap<>(A_VALUE_FOR_EVERY_FIELD.get(provider));
    Map.Entry<String, String> refusable = A_VALUE_NO_ACCOUNT_MAY_HOLD.get(provider);
    spoiled.put(refusable.getKey(), refusable.getValue());

    assertThat(configure(module, spoiled))
        .isInstanceOfSatisfying(
            ConfigurationAdmission.Verdict.Refused.class,
            refused -> {
              assertThat(refused.refusal().field()).isEqualTo(refusable.getKey());
              assertThat(refused.refusal().message()).doesNotContain(refusable.getValue());
            });
  }

  @ParameterizedTest(name = "{0} never hands back a secret it was sent")
  @MethodSource("everyProviderThisTestCarriesValuesFor")
  void a_secret_this_provider_was_sent_reaches_neither_a_refusal_nor_a_disclosure(String provider) {
    SimulatorProvider module = moduleNamed(provider);

    for (String secret : module.accountFields().secretKeys()) {
      Map<String, String> sent = new LinkedHashMap<>(A_VALUE_FOR_EVERY_FIELD.get(provider));
      String marker = aMarkerFor(secret);
      sent.put(secret, marker);

      switch (configure(module, sent)) {
        case ConfigurationAdmission.Verdict.Refused refused ->
            assertThat(refused.refusal().message())
                .as("provider %s refusing %s", provider, secret)
                .doesNotContain(marker);
        case ConfigurationAdmission.Verdict.Conflicting conflicting ->
            assertThat(conflicting.refusal().message())
                .as("provider %s refusing %s", provider, secret)
                .doesNotContain(marker);
        case ConfigurationAdmission.Verdict.Rejected rejected ->
            assertThat(rejected.message())
                .as("provider %s rejecting %s", provider, secret)
                .doesNotContain(marker);
        case ConfigurationAdmission.Verdict.Applied _ ->
            assertThat(module.disclosureOf(RUN, ACCOUNT).orElseThrow().disclosed().get(secret))
                .as("provider %s disclosing %s", provider, secret)
                .containsExactly(Redaction.REDACTED);
      }
      clearWhatThisTestWrote();
    }
  }

  @ParameterizedTest(name = "{0} keeps every secret it was sent out of the audit trail")
  @MethodSource("everyProviderThisTestCarriesValuesFor")
  void a_secret_this_provider_was_sent_never_reaches_the_row_the_action_writes(String provider) {
    SimulatorProvider module = moduleNamed(provider);
    Map<String, String> marked = markedSecretsOf(module);

    Map<String, List<String>> recorded =
        ConfigurationFacts.of(
            module.accountFields(),
            new DeclaredConfiguration(
                RUN.value(),
                null,
                List.of(new DeclaredAccount(ACCOUNT, marked)),
                null,
                null,
                null));

    for (String secret : module.accountFields().secretKeys()) {
      assertThat(recorded.get("accounts[0].configuration." + secret))
          .as("provider %s auditing %s", provider, secret)
          .containsExactly(Redaction.REDACTED);
    }
    assertThat(String.valueOf(recorded)).doesNotContain(theMarkersOf(module));
    assertThat(recorded).containsEntry("accounts[0].accountId", List.of(ACCOUNT));
  }

  @ParameterizedTest(name = "{0} withholds a field it never declared rather than printing it")
  @MethodSource("everyProviderThisTestCarriesValuesFor")
  void a_field_this_provider_never_declared_is_withheld_rather_than_printed(String provider) {
    SimulatorProvider module = moduleNamed(provider);
    String undeclared = "aMerchantSecretNoProviderDeclares";

    Map<String, List<String>> recorded =
        ConfigurationFacts.of(
            module.accountFields(),
            new DeclaredConfiguration(
                RUN.value(),
                null,
                List.of(new DeclaredAccount(ACCOUNT, Map.of(undeclared, aMarkerFor(undeclared)))),
                null,
                null,
                null));

    assertThat(recorded.get("accounts[0].configuration." + undeclared))
        .containsExactly(Redaction.REDACTED);
  }

  private Map<String, String> markedSecretsOf(SimulatorProvider module) {
    Map<String, String> marked =
        new LinkedHashMap<>(A_VALUE_FOR_EVERY_FIELD.get(module.id().value()));
    for (String secret : module.accountFields().secretKeys()) {
      marked.put(secret, aMarkerFor(secret));
    }
    return marked;
  }

  private static String[] theMarkersOf(SimulatorProvider module) {
    List<String> markers = new ArrayList<>();
    for (String secret : module.accountFields().secretKeys()) {
      markers.add(aMarkerFor(secret));
    }
    assertThat(markers).isNotEmpty();
    return markers.toArray(new String[0]);
  }

  private static String aMarkerFor(String field) {
    return "LEAKED-" + field + "-MUST-NEVER-COME-BACK";
  }

  private ConfigurationAdmission.Verdict configure(
      SimulatorProvider module, Map<String, String> configuration) {
    return configurations.apply(
        module,
        new DeclaredConfiguration(
            RUN.value(),
            null,
            List.of(new DeclaredAccount(ACCOUNT, Map.copyOf(configuration))),
            null,
            null,
            null));
  }

  private SimulatorProvider moduleNamed(String provider) {
    return providers.find(provider).orElseThrow();
  }

  private Set<String> theKeysItRedacts(SimulatorProvider module) {
    DisclosedAccount disclosed = configureTheAccountOf(module);
    Set<String> redacted = new LinkedHashSet<>();
    for (Map.Entry<String, List<String>> field : disclosed.disclosed().entrySet()) {
      if (field.getValue().contains(Redaction.REDACTED)) {
        redacted.add(field.getKey());
      }
    }
    return Set.copyOf(redacted);
  }

  private DisclosedAccount configureTheAccountOf(SimulatorProvider module) {
    ConfigurationAdmission.Verdict verdict =
        configurations.apply(
            module,
            new DeclaredConfiguration(
                RUN.value(),
                null,
                List.of(
                    new DeclaredAccount(ACCOUNT, A_VALUE_FOR_EVERY_FIELD.get(module.id().value()))),
                null,
                null,
                null));

    assertThat(verdict).isInstanceOf(ConfigurationAdmission.Verdict.Applied.class);
    return module.disclosureOf(RUN, ACCOUNT).orElseThrow();
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
