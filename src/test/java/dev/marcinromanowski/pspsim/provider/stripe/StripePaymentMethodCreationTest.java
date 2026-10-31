package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.TheSpellingsOfAHeaderName.aHeaderOfThisNameUnderAnySpelling;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.ACCOUNT;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.ANOTHER_SECRET_KEY;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.ELSEWHERE_SECRET_KEY;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.PUBLISHABLE_KEY;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.RUN;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.SECRET_KEY;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.THE_FIRST_MINTED_METHOD;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.aVisa;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.flat;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.theValueAt;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.with;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.bodyOf;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class StripePaymentMethodCreationTest {

  private final StripeCardWorld world = new StripeCardWorld();

  @Test
  void a_secret_key_creates_a_card_and_is_answered_the_hash_stripe_answers_for_the_number() {
    StripeApiResponse created = world.createMethod(SECRET_KEY, aVisa());

    assertThat(created.httpStatus()).as(bodyOf(created)).isEqualTo(200);
    assertThat(created.providerObjectId()).isEqualTo(THE_FIRST_MINTED_METHOD);
    assertThat(flat(created))
        .containsEntry("id", List.of(THE_FIRST_MINTED_METHOD))
        .containsEntry("object", List.of("payment_method"))
        .containsEntry("type", List.of("card"))
        .containsEntry("allow_redisplay", List.of("unspecified"))
        .containsEntry("customer", List.of())
        .containsEntry("livemode", List.of("false"))
        .containsEntry("created", List.of("1793437200"))
        .containsEntry("card.brand", List.of("visa"))
        .containsEntry("card.last4", List.of("4242"))
        .containsEntry("card.exp_month", List.of("10"))
        .containsEntry("card.exp_year", List.of("2028"))
        .containsEntry("card.funding", List.of("credit"))
        .containsEntry("card.country", List.of("US"))
        .containsEntry("card.display_brand", List.of("visa"))
        .containsEntry("card.networks.available", List.of("visa"))
        .containsEntry("card.networks.preferred", List.of())
        .containsEntry("card.regulated_status", List.of("unregulated"))
        .containsEntry("card.three_d_secure_usage.supported", List.of("true"))
        .containsEntry("card.checks.cvc_check", List.of())
        .containsEntry("card.checks.address_line1_check", List.of())
        .containsEntry("card.checks.address_postal_code_check", List.of())
        .containsEntry("card.generated_from", List.of())
        .containsEntry("card.wallet", List.of())
        .containsEntry("billing_details.address.city", List.of())
        .containsEntry("billing_details.tax_id", List.of());
    assertThat(bodyOf(created)).doesNotContain("4242424242424242").doesNotContain("fingerprint");
    assertThat(world.theMethodStored(THE_FIRST_MINTED_METHOD).origin())
        .isEqualTo(ObjectOrigin.PROVIDER_API);
  }

  @Test
  void the_answer_carries_exactly_the_members_of_the_published_object() {
    StripeApiResponse created = world.createMethod(SECRET_KEY, aVisa());

    assertThat(membersOf(bodyOf(created)))
        .containsExactly(
            "id",
            "object",
            "allow_redisplay",
            "billing_details",
            "created",
            "customer",
            "livemode",
            "metadata",
            "type",
            "card");
    assertThat(
            ((StripeJson.JsonObject)
                    ((StripeJson.JsonObject) StripeJsonReader.read(bodyOf(created)))
                        .members()
                        .get("card"))
                .members()
                .keySet())
        .containsExactly(
            "brand",
            "checks",
            "country",
            "display_brand",
            "exp_month",
            "exp_year",
            "funding",
            "generated_from",
            "last4",
            "networks",
            "regulated_status",
            "three_d_secure_usage",
            "wallet");
  }

  @Test
  void a_publishable_key_creates_the_same_card_without_a_client_secret_and_is_shown_no_metadata() {
    StripeApiResponse created =
        world.createMethod(PUBLISHABLE_KEY, with(aVisa(), "metadata[test_key]", "test_value"));

    assertThat(created.httpStatus()).as(bodyOf(created)).isEqualTo(200);
    assertThat(membersOf(bodyOf(created)))
        .containsExactly(
            "id",
            "object",
            "allow_redisplay",
            "billing_details",
            "created",
            "customer",
            "livemode",
            "type",
            "card");
    assertThat(theValueAt(created, "card.brand")).isEqualTo("visa");
    assertThat(world.theMethodStored(THE_FIRST_MINTED_METHOD).metadata())
        .containsExactly(Map.entry("test_key", "test_value"));
    assertThat(world.theMethodStored(THE_FIRST_MINTED_METHOD).accountId()).isEqualTo(ACCOUNT);
  }

  @Test
  void a_secret_key_reads_back_the_metadata_the_publishable_key_was_not_shown() {
    world.createMethod(PUBLISHABLE_KEY, with(aVisa(), "metadata[test_key]", "test_value"));

    StripeApiResponse read = world.retrieveMethod(SECRET_KEY, THE_FIRST_MINTED_METHOD);

    assertThat(read.httpStatus()).isEqualTo(200);
    assertThat(theValueAt(read, "metadata.test_key")).isEqualTo("test_value");
  }

  @Test
  void the_billing_details_the_metadata_and_the_redisplay_sent_are_answered_as_sent() {
    Map<String, String> form = aVisa();
    form.put("billing_details[address][city]", "San Francisco");
    form.put("billing_details[address][country]", "US");
    form.put("billing_details[address][line1]", "150 Townsend St");
    form.put("billing_details[address][line2]", "4th Floor");
    form.put("billing_details[address][postal_code]", "94103");
    form.put("billing_details[address][state]", "CA");
    form.put("billing_details[email]", "email@email.com");
    form.put("billing_details[name]", "Isaac Asimov");
    form.put("billing_details[phone]", "555-555-5555");
    form.put("billing_details[tax_id]", "12-3456789");
    form.put("metadata[second]", "2");
    form.put("metadata[first]", "1");
    form.put("metadata[unset]", "");
    form.put("allow_redisplay", "always");

    StripeApiResponse created = world.createMethod(SECRET_KEY, form);

    assertThat(created.httpStatus()).as(bodyOf(created)).isEqualTo(200);
    assertThat(flat(created))
        .containsEntry("billing_details.address.city", List.of("San Francisco"))
        .containsEntry("billing_details.address.country", List.of("US"))
        .containsEntry("billing_details.address.line1", List.of("150 Townsend St"))
        .containsEntry("billing_details.address.line2", List.of("4th Floor"))
        .containsEntry("billing_details.address.postal_code", List.of("94103"))
        .containsEntry("billing_details.address.state", List.of("CA"))
        .containsEntry("billing_details.email", List.of("email@email.com"))
        .containsEntry("billing_details.name", List.of("Isaac Asimov"))
        .containsEntry("billing_details.phone", List.of("555-555-5555"))
        .containsEntry("billing_details.tax_id", List.of("12-3456789"))
        .containsEntry("allow_redisplay", List.of("always"))
        .doesNotContainKey("metadata.unset");
    assertThat(bodyOf(created)).contains("\"metadata\":{\"second\":\"2\",\"first\":\"1\"}");
  }

  @Test
  void a_number_written_with_spaces_is_the_same_card() {
    StripeApiResponse created =
        world.createMethod(SECRET_KEY, with(aVisa(), "card[number]", "4242 4242 4242 4242"));

    assertThat(created.httpStatus()).isEqualTo(200);
    assertThat(theValueAt(created, "card.last4")).isEqualTo("4242");
    assertThat(theValueAt(created, "card.country")).isEqualTo("US");
  }

  @ParameterizedTest
  @CsvSource({
    "5555555555554444,mastercard,unknown,4444,mastercard",
    "378282246310005,amex,unknown,0005,american_express",
    "36227206271667,diners,unknown,1667,diners_club",
    "6205500000000000004,unionpay,unknown,0004,union_pay",
    "6011981111111113,discover,debit,1113,discover",
    "5105105105105100,mastercard,prepaid,5100,mastercard",
    "3566002020360505,jcb,unknown,0505,jcb"
  })
  void a_published_number_of_no_country_answers_its_brand_and_funding_and_no_country(
      String number, String brand, String funding, String last4, String displayBrand) {
    StripeApiResponse created =
        world.createMethod(SECRET_KEY, with(aVisa(), "card[number]", number));

    assertThat(created.httpStatus()).as(bodyOf(created)).isEqualTo(200);
    assertThat(flat(created))
        .containsEntry("card.brand", List.of(brand))
        .containsEntry("card.funding", List.of(funding))
        .containsEntry("card.last4", List.of(last4))
        .containsEntry("card.country", List.of())
        .containsEntry("card.display_brand", List.of(displayBrand))
        .containsEntry("card.networks.available", List.of(brand))
        .containsEntry("card.regulated_status", List.of())
        .containsEntry("card.three_d_secure_usage", List.of());
  }

  @ParameterizedTest
  @CsvSource({
    "4000058260000005,visa,debit,GB",
    "4000007640000003,visa,credit,TH",
    "5555558265554449,mastercard,unknown,GB",
    "3530111333300000,jcb,unknown,JP",
    "4000000760000002,visa,unknown,BR"
  })
  void a_number_of_the_country_table_answers_its_country(
      String number, String brand, String funding, String country) {
    StripeApiResponse created =
        world.createMethod(SECRET_KEY, with(aVisa(), "card[number]", number));

    assertThat(flat(created))
        .containsEntry("card.brand", List.of(brand))
        .containsEntry("card.funding", List.of(funding))
        .containsEntry("card.country", List.of(country))
        .containsEntry("card.regulated_status", List.of());
  }

  @ParameterizedTest
  @ValueSource(strings = {"4000000000000002", "4000000000009995"})
  void a_decline_card_the_recordings_answered_is_created_as_they_answered_it(String number) {
    StripeApiResponse created =
        world.createMethod(SECRET_KEY, with(aVisa(), "card[number]", number));

    assertThat(created.httpStatus()).isEqualTo(200);
    assertThat(flat(created))
        .containsEntry("card.brand", List.of("visa"))
        .containsEntry("card.funding", List.of("credit"))
        .containsEntry("card.country", List.of("US"))
        .containsEntry("card.regulated_status", List.of("unregulated"))
        .containsEntry("card.three_d_secure_usage.supported", List.of("true"));
  }

  @Test
  void a_number_stripe_publishes_nothing_for_answers_only_what_the_number_says() {
    StripeApiResponse created =
        world.createMethod(SECRET_KEY, with(aVisa(), "card[number]", "4111111111111111"));

    assertThat(created.httpStatus()).isEqualTo(200);
    assertThat(flat(created))
        .containsEntry("card.brand", List.of("unknown"))
        .containsEntry("card.funding", List.of("unknown"))
        .containsEntry("card.last4", List.of("1111"))
        .containsEntry("card.exp_month", List.of("10"))
        .containsEntry("card.exp_year", List.of("2028"))
        .containsEntry("card.country", List.of())
        .containsEntry("card.display_brand", List.of())
        .containsEntry("card.networks", List.of())
        .containsEntry("card.regulated_status", List.of())
        .containsEntry("card.three_d_secure_usage", List.of());
  }

  @ParameterizedTest
  @CsvSource({
    "tok_visa,visa,credit,4242,US",
    "tok_us,visa,credit,4242,US",
    "tok_mastercard_prepaid,mastercard,prepaid,5100,",
    "tok_jcb,jcb,unknown,0000,JP",
    "tok_gb_debit,visa,debit,0005,GB"
  })
  void a_published_test_token_stands_for_its_card_expiring_a_year_after_this_month(
      String token, String brand, String funding, String last4, String country) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("type", "card");
    form.put("card[token]", token);

    StripeApiResponse created = world.createMethod(PUBLISHABLE_KEY, form);

    assertThat(created.httpStatus()).as(bodyOf(created)).isEqualTo(200);
    assertThat(flat(created))
        .containsEntry("card.brand", List.of(brand))
        .containsEntry("card.funding", List.of(funding))
        .containsEntry("card.last4", List.of(last4))
        .containsEntry("card.country", country == null ? List.of() : List.of(country))
        .containsEntry("card.exp_month", List.of("10"))
        .containsEntry("card.exp_year", List.of("2027"));
  }

  @Test
  void a_token_stripe_does_not_publish_is_no_such_token() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("type", "card");
    form.put("card[token]", "tok_this_simulator_never_issued");

    StripeApiResponse refused = world.createMethod(SECRET_KEY, form);

    assertThat(refused.httpStatus()).isEqualTo(404);
    assertThat(flat(refused))
        .containsEntry("error.type", List.of("invalid_request_error"))
        .containsEntry("error.code", List.of("resource_missing"))
        .containsEntry("error.param", List.of("card[token]"))
        .containsEntry(
            "error.message", List.of("No such token: 'tok_this_simulator_never_issued'"));
    assertThat(world.theMethodsStored()).isEmpty();
  }

  @Test
  void a_card_number_sent_as_a_token_is_never_repeated_in_the_refusal() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("type", "card");
    form.put("card[token]", "4242424242424242");

    StripeApiResponse refused = world.createMethod(SECRET_KEY, form);

    assertThat(refused.httpStatus()).isEqualTo(404);
    assertThat(theValueAt(refused, "error.message")).isEqualTo("No such token: '[redacted]'");
    assertThat(world.everythingTheTrailAndTheExchangesKeep()).doesNotContain("4242424242424242");
  }

  @Test
  void a_token_beside_card_details_is_refused_as_two_exclusive_parameters() {
    StripeApiResponse refused =
        world.createMethod(SECRET_KEY, with(aVisa(), "card[token]", "tok_visa"));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(flat(refused))
        .containsEntry("error.code", List.of("parameters_exclusive"))
        .containsEntry("error.param", List.of("card[token]"))
        .containsEntry(
            "error.message",
            List.of("You may only specify one of these parameters: card[token], card[number]."));
    assertThat(world.theMethodsStored()).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"type", "card", "card[number]", "card[exp_month]", "card[exp_year]"})
  void a_missing_required_parameter_is_named_the_way_stripe_names_it(String missing) {
    Map<String, String> form = aVisa();
    if (missing.equals("card")) {
      form.keySet().removeIf(name -> name.startsWith("card"));
    } else {
      form.remove(missing);
    }

    StripeApiResponse refused = world.createMethod(SECRET_KEY, form);

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(flat(refused))
        .containsEntry("error.type", List.of("invalid_request_error"))
        .containsEntry("error.code", List.of("parameter_missing"))
        .containsEntry("error.param", List.of(missing))
        .containsEntry("error.message", List.of("Missing required param: " + missing + "."));
    assertThat(world.theMethodsStored()).isEmpty();
  }

  @Test
  void a_type_other_than_card_is_refused_by_name() {
    StripeApiResponse refused = world.createMethod(SECRET_KEY, with(aVisa(), "type", "paypal"));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(flat(refused))
        .containsEntry("error.param", List.of("type"))
        .doesNotContainKey("error.code");
    assertThat(theValueAt(refused, "error.message"))
        .isEqualTo(
            "This simulator creates a PaymentMethod of type card only, and this request asks for"
                + " one of type `paypal`. A PaymentMethod of another type exists here only where a"
                + " fixture seeded it.");
    assertThat(world.theMethodsStored()).isEmpty();
  }

  @Test
  void a_card_sent_as_a_value_is_refused_as_unreadable() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("type", "card");
    form.put("card", "4242424242424242");

    StripeApiResponse refused = world.createMethod(SECRET_KEY, form);

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(flat(refused))
        .containsEntry("error.param", List.of("card"))
        .doesNotContainKey("error.code");
    assertThat(bodyOf(refused)).doesNotContain("4242424242424242");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "4242-4242-4242-4242",
        "42424242424",
        "42424242424242424242",
        "4242x24242424242",
        "٤242424242424242"
      })
  void a_number_that_is_not_twelve_to_nineteen_digits_is_an_invalid_number(String number) {
    StripeApiResponse refused =
        world.createMethod(SECRET_KEY, with(aVisa(), "card[number]", number));

    assertThatACardError(refused, "invalid_number", "Your card number is invalid.", "number");
  }

  @Test
  void a_number_that_fails_the_luhn_check_is_an_incorrect_number() {
    StripeApiResponse refused =
        world.createMethod(SECRET_KEY, with(aVisa(), "card[number]", "4242424242424241"));

    assertThatACardError(refused, "incorrect_number", "Your card number is incorrect.", "number");
  }

  @ParameterizedTest
  @ValueSource(strings = {"13", "0", "00", "123", "-1"})
  void a_month_outside_the_year_is_an_invalid_expiry_month(String month) {
    StripeApiResponse refused =
        world.createMethod(SECRET_KEY, with(aVisa(), "card[exp_month]", month));

    assertThatACardError(
        refused, "invalid_expiry_month", "Your card's expiration month is invalid.", "exp_month");
  }

  @ParameterizedTest
  @CsvSource({"card[exp_month],ten", "card[exp_year],2028.0"})
  void a_month_or_year_that_is_no_integer_is_refused_as_an_invalid_integer(
      String param, String sent) {
    StripeApiResponse refused = world.createMethod(SECRET_KEY, with(aVisa(), param, sent));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(flat(refused))
        .containsEntry("error.type", List.of("invalid_request_error"))
        .containsEntry("error.code", List.of("parameter_invalid_integer"))
        .containsEntry("error.param", List.of(param))
        .containsEntry("error.message", List.of("Invalid integer: " + sent));
    assertThat(world.theMethodsStored()).isEmpty();
  }

  @Test
  void a_card_number_sent_as_the_month_is_never_repeated_in_the_refusal() {
    StripeApiResponse refused =
        world.createMethod(SECRET_KEY, with(aVisa(), "card[exp_month]", "4242 4242 4242 4242"));

    assertThat(theValueAt(refused, "error.message")).isEqualTo("Invalid integer: [redacted]");
  }

  @ParameterizedTest
  @ValueSource(strings = {"2013", "2025", "20", "95", "76", "123", "1", "-2028", "20280"})
  void a_year_before_this_one_or_of_no_readable_length_is_an_invalid_expiry_year(String year) {
    StripeApiResponse refused =
        world.createMethod(SECRET_KEY, with(aVisa(), "card[exp_year]", year));

    assertThatACardError(
        refused, "invalid_expiry_year", "Your card's expiration year is invalid.", "exp_year");
  }

  @ParameterizedTest
  @CsvSource({"42,2042", "75,2075", "26,2026", "2026,2026", "9999,9999"})
  void a_year_of_this_year_or_later_is_read_as_the_year_it_names(String sent, String read) {
    StripeApiResponse created =
        world.createMethod(
            SECRET_KEY, with(with(aVisa(), "card[exp_year]", sent), "card[exp_month]", "12"));

    assertThat(created.httpStatus()).as(bodyOf(created)).isEqualTo(200);
    assertThat(theValueAt(created, "card.exp_year")).isEqualTo(read);
  }

  @Test
  void a_month_already_past_in_this_year_is_an_invalid_expiry_month() {
    StripeApiResponse refused =
        world.createMethod(
            SECRET_KEY, with(with(aVisa(), "card[exp_year]", "2026"), "card[exp_month]", "9"));

    assertThatACardError(
        refused, "invalid_expiry_month", "Your card's expiration month is invalid.", "exp_month");
  }

  @Test
  void this_month_of_this_year_has_not_passed() {
    StripeApiResponse created =
        world.createMethod(
            SECRET_KEY, with(with(aVisa(), "card[exp_year]", "2026"), "card[exp_month]", "10"));

    assertThat(created.httpStatus()).isEqualTo(200);
  }

  @ParameterizedTest
  @ValueSource(strings = {"99", "12345", "12a", "1 2 3"})
  void a_security_code_of_other_than_three_or_four_digits_is_an_invalid_cvc(String cvc) {
    StripeApiResponse refused = world.createMethod(SECRET_KEY, with(aVisa(), "card[cvc]", cvc));

    assertThatACardError(refused, "invalid_cvc", "Your card's security code is invalid.", "cvc");
  }

  @ParameterizedTest
  @ValueSource(strings = {"1234", ""})
  void a_four_digit_or_a_blank_security_code_is_accepted(String cvc) {
    assertThat(world.createMethod(SECRET_KEY, with(aVisa(), "card[cvc]", cvc)).httpStatus())
        .isEqualTo(200);
  }

  @Test
  void a_card_with_no_security_code_is_accepted() {
    assertThat(world.createMethod(SECRET_KEY, with(aVisa(), "card[cvc]", null)).httpStatus())
        .isEqualTo(200);
  }

  @ParameterizedTest
  @CsvSource({
    "billing_details,a name,billing_details",
    "billing_details[address],somewhere,billing_details[address]",
    "metadata,a note,metadata",
    "allow_redisplay,sometimes,allow_redisplay"
  })
  void a_hash_sent_as_a_value_or_a_value_stripe_does_not_publish_is_refused(
      String name, String sent, String param) {
    StripeApiResponse refused = world.createMethod(SECRET_KEY, with(aVisa(), name, sent));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(flat(refused)).containsEntry("error.param", List.of(param));
    assertThat(world.theMethodsStored()).isEmpty();
  }

  @Test
  void an_unpublished_redisplay_is_answered_with_the_values_stripe_publishes() {
    StripeApiResponse refused =
        world.createMethod(SECRET_KEY, with(aVisa(), "allow_redisplay", "sometimes"));

    assertThat(theValueAt(refused, "error.message"))
        .isEqualTo("Invalid allow_redisplay: must be one of always, limited, or unspecified");
  }

  @Test
  void a_billing_value_longer_than_stripe_accepts_is_refused_and_one_as_long_is_kept() {
    StripeApiResponse refused =
        world.createMethod(SECRET_KEY, with(aVisa(), "billing_details[name]", "n".repeat(5001)));
    StripeApiResponse created =
        world.createMethod(SECRET_KEY, with(aVisa(), "billing_details[name]", "n".repeat(5000)));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(flat(refused)).containsEntry("error.param", List.of("billing_details[name]"));
    assertThat(created.httpStatus()).isEqualTo(200);
    assertThat(world.theMethodsStored()).hasSize(1);
  }

  @Test
  void metadata_past_the_published_limits_is_refused_and_metadata_at_them_is_kept() {
    StripeApiResponse aLongKey =
        world.createMethod(SECRET_KEY, with(aVisa(), "metadata[" + "k".repeat(41) + "]", "v"));
    StripeApiResponse aLongValue =
        world.createMethod(SECRET_KEY, with(aVisa(), "metadata[note]", "v".repeat(501)));
    Map<String, String> fiftyOne = aVisa();
    Map<String, String> fifty = aVisa();
    for (int key = 0; key < 51; key++) {
      fiftyOne.put("metadata[key" + key + "]", "v");
      if (key < 50) {
        fifty.put("metadata[key" + key + "]", "v");
      }
    }
    StripeApiResponse tooMany = world.createMethod(SECRET_KEY, fiftyOne);
    StripeApiResponse atTheLimits =
        world.createMethod(
            SECRET_KEY,
            with(
                with(fifty, "metadata[key0]", "v".repeat(500)),
                "metadata[" + "k".repeat(40) + "]",
                null));

    assertThat(flat(aLongKey))
        .containsEntry("error.param", List.of("metadata[" + "k".repeat(41) + "]"));
    assertThat(flat(aLongValue)).containsEntry("error.param", List.of("metadata[note]"));
    assertThat(flat(tooMany)).containsEntry("error.param", List.of("metadata"));
    assertThat(atTheLimits.httpStatus()).as(bodyOf(atTheLimits)).isEqualTo(200);
    assertThat(world.theMethodsStored()).hasSize(1);
  }

  @Test
  void a_method_another_account_created_is_answered_as_no_such_method() {
    world.createMethod(SECRET_KEY, aVisa());

    assertThat(world.retrieveMethod(SECRET_KEY, THE_FIRST_MINTED_METHOD).httpStatus())
        .isEqualTo(200);
    assertThat(world.retrieveMethod(ANOTHER_SECRET_KEY, THE_FIRST_MINTED_METHOD).httpStatus())
        .isEqualTo(404);
  }

  @Test
  void a_method_another_run_created_is_answered_as_no_such_method() {
    world.createMethod(ELSEWHERE_SECRET_KEY, aVisa());

    assertThat(world.theMethodsStored())
        .singleElement()
        .satisfies(method -> assertThat(method.testRunId()).isEqualTo(StripeCardWorld.ANOTHER_RUN));
    assertThat(world.retrieveMethod(SECRET_KEY, THE_FIRST_MINTED_METHOD).httpStatus())
        .isEqualTo(404);
  }

  @Test
  void the_number_and_the_cvc_are_withheld_from_the_request_row_and_the_rest_is_kept() {
    world.createMethodUnderTheKey(SECRET_KEY, aVisa());

    AuditRecord inbound =
        world.audit.all().stream()
            .filter(record -> record.direction() == AuditDirection.INBOUND_REQUEST)
            .findFirst()
            .orElseThrow();
    assertThat(inbound.sanitizedBody())
        .containsEntry("card[number]", List.of("[redacted]"))
        .containsEntry("card[cvc]", List.of("[redacted]"))
        .containsEntry("card[exp_month]", List.of("10"))
        .containsEntry("card[exp_year]", List.of("2028"))
        .containsEntry("type", List.of("card"));
    assertThat(world.everythingTheTrailAndTheExchangesKeep())
        .doesNotContain("4242424242424242")
        .doesNotContain("=[100]")
        .doesNotContain("\"100\"");
  }

  @Test
  void a_card_number_is_withheld_under_any_spelling_and_under_any_name() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("type", "card");
    form.put("card[number][0]", "4000056655665556");
    form.put("Card[Number]", "5555555555554444");
    form.put("number", "4000058260000005");
    form.put("metadata[note]", "3530 1113 3330 0000");
    form.put("card[cvc", "424");

    world.createMethod(SECRET_KEY, form);

    assertThat(world.everythingTheTrailAndTheExchangesKeep())
        .doesNotContain("4000056655665556")
        .doesNotContain("5555555555554444")
        .doesNotContain("4000058260000005")
        .doesNotContain("3530 1113 3330 0000")
        .doesNotContain("424]");
  }

  @Test
  void a_replay_under_the_same_key_answers_the_method_it_created_and_creates_no_second() {
    StripeApiResponse first = world.createMethodUnderTheKey(SECRET_KEY, aVisa());
    StripeApiResponse again = world.createMethodUnderTheKey(SECRET_KEY, aVisa());

    assertThat(theValueAt(again, "id")).isEqualTo(theValueAt(first, "id"));
    assertThat(again.headers()).containsKey(StripeFields.IDEMPOTENT_REPLAYED_HEADER);
    assertThat(world.theMethodsStored()).hasSize(1);
    assertThat(world.everythingTheTrailAndTheExchangesKeep()).doesNotContain("4242424242424242");
  }

  @Test
  void a_refusal_claims_no_key_so_a_corrected_request_under_it_creates_the_method() {
    StripeApiResponse refused =
        world.createMethodUnderTheKey(
            SECRET_KEY, with(aVisa(), "card[number]", "4242424242424241"));
    StripeApiResponse created = world.createMethodUnderTheKey(SECRET_KEY, aVisa());

    assertThat(refused.httpStatus()).isEqualTo(402);
    assertThat(created.httpStatus()).isEqualTo(200);
    assertThat(created.headers())
        .doesNotHave(aHeaderOfThisNameUnderAnySpelling(StripeFields.IDEMPOTENT_REPLAYED_HEADER));
  }

  @Test
  void the_method_a_secret_key_created_is_listed_with_the_accounts_other_methods() {
    world.seedAFixture(ACCOUNT, "pm_a_fixture_of_this_account", null);
    world.createMethod(SECRET_KEY, aVisa());

    StripeApiResponse listed =
        world
            .api()
            .listPaymentMethods(
                new StripeApiCall(
                    "GET",
                    StripeCardWorld.PAYMENT_METHODS,
                    StripeCardWorld.bearer(SECRET_KEY),
                    Map.of(),
                    Map.of()));

    assertThat(flat(listed).get("data.id"))
        .containsExactly(THE_FIRST_MINTED_METHOD, "pm_a_fixture_of_this_account");
    assertThat(RUN).isEqualTo(world.theMethodStored(THE_FIRST_MINTED_METHOD).testRunId());
  }

  private void assertThatACardError(
      StripeApiResponse refused, String code, String message, String param) {
    assertThat(refused.httpStatus()).as(bodyOf(refused)).isEqualTo(402);
    assertThat(flat(refused))
        .containsEntry("error.type", List.of("card_error"))
        .containsEntry("error.code", List.of(code))
        .containsEntry("error.message", List.of(message))
        .containsEntry("error.param", List.of(param))
        .containsEntry(
            "error.doc_url",
            List.of("https://docs.stripe.com/error-codes#" + code.replace('_', '-')))
        .doesNotContainKey("error.decline_code");
    assertThat(world.theMethodsStored()).isEmpty();
  }

  private static List<String> membersOf(String body) {
    return new ArrayList<>(
        ((StripeJson.JsonObject) StripeJsonReader.read(body)).members().keySet());
  }
}
