package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import dev.marcinromanowski.pspsim.kernel.spi.ConfiguredAccount;
import dev.marcinromanowski.pspsim.kernel.spi.DisclosedAccount;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurationRejectedException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TranzilaTerminalTest {

  private static final String PREFIX = "accounts[0].configuration.";

  @Test
  void an_account_naming_only_its_credentials_takes_every_default_the_contract_settles() {
    TranzilaTerminal terminal = of(Map.of());

    assertThat(terminal.terminalName()).isEqualTo("myterminal");
    assertThat(terminal.appKey()).isEqualTo("key");
    assertThat(terminal.appSecret()).isEqualTo("secret");
    assertThat(terminal.accountId()).isEqualTo("account-1");
    assertThat(terminal.testRunId()).isEqualTo(TranzilaWiring.RUN);
    assertThat(terminal.accessToken())
        .isEqualTo(TranzilaAccessToken.AS_THE_AUTHENTICATION_PAGES_PUBLISH_IT);
    assertThat(terminal.authentication())
        .isEqualTo(TranzilaAuthentication.THE_ACCESS_TOKEN_IS_VERIFIED);
    assertThat(terminal.verificationApprovalCode().code()).isEqualTo("777");
    assertThat(terminal.j5Validity()).isEqualTo(Duration.ofDays(7));
  }

  @Test
  void an_account_takes_every_value_each_setting_offers() {
    TranzilaTerminal terminal =
        of(
            Map.of(
                TranzilaAccessToken.SETTING, "AS_THE_QUICKSTART_SIGNS_IT",
                TranzilaAuthentication.SETTING, "THE_ACCESS_TOKEN_IS_NOT_VERIFIED",
                TranzilaVerificationApprovalCode.SETTING, "000",
                TranzilaJ5Validity.SETTING, "P1DT2H"));

    assertThat(terminal.accessToken()).isEqualTo(TranzilaAccessToken.AS_THE_QUICKSTART_SIGNS_IT);
    assertThat(terminal.authentication().verifiesTheAccessToken()).isFalse();
    assertThat(TranzilaAuthentication.THE_ACCESS_TOKEN_IS_VERIFIED.verifiesTheAccessToken())
        .isTrue();
    assertThat(terminal.verificationApprovalCode())
        .isEqualTo(TranzilaVerificationApprovalCode.AS_THE_VENDOR_ANSWERED_IT);
    assertThat(terminal.j5Validity()).isEqualTo(Duration.ofHours(26));
  }

  @Test
  void a_blank_setting_takes_the_default_as_an_absent_one_does() {
    TranzilaTerminal terminal =
        of(
            Map.of(
                TranzilaAccessToken.SETTING, " ",
                TranzilaAuthentication.SETTING, " ",
                TranzilaVerificationApprovalCode.SETTING, " ",
                TranzilaJ5Validity.SETTING, " "));

    assertThat(terminal.accessToken())
        .isEqualTo(TranzilaAccessToken.AS_THE_AUTHENTICATION_PAGES_PUBLISH_IT);
    assertThat(terminal.authentication())
        .isEqualTo(TranzilaAuthentication.THE_ACCESS_TOKEN_IS_VERIFIED);
    assertThat(terminal.verificationApprovalCode().code()).isEqualTo("777");
    assertThat(terminal.j5Validity()).isEqualTo(Duration.ofDays(7));
  }

  @Test
  void each_credential_is_required_and_refused_in_its_own_words() {
    assertThatThrownBy(() -> without(TranzilaTerminal.TERMINAL_NAME_PROPERTY))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessage(PREFIX + TranzilaTerminal.TERMINAL_NAME_IS_IN_EVERY_BODY);
    assertThatThrownBy(() -> without(TranzilaTerminal.APP_KEY_PROPERTY))
        .hasMessage(PREFIX + TranzilaTerminal.APP_KEY_IS_THE_ACCOUNT);
    assertThatThrownBy(() -> without(TranzilaTerminal.APP_SECRET_PROPERTY))
        .hasMessage(PREFIX + TranzilaTerminal.APP_SECRET_IS_WHAT_THE_TOKEN_IS_MADE_WITH);
  }

  @Test
  void a_blank_credential_is_refused_as_a_missing_one() {
    Map<String, String> configuration = credentials();
    configuration.put(TranzilaTerminal.APP_KEY_PROPERTY, " ");

    assertThatThrownBy(() -> parse(configuration))
        .hasMessage(PREFIX + TranzilaTerminal.APP_KEY_IS_THE_ACCOUNT);
  }

  @Test
  void a_terminal_name_outside_the_published_shape_is_refused() {
    for (String refused : List.of("my", "1terminal", "MyTerminal", "a".repeat(17), "my-terminal")) {
      Map<String, String> configuration = credentials();
      configuration.put(TranzilaTerminal.TERMINAL_NAME_PROPERTY, refused);

      assertThatThrownBy(() -> parse(configuration))
          .hasMessage(PREFIX + TranzilaTerminal.TERMINAL_NAME_IS_THE_PUBLISHED_SHAPE);
    }
    Map<String, String> sixteen = credentials();
    sixteen.put(TranzilaTerminal.TERMINAL_NAME_PROPERTY, "a" + "1".repeat(15));
    assertThat(parse(sixteen).terminalName()).hasSize(16);
  }

  @Test
  void a_setting_naming_no_value_it_offers_is_refused_naming_the_values_it_does() {
    assertThatThrownBy(() -> of(Map.of(TranzilaAccessToken.SETTING, "HMAC")))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageStartingWith(PREFIX + TranzilaAccessToken.SETTING + " names neither")
        .hasMessageEndingWith(
            "The two values are AS_THE_AUTHENTICATION_PAGES_PUBLISH_IT, the default, and"
                + " AS_THE_QUICKSTART_SIGNS_IT");
    assertThatThrownBy(() -> of(Map.of(TranzilaAuthentication.SETTING, "OFF")))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageEndingWith(
            "The two values are THE_ACCESS_TOKEN_IS_VERIFIED, the default, and"
                + " THE_ACCESS_TOKEN_IS_NOT_VERIFIED");
    assertThatThrownBy(() -> of(Map.of(TranzilaVerificationApprovalCode.SETTING, "001")))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageEndingWith("The two values are 777, the default, and 000");
  }

  @Test
  void a_j5_validity_that_no_hold_could_outlive_is_refused_in_its_own_words() {
    assertThatThrownBy(() -> of(Map.of(TranzilaJ5Validity.SETTING, "seven days")))
        .hasMessageStartingWith(PREFIX + "tranzila.j5-validity must name a span of time");
    assertThatThrownBy(() -> of(Map.of(TranzilaJ5Validity.SETTING, "PT0S")))
        .hasMessageStartingWith(PREFIX + "tranzila.j5-validity must be longer than nothing");
    assertThatThrownBy(() -> of(Map.of(TranzilaJ5Validity.SETTING, "-PT1H")))
        .hasMessageStartingWith(PREFIX + "tranzila.j5-validity must be longer than nothing");
    assertThatThrownBy(() -> of(Map.of(TranzilaJ5Validity.SETTING, "P366D")))
        .hasMessageStartingWith(PREFIX + "tranzila.j5-validity must be at most 365 days");
    assertThatThrownBy(() -> of(Map.of(TranzilaJ5Validity.SETTING, "PT0.0000001S")))
        .hasMessageStartingWith(
            PREFIX + "tranzila.j5-validity must be a whole number of microseconds");
    assertThat(of(Map.of(TranzilaJ5Validity.SETTING, "P365D")).j5Validity())
        .isEqualTo(Duration.ofDays(365));
    assertThat(of(Map.of(TranzilaJ5Validity.SETTING, "PT0.000001S")).j5Validity())
        .isEqualTo(Duration.ofNanos(1000));
  }

  @Test
  void the_disclosure_names_every_setting_and_withholds_the_secret() {
    DisclosedAccount disclosed =
        of(Map.of(TranzilaVerificationApprovalCode.SETTING, "000")).disclosure();

    assertThat(disclosed.accountId()).isEqualTo("account-1");
    assertThat(disclosed.disclosed())
        .containsExactly(
            Map.entry("terminalName", List.of("myterminal")),
            Map.entry("appKey", List.of("key")),
            Map.entry("appSecret", List.of("[redacted]")),
            Map.entry("tranzila.access-token", List.of("AS_THE_AUTHENTICATION_PAGES_PUBLISH_IT")),
            Map.entry("tranzila.authentication", List.of("THE_ACCESS_TOKEN_IS_VERIFIED")),
            Map.entry("tranzila.verification-approval-code", List.of("000")),
            Map.entry("tranzila.j5-validity", List.of("PT168H")));
  }

  @Test
  void the_account_fields_name_the_secret_as_secret_and_nothing_else() {
    assertThat(TranzilaTerminal.ACCOUNT_FIELDS.secretKeys()).containsExactly("appSecret");
    assertThat(TranzilaTerminal.CONFIGURATION_KEYS)
        .containsExactlyInAnyOrder(
            "terminalName",
            "appKey",
            "appSecret",
            "tranzila.access-token",
            "tranzila.authentication",
            "tranzila.verification-approval-code",
            "tranzila.j5-validity");
  }

  @Test
  void every_setting_offers_its_values_with_words_a_tester_reads() {
    assertThat(TranzilaAccessToken.acceptedValues())
        .extracting(AcceptedValue::value)
        .containsExactly("AS_THE_AUTHENTICATION_PAGES_PUBLISH_IT", "AS_THE_QUICKSTART_SIGNS_IT");
    assertThat(TranzilaAuthentication.acceptedValues())
        .extracting(AcceptedValue::value)
        .containsExactly("THE_ACCESS_TOKEN_IS_VERIFIED", "THE_ACCESS_TOKEN_IS_NOT_VERIFIED");
    assertThat(TranzilaVerificationApprovalCode.acceptedValues())
        .extracting(AcceptedValue::value)
        .containsExactly("777", "000");
  }

  @Test
  void a_stored_approval_code_reads_back_and_one_no_value_names_is_a_broken_store() {
    assertThat(TranzilaVerificationApprovalCode.stored("000"))
        .isEqualTo(TranzilaVerificationApprovalCode.AS_THE_VENDOR_ANSWERED_IT);
    assertThat(TranzilaVerificationApprovalCode.stored("777"))
        .isEqualTo(TranzilaVerificationApprovalCode.AS_THE_CODE_TABLE_PUBLISHES_IT);
    assertThatThrownBy(() -> TranzilaVerificationApprovalCode.stored("001"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("001");
  }

  @Test
  void the_request_time_is_read_in_the_unit_each_construction_publishes() {
    assertThat(
            TranzilaAccessToken.AS_THE_AUTHENTICATION_PAGES_PUBLISH_IT.requestTimeIn("1700000000"))
        .contains(java.time.Instant.ofEpochSecond(1700000000L));
    assertThat(TranzilaAccessToken.AS_THE_QUICKSTART_SIGNS_IT.requestTimeIn("1700000000123"))
        .contains(java.time.Instant.ofEpochMilli(1700000000123L));
    assertThat(TranzilaAccessToken.AS_THE_QUICKSTART_SIGNS_IT.theUnitOfTheRequestTime())
        .isEqualTo("Unix milliseconds");
    assertThat(TranzilaAccessToken.AS_THE_AUTHENTICATION_PAGES_PUBLISH_IT.theUnitOfTheRequestTime())
        .isEqualTo("Unix seconds");
    assertThat(TranzilaAccessToken.wholeNumberIn("1234567890123456")).isEmpty();
    assertThat(TranzilaAccessToken.wholeNumberIn("123456789012345")).contains(123456789012345L);
    assertThat(TranzilaAccessToken.wholeNumberIn("")).isEmpty();
    assertThat(TranzilaAccessToken.wholeNumberIn("-1")).isEmpty();
  }

  private static TranzilaTerminal of(Map<String, String> settings) {
    Map<String, String> configuration = credentials();
    configuration.putAll(settings);
    return parse(configuration);
  }

  private static TranzilaTerminal without(String property) {
    Map<String, String> configuration = credentials();
    configuration.remove(property);
    return parse(configuration);
  }

  private static Map<String, String> credentials() {
    Map<String, String> configuration = new LinkedHashMap<>();
    configuration.put(TranzilaTerminal.TERMINAL_NAME_PROPERTY, "myterminal");
    configuration.put(TranzilaTerminal.APP_KEY_PROPERTY, "key");
    configuration.put(TranzilaTerminal.APP_SECRET_PROPERTY, "secret");
    return configuration;
  }

  private static TranzilaTerminal parse(Map<String, String> configuration) {
    return TranzilaTerminal.of(
        PREFIX, TranzilaWiring.RUN, new ConfiguredAccount("account-1", configuration));
  }
}
