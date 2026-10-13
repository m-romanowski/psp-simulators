package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProviderPaymentMethodFieldsTest {

  private static final ProviderId PROVIDER = ProviderId.of("twokeys");

  private static final String CUSTOMER_ID = ProviderPaymentMethodFields.CUSTOMER_ID;

  private static final String PAYMENT_METHOD_ID = ProviderPaymentMethodFields.PAYMENT_METHOD_ID;

  private static final String TOKEN = ProviderPaymentMethodFields.TOKEN;

  @Test
  void a_provider_native_id_declared_with_no_example_is_refused_before_a_box_hints_its_key() {
    List<ConfigurableField> hintingNothing =
        List.of(
            new ConfigurableField(
                TOKEN, "the token", "a sentence a test does not read", true, false));

    assertThatThrownBy(
            () ->
                new ProviderPaymentMethodFields(
                    PROVIDER,
                    hintingNothing,
                    Map.of(
                        CUSTOMER_ID,
                        "it names no customer",
                        PAYMENT_METHOD_ID,
                        "it calls that a token")))
        .as(
            "the payment-methods form draws each of these as a box and prints its example as the"
                + " hint, and the only other thing it could print there is the key, which is a"
                + " developer's name for the field rather than a value a tester could type")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(TOKEN)
        .hasMessageContaining(ProviderPaymentMethodFields.A_BOX_HINTS_A_VALUE_AND_NEVER_A_KEY);
  }

  @Test
  void a_key_a_provider_declares_an_owner_key_lets_many_fixtures_of_one_run_name_one_value() {
    ProviderPaymentMethodFields declared = aProviderThatOwnsItsCustomers();

    assertThat(declared.namesAnOwner(CUSTOMER_ID)).isTrue();
    assertThat(declared.namesAnOwner(PAYMENT_METHOD_ID)).isFalse();
  }

  @Test
  void a_provider_that_shares_every_key_it_reads_is_refused_because_nothing_would_resolve_one() {
    List<ConfigurableField> onlyACustomerId =
        List.of(
            new ConfigurableField(
                CUSTOMER_ID,
                "the customer",
                "a sentence a test does not read",
                true,
                false,
                List.of(),
                "a value a test does not read"));
    Map<String, String> readingNeitherOther =
        Map.of(PAYMENT_METHOD_ID, "it calls that a customer", TOKEN, "it mints no token");
    Set<String> namingAnOwner = Set.of(CUSTOMER_ID);

    assertThatThrownBy(
            () ->
                new ProviderPaymentMethodFields(
                    PROVIDER, onlyACustomerId, readingNeitherOther, namingAnOwner))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must also require a key that names the instrument itself");
  }

  @Test
  void
      a_provider_that_shares_a_key_whose_instrument_key_is_optional_is_refused_by_the_same_floor() {
    List<ConfigurableField> neitherRequired =
        List.of(
            new ConfigurableField(
                PAYMENT_METHOD_ID,
                "the method",
                "a sentence a test does not read",
                false,
                false,
                List.of(),
                "a value a test does not read"),
            new ConfigurableField(
                CUSTOMER_ID,
                "the customer",
                "a sentence a test does not read",
                false,
                false,
                List.of(),
                "a value a test does not read"));
    Map<String, String> mintingNoToken = Map.of(TOKEN, "it mints no token");
    Set<String> namingAnOwner = Set.of(CUSTOMER_ID);

    assertThatThrownBy(
            () ->
                new ProviderPaymentMethodFields(
                    PROVIDER, neitherRequired, mintingNoToken, namingAnOwner))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must also require a key that names the instrument itself");
  }

  @Test
  void a_provider_that_shares_a_key_it_never_reads_is_refused_before_a_fixture_is_ever_seeded() {
    List<ConfigurableField> onlyAToken =
        List.of(
            new ConfigurableField(
                TOKEN,
                "the token",
                "a sentence a test does not read",
                true,
                false,
                List.of(),
                "a value a test does not read"));
    Map<String, String> readingNeitherOther =
        Map.of(CUSTOMER_ID, "it names no customer", PAYMENT_METHOD_ID, "it calls that a token");
    Set<String> namingAnOwnerItNeverReads = Set.of(CUSTOMER_ID);

    assertThatThrownBy(
            () ->
                new ProviderPaymentMethodFields(
                    PROVIDER, onlyAToken, readingNeitherOther, namingAnOwnerItNeverReads))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reads no such key");
  }

  @Test
  void a_provider_that_declares_no_owner_key_shares_nothing() {
    assertThat(aProviderThatReadsOnlyAToken().namesAnOwner(TOKEN)).isFalse();
    assertThat(aProviderThatReadsOnlyAToken().namesAnOwner(CUSTOMER_ID)).isFalse();
  }

  private static ProviderPaymentMethodFields aProviderThatOwnsItsCustomers() {
    return new ProviderPaymentMethodFields(
        PROVIDER,
        List.of(
            new ConfigurableField(
                PAYMENT_METHOD_ID,
                "the method",
                "a sentence a test does not read",
                true,
                false,
                List.of(),
                "a value a test does not read"),
            new ConfigurableField(
                CUSTOMER_ID,
                "the customer",
                "a sentence a test does not read",
                false,
                false,
                List.of(),
                "a value a test does not read")),
        Map.of(TOKEN, "it mints no token"),
        Set.of(CUSTOMER_ID));
  }

  @Test
  void a_provider_answers_for_every_key_the_published_schema_names() {
    ProviderPaymentMethodFields declared = aProviderThatReadsOnlyAToken();

    assertThat(declared.keys()).containsExactly(TOKEN);
    assertThat(declared.field(TOKEN)).isPresent();
    assertThat(declared.theRefusalOf(CUSTOMER_ID)).isEqualTo("it names no customer");
  }

  @Test
  void a_provider_that_neither_reads_nor_refuses_a_published_key_is_refused_by_the_spi() {
    List<ConfigurableField> onlyAToken =
        List.of(
            new ConfigurableField(
                TOKEN,
                "the token",
                "a sentence a test does not read",
                true,
                false,
                List.of(),
                "a value a test does not read"));
    Map<String, String> sayingNothingOfThePaymentMethodId =
        Map.of(CUSTOMER_ID, "it names no customer");

    assertThatThrownBy(
            () ->
                new ProviderPaymentMethodFields(
                    PROVIDER, onlyAToken, sayingNothingOfThePaymentMethodId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("providerIds." + PAYMENT_METHOD_ID);
  }

  @Test
  void a_key_a_provider_both_reads_and_refuses_is_refused_by_the_spi() {
    List<ConfigurableField> onlyAToken =
        List.of(
            new ConfigurableField(
                TOKEN,
                "the token",
                "a sentence a test does not read",
                true,
                false,
                List.of(),
                "a value a test does not read"));
    Map<String, String> refusingTheKeyItTakes =
        Map.of(
            CUSTOMER_ID,
            "it names no customer",
            PAYMENT_METHOD_ID,
            "it calls that a token",
            TOKEN,
            "it mints no token");

    assertThatThrownBy(
            () -> new ProviderPaymentMethodFields(PROVIDER, onlyAToken, refusingTheKeyItTakes))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot be both taken and refused");
  }

  @Test
  void a_provider_that_declares_one_key_twice_is_refused_because_a_form_would_draw_two_inputs() {
    List<ConfigurableField> oneKeyTwice =
        List.of(
            new ConfigurableField(
                TOKEN,
                "the token",
                "a sentence a test does not read",
                true,
                false,
                List.of(),
                "a value a test does not read"),
            new ConfigurableField(
                TOKEN,
                "the token again",
                "a sentence a test does not read",
                false,
                false,
                List.of(),
                "a value a test does not read"));
    Map<String, String> readingNeitherOther =
        Map.of(CUSTOMER_ID, "it names no customer", PAYMENT_METHOD_ID, "it calls that a token");

    assertThatThrownBy(
            () -> new ProviderPaymentMethodFields(PROVIDER, oneKeyTwice, readingNeitherOther))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("declared twice");
  }

  @Test
  void a_provider_that_refuses_a_key_without_saying_what_it_has_instead_is_refused() {
    List<ConfigurableField> onlyAToken =
        List.of(
            new ConfigurableField(
                TOKEN,
                "the token",
                "a sentence a test does not read",
                true,
                false,
                List.of(),
                "a value a test does not read"));
    Map<String, String> sayingNothingInItsOwnWords =
        Map.of(CUSTOMER_ID, "it names no customer", PAYMENT_METHOD_ID, "  ");

    assertThatThrownBy(
            () -> new ProviderPaymentMethodFields(PROVIDER, onlyAToken, sayingNothingInItsOwnWords))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("in its own words");
  }

  @Test
  void a_provider_may_read_no_provider_native_id_at_all_as_long_as_it_says_so_for_each() {
    ProviderPaymentMethodFields declared =
        new ProviderPaymentMethodFields(
            PROVIDER,
            List.of(),
            Map.of(
                CUSTOMER_ID,
                "it names no customer",
                PAYMENT_METHOD_ID,
                "it names no payment method",
                TOKEN,
                "it mints no token"));

    assertThat(declared.keys()).isEmpty();
    assertThat(declared.theRefusalOf(TOKEN)).isEqualTo("it mints no token");
  }

  @Test
  void a_key_the_published_schema_never_named_is_refused_by_naming_the_ones_it_does() {
    assertThat(aProviderThatReadsOnlyAToken().theRefusalOf("pan"))
        .contains("no key of the published payment-method fixture schema")
        .contains(CUSTOMER_ID, PAYMENT_METHOD_ID, TOKEN);
  }

  private static ProviderPaymentMethodFields aProviderThatReadsOnlyAToken() {
    return new ProviderPaymentMethodFields(
        PROVIDER,
        List.of(
            new ConfigurableField(
                TOKEN,
                "the token",
                "a sentence a test does not read",
                true,
                false,
                List.of(),
                "a value a test does not read")),
        Map.of(CUSTOMER_ID, "it names no customer", PAYMENT_METHOD_ID, "it calls that a token"));
  }
}
