package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderPairingTest {

  private static final ProviderId PROVIDER = ProviderId.of("worldline");

  private static final PairingSetting A_BASE_URL =
      new PairingSetting("BASE_URL", new PairingValue.TheProviderBaseUrl());

  @Test
  void a_base_path_that_is_not_a_path_of_this_simulator_is_refused_by_naming_the_provider() {
    assertThatThrownBy(() -> pairing("https://api.provider.example", A_BASE_URL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("worldline")
        .hasMessageContaining("must begin with a slash");
  }

  @Test
  void a_provider_that_says_nothing_about_how_a_request_names_its_account_is_refused() {
    List<PairingSetting> settings = List.of(A_BASE_URL);

    assertThatThrownBy(() -> new ProviderPairing(PROVIDER, "/v2", "   ", settings))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("in its own words");
  }

  @Test
  void a_provider_that_declares_no_pairing_setting_is_refused_rather_than_shown_empty() {
    assertThatThrownBy(() -> new ProviderPairing(PROVIDER, "/v2", "a bearer", List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("declares no pairing setting");
  }

  @Test
  void one_setting_name_declared_twice_is_refused_because_one_snippet_line_would_win() {
    PairingSetting fromAnAccountField =
        new PairingSetting("API_KEY", new PairingValue.FromAccountField("apiKey"));
    PairingSetting fromTheAccountId =
        new PairingSetting("API_KEY", new PairingValue.TheAccountId());

    assertThatThrownBy(() -> pairing("/v2", fromAnAccountField, fromTheAccountId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("API_KEY")
        .hasMessageContaining("declared twice");
  }

  @Test
  void a_pairing_names_the_account_fields_it_reads_and_names_nothing_it_resolves_itself() {
    ProviderPairing pairing =
        pairing(
            "/v2",
            A_BASE_URL,
            new PairingSetting("API_KEY", new PairingValue.FromAccountField("apiKey")),
            new PairingSetting("API_SECRET", new PairingValue.FromAccountField("apiSecret")),
            new PairingSetting("RUN", new PairingValue.TheTestRunId()));

    assertThat(pairing.accountFieldsItReads()).containsExactly("apiKey", "apiSecret");
  }

  @Test
  void a_setting_with_no_name_names_no_key_a_snippet_could_carry_a_value_against() {
    PairingValue theTestRunId = new PairingValue.TheTestRunId();

    assertThatThrownBy(() -> new PairingSetting(" ", theTestRunId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("names nothing a tester could set");
  }

  @Test
  void a_literal_with_no_text_and_a_field_with_no_key_are_both_refused() {
    assertThatThrownBy(() -> new PairingValue.Given(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("configure nothing");
    assertThatThrownBy(() -> new PairingValue.FromAccountField(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("names no field of any provider");
  }

  @Test
  void a_provider_base_url_is_the_public_base_url_and_the_path_that_provider_answers_on() {
    ProviderPairing pairing = pairing("/v2", A_BASE_URL);

    assertThat(pairing.providerBaseUrlWithin("https://simulator.internal"))
        .isEqualTo("https://simulator.internal/v2");
  }

  @Test
  void a_public_base_url_ending_in_a_slash_does_not_double_the_one_the_base_path_carries() {
    ProviderPairing pairing = pairing("/v2", A_BASE_URL);

    assertThat(pairing.providerBaseUrlWithin("https://simulator.internal/"))
        .isEqualTo("https://simulator.internal/v2");
  }

  private static ProviderPairing pairing(String basePath, PairingSetting... settings) {
    return new ProviderPairing(PROVIDER, basePath, "a signed header", List.of(settings));
  }
}
