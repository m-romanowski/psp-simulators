package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConfigurationRefusalTest {

  @Test
  void a_refusal_of_a_configured_value_names_the_field_a_form_drew_for_it() {
    ConfigurationRefusal refusal =
        ConfigurationRefusal.of("accounts[0].configuration.userId is required by provider grow");

    assertThat(refusal.namesAField()).isTrue();
    assertThat(refusal.field()).isEqualTo("userId");
  }

  @Test
  void a_refusal_of_a_field_a_provider_spells_with_dots_keeps_the_whole_key() {
    ConfigurationRefusal refusal =
        ConfigurationRefusal.of(
            "accounts[2].configuration.azericard.partial-completion must be one of two modes");

    assertThat(refusal.field()).isEqualTo("azericard.partial-completion");
  }

  @Test
  void a_refusal_of_the_identity_itself_names_the_identity_input() {
    ConfigurationRefusal refusal = ConfigurationRefusal.of("accounts[0].accountId is required");

    assertThat(refusal.field()).isEqualTo("accountId");
  }

  @Test
  void a_refusal_that_belongs_to_no_field_is_shown_beside_none_of_them() {
    ConfigurationRefusal refusal =
        ConfigurationRefusal.of("testRunId must match [A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    assertThat(refusal.namesAField()).isFalse();
    assertThat(refusal.field()).isNull();
  }

  @Test
  void a_refusal_of_an_account_position_names_no_field_because_no_input_drew_it() {
    assertThat(ConfigurationRefusal.of("accounts[1] must name an account").field()).isNull();
  }

  @Test
  void a_refusal_whose_prefix_names_no_field_after_it_is_shown_beside_none_of_them() {
    assertThat(ConfigurationRefusal.of("accounts[0].configuration. is required").field()).isNull();
    assertThat(ConfigurationRefusal.of("accounts[0].configuration.").field()).isNull();
  }

  @Test
  void a_message_shaped_like_a_property_path_but_naming_no_account_position_names_no_field() {
    assertThat(ConfigurationRefusal.of("accounts[x].configuration.userId is required").field())
        .isNull();
    assertThat(ConfigurationRefusal.of("accounts[].accountId is required").field()).isNull();
  }

  @Test
  void an_identity_refusal_that_only_starts_with_the_identity_path_names_no_field() {
    assertThat(ConfigurationRefusal.of("accounts[0].accountIdentifier is required").field())
        .isNull();
  }

  @Test
  void the_prefix_every_provider_builds_is_the_prefix_the_reader_recognises() {
    for (int index = 0; index < 3; index++) {
      assertThat(
              ConfigurationRefusal.of(
                      AccountProperty.prefixOf(index) + "userId is required by provider grow")
                  .field())
          .isEqualTo("userId");
      assertThat(
              ConfigurationRefusal.of(AccountProperty.identityOf(index) + " is required").field())
          .isEqualTo("accountId");
    }
  }

  @Test
  void a_refusal_read_as_a_field_is_reprinted_under_the_name_the_box_drawing_it_carries() {
    assertThat(
            AccountProperty.theRefusalWithTheFieldNamed(
                "accounts[0].configuration.userId is required by provider grow", "Grow user id"))
        .as(
            "a tester has no accounts[0]: their screen draws a box labelled this way, and"
                + " everything the provider said after the property path is kept word for word")
        .isEqualTo("Grow user id is required by provider grow");
    assertThat(
            AccountProperty.theRefusalWithTheFieldNamed(
                "accounts[2].accountId is required", "account identity"))
        .isEqualTo("account identity is required");
  }

  @Test
  void a_refusal_that_is_no_property_path_at_all_is_reprinted_exactly_as_the_provider_said_it() {
    String saidOfNoField = "this provider holds no account of that identity";

    assertThat(AccountProperty.theRefusalWithTheFieldNamed(saidOfNoField, "Grow user id"))
        .as(
            "the screen asks for this rewrite whenever the refusal names a box, and a message"
                + " carrying no property path has nothing to replace, so it must come back whole"
                + " rather than empty or under a name it never spoke of")
        .isEqualTo(saidOfNoField);
  }

  @Test
  void the_refusal_keeps_the_whole_message_so_nothing_the_provider_said_is_dropped() {
    String said = "accounts[0].configuration.secretKey must begin with sk_test_";

    assertThat(ConfigurationRefusal.of(said).message()).isEqualTo(said);
  }
}
