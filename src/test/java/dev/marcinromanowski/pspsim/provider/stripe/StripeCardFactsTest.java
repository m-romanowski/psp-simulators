package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class StripeCardFactsTest {

  private static final Instant NOW = Instant.parse("2026-10-31T09:00:00Z");

  @ParameterizedTest
  @ValueSource(
      strings = {
        "card[number]",
        "card[cvc]",
        "payment_method_data[card][number]",
        "payment_method_data[card][cvc]",
        "card[number][0]",
        "Card[Number]",
        "card[cvc",
        "card]number[",
        "a[card][b][cvc]"
      })
  void a_name_whose_keys_hold_card_and_later_number_or_cvc_names_a_sensitive_card_member(
      String name) {
    assertThat(StripeFields.namesASensitiveCardMember(name)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "card[exp_month]",
        "card[exp_year]",
        "card[token]",
        "number",
        "cvc",
        "card",
        "number[card]",
        "metadata[cvc_note]",
        "cards[number]"
      })
  void a_name_without_a_card_member_after_card_names_none(String name) {
    assertThat(StripeFields.namesASensitiveCardMember(name)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "4242424242424242",
        "4242 4242 4242 4242",
        "4242-4242-4242-4242",
        "378282246310005",
        "6205500000000000004",
        "424242424242"
      })
  void a_value_of_twelve_to_nineteen_digits_passing_the_luhn_check_reads_as_a_card_number(
      String value) {
    assertThat(StripeFields.readsAsACardNumber(value)).isTrue();
    assertThat(StripeFields.withheldIfACardNumber(value)).isEqualTo("[redacted]");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "4242424242424241",
        "42424242420",
        "62055000000000000040",
        "1099",
        "pm_card_visa",
        "4242x24242424242",
        ""
      })
  void a_value_that_is_no_card_number_is_kept(String value) {
    assertThat(StripeFields.readsAsACardNumber(value)).isFalse();
    assertThat(StripeFields.withheldIfACardNumber(value)).isEqualTo(value);
  }

  @Test
  void withholding_the_card_redacts_by_name_and_by_value_and_keeps_every_other_value() {
    Map<String, List<String>> sent = new LinkedHashMap<>();
    sent.put("card[number]", List.of("4111"));
    sent.put("card[cvc]", List.of("123"));
    sent.put("card[exp_month]", List.of("10"));
    sent.put("metadata[note]", List.of("4242424242424242", "kept"));

    assertThat(StripeFields.withholdingTheCard(sent))
        .containsExactly(
            Map.entry("card[number]", List.of("[redacted]")),
            Map.entry("card[cvc]", List.of("[redacted]")),
            Map.entry("card[exp_month]", List.of("10")),
            Map.entry("metadata[note]", List.of("[redacted]", "kept")));
  }

  @ParameterizedTest
  @CsvSource({
    "4242424242424242,true",
    "4242424242424241,false",
    "0000000000000000,true",
    "79927398713,true",
    "79927398710,false",
    "18,true",
    "81,false"
  })
  void the_luhn_check_doubles_every_second_digit_from_the_right(String digits, boolean passes) {
    assertThat(StripeNewPaymentMethod.passesLuhn(digits)).isEqualTo(passes);
  }

  @ParameterizedTest
  @CsvSource({
    "4242 4242 4242 4242,4242424242424242",
    "424242424242,424242424242",
    "4242424242424242424,4242424242424242424"
  })
  void the_digits_of_a_number_are_read_without_its_spaces(String number, String digits) {
    assertThat(StripeNewPaymentMethod.theDigitsOf(number)).isEqualTo(digits);
  }

  @ParameterizedTest
  @ValueSource(strings = {"42424242424", "42424242424242424242", "4242-4242", "4242a"})
  void a_number_outside_twelve_to_nineteen_digits_has_no_digits_to_read(String number) {
    assertThat(StripeNewPaymentMethod.theDigitsOf(number)).isNull();
  }

  @ParameterizedTest
  @CsvSource({
    "42,2026,2042",
    "75,2026,2075",
    "76,2026,1976",
    "26,2026,2026",
    "95,2026,1995",
    "00,2026,2000",
    "99,2051,2099",
    "01,2051,2001",
    "00,2051,2100",
    "2031,2026,2031",
    "0042,2026,42"
  })
  void a_two_digit_year_is_read_as_the_year_nearest_this_one(String sent, int thisYear, int read) {
    assertThat(StripeNewPaymentMethod.aYear(sent, thisYear)).isEqualTo(read);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1", "123", "20261", "-1", "-42"})
  void a_year_of_another_length_or_sign_reads_as_no_year(String sent) {
    assertThat(StripeNewPaymentMethod.aYear(sent, 2026)).isNull();
  }

  @Test
  void the_catalogue_holds_every_number_once_and_every_test_name_once() {
    List<StripeTestCards.TestCard> catalogue = StripeTestCards.catalogue();
    Set<String> numbers = new HashSet<>();
    Set<String> names = new HashSet<>();
    for (StripeTestCards.TestCard card : catalogue) {
      assertThat(numbers.add(card.number())).as(card.number()).isTrue();
      assertThat(StripeNewPaymentMethod.passesLuhn(card.number())).as(card.number()).isTrue();
      for (String name : card.names()) {
        assertThat(names.add(name)).as(name).isTrue();
      }
    }
    assertThat(catalogue).hasSize(81);
    assertThat(names).hasSize(73);
  }

  @ParameterizedTest
  @CsvSource({
    "pm_card_visa,4242424242424242",
    "pm_card_us,4242424242424242",
    "pm_card_jcb,3530111333300000",
    "pm_card_th_debit,4000057640000008",
    "pm_card_visa_chargeDeclined,4000000000000002"
  })
  void a_published_test_id_names_the_card_it_stands_for(String id, String number) {
    assertThat(StripeTestCards.byPaymentMethodId(id))
        .hasValueSatisfying(card -> assertThat(card.number()).isEqualTo(number));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "pm_card_",
        "pm_card_nothing",
        "visa",
        "tok_visa",
        "pm_card_visa_chargeDeclinedLostCard"
      })
  void a_name_the_catalogue_does_not_hold_names_no_card(String id) {
    assertThat(StripeTestCards.byPaymentMethodId(id)).isEmpty();
  }

  @Test
  void a_token_names_its_card_and_a_payment_method_id_is_no_token() {
    assertThat(StripeTestCards.byToken("tok_amex"))
        .hasValueSatisfying(card -> assertThat(card.number()).isEqualTo("378282246310005"));
    assertThat(StripeTestCards.byToken("pm_card_amex")).isEmpty();
    assertThat(StripeTestCards.byToken(null)).isEmpty();
    assertThat(StripeTestCards.byPaymentMethodId(null)).isEmpty();
  }

  @Test
  void a_card_a_test_name_stands_for_expires_in_this_month_a_year_on() {
    StripeCard card =
        StripeTestCards.theCardATestNameStandsFor(
            StripeTestCards.byToken("tok_visa").orElseThrow(),
            Instant.parse("2026-02-01T00:00:00Z"));

    assertThat(card.expMonth()).isEqualTo(2);
    assertThat(card.expYear()).isEqualTo(2027);
    assertThat(card.last4()).isEqualTo("4242");
  }

  @ParameterizedTest
  @CsvSource({
    "visa,visa",
    "mastercard,mastercard",
    "amex,american_express",
    "discover,discover",
    "diners,diners_club",
    "jcb,jcb",
    "unionpay,union_pay"
  })
  void a_brand_is_displayed_as_stripe_spells_its_display_brand(String brand, String display) {
    assertThat(StripeTestCards.theDisplayBrandOf(brand)).isEqualTo(display);
  }

  @Test
  void an_unknown_brand_is_displayed_as_nothing() {
    assertThat(StripeTestCards.theDisplayBrandOf("unknown")).isNull();
  }

  @Test
  void a_fixture_card_is_a_card_hash_naming_its_brand_as_its_only_network() {
    StripeCard card = StripeCard.ofAFixture(new FixtureCard("visa", "4242", 12, 2030, "credit"));

    assertThat(card)
        .isEqualTo(
            new StripeCard(
                "visa", "4242", 12, 2030, "credit", null, null, List.of("visa"), null, null));
  }

  @Test
  void a_card_hash_refuses_a_regulated_status_stripe_does_not_publish_and_a_card_number() {
    assertThatThrownBy(
            () ->
                new StripeCard(
                    "visa", "4242", 12, 2030, "credit", null, null, null, "sometimes", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(StripeCard.A_REGULATED_STATUS_IS_ONE_STRIPE_PUBLISHES);
    assertThatThrownBy(
            () ->
                new StripeCard(
                    "visa", "4242424242424242", 12, 2030, "credit", null, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(FixtureCard.A_FIXTURE_HOLDS_NO_CARD_NUMBER);
    assertThat(
            new StripeCard("visa", "4242", 12, 2030, "credit", null, null, null, "regulated", null)
                .regulatedStatus())
        .isEqualTo("regulated");
  }

  @Test
  void a_billing_value_longer_than_stripe_accepts_is_refused_by_the_object() {
    assertThatThrownBy(
            () ->
                new StripeBillingDetails(
                    null, null, null, null, null, null, null, "n".repeat(5001), null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(StripeBillingDetails.A_VALUE_IS_AS_LONG_AS_STRIPE_ACCEPTS);
    assertThat(
            new StripeBillingDetails(
                    null, null, null, null, null, null, null, "n".repeat(5000), null, null)
                .name())
        .hasSize(5000);
  }

  @Test
  void a_billing_member_is_read_by_the_name_stripe_publishes_it_under() {
    StripeBillingDetails details =
        StripeBillingDetails.of(
            Map.of(
                "city",
                "c",
                "country",
                "PL",
                "line1",
                "l1",
                "line2",
                "l2",
                "postal_code",
                "00-001",
                "state",
                "s",
                "email",
                "e@x.pl",
                "name",
                "n",
                "phone",
                "p",
                "tax_id",
                "t"));

    assertThat(details)
        .isEqualTo(
            new StripeBillingDetails(
                "c", "PL", "l1", "l2", "00-001", "s", "e@x.pl", "n", "p", "t"));
    assertThat(details.addressMember("postal_code")).isEqualTo("00-001");
    assertThat(details.personMember("tax_id")).isEqualTo("t");
    assertThatThrownBy(() -> details.addressMember("email"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> details.personMember("city"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void a_payment_method_refuses_a_redisplay_stripe_does_not_publish() {
    assertThatThrownBy(
            () ->
                new StripePaymentMethod(
                    TestRunId.of("run"),
                    "pm_sim_1",
                    "merchant-1",
                    null,
                    StripePaymentMethodType.CARD,
                    StripeCard.ofAFixture(new FixtureCard("visa", "4242", 12, 2030, "credit")),
                    StripeBillingDetails.NONE,
                    Map.of(),
                    "sometimes",
                    null,
                    ObjectOrigin.PROVIDER_API,
                    NOW,
                    NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(StripePaymentMethod.A_REDISPLAY_IS_ONE_STRIPE_PUBLISHES);
  }

  @Test
  void a_payment_method_attached_to_a_customer_keeps_everything_else_it_carries() {
    Map<String, String> metadata = new LinkedHashMap<>();
    metadata.put("b", "2");
    metadata.put("a", "1");
    StripePaymentMethod method =
        new StripePaymentMethod(
            TestRunId.of("run"),
            "pm_sim_1",
            "merchant-1",
            null,
            StripePaymentMethodType.CARD,
            StripeCard.ofAFixture(new FixtureCard("visa", "4242", 12, 2030, "credit")),
            new StripeBillingDetails(null, null, null, null, null, null, null, "n", null, null),
            metadata,
            "limited",
            null,
            ObjectOrigin.PROVIDER_API,
            NOW,
            NOW);
    Instant later = NOW.plusSeconds(60);

    StripePaymentMethod attached = method.attachedTo("cus_1", later);
    StripePaymentMethod detached = attached.detachedAt(later.plusSeconds(1));

    assertThat(attached.customerId()).isEqualTo("cus_1");
    assertThat(attached.updatedAt()).isEqualTo(later);
    assertThat(attached.createdAt()).isEqualTo(NOW);
    assertThat(attached.metadata().keySet()).containsExactly("b", "a");
    assertThat(attached.allowRedisplay()).isEqualTo("limited");
    assertThat(attached.billingDetails().name()).isEqualTo("n");
    assertThat(detached.customerId()).isNull();
    assertThat(detached.metadata()).isEqualTo(metadata);
    assertThat(detached.allowRedisplay()).isEqualTo("limited");
  }
}
