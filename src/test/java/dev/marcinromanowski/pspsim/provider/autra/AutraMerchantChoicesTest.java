package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AutraMerchantChoicesTest {

  @Test
  void the_two_answers_a_refused_credential_reads_as_are_offered_as_what_comes_back() {
    assertThat(AutraOauthFailure.acceptedValues())
        .containsExactly(
            new AcceptedValue(
                "BAD_REQUEST_WITH_ERROR_STRING",
                "a refused credential comes back 400 with a body that carries the message, as"
                    + " autra's reference declares it"),
            new AcceptedValue(
                "UNAUTHORIZED_INVALID_CLIENT",
                "a refused credential comes back 401 invalid_client, as autra's setup guide"
                    + " answers it"));
  }

  @Test
  void the_three_statuses_an_unknown_merchant_answers_at_are_all_three_offered() {
    assertThat(valuesOf(AutraMerchantNotFound.SETTING))
        .containsExactly("FORBIDDEN", "NOT_FOUND", "UNPROCESSABLE");
    assertThat(sentencesOf(AutraMerchantNotFound.SETTING))
        .as("the label said \"the MERCHANT_NOT_FOUND status\" and named none of the three")
        .anySatisfy(said -> assertThat(said).contains("403"))
        .anySatisfy(said -> assertThat(said).contains("404"))
        .anySatisfy(said -> assertThat(said).contains("422"));
  }

  @Test
  void the_values_offered_for_each_field_are_every_value_that_field_has() {
    assertThat(valuesOf(AutraOauthFailure.SETTING))
        .containsExactlyElementsOf(namesOf(AutraOauthFailure.values()));
    assertThat(valuesOf(AutraOauthFieldCasing.SETTING))
        .containsExactlyElementsOf(namesOf(AutraOauthFieldCasing.values()));
    assertThat(valuesOf(AutraRequestShape.PRE_AUTHORIZATION_SETTING))
        .containsExactlyElementsOf(namesOf(AutraRequestShape.values()));
    assertThat(valuesOf(AutraRequestShape.VOID_SETTING))
        .containsExactlyElementsOf(namesOf(AutraRequestShape.values()));
    assertThat(valuesOf(AutraMerchantNotFound.SETTING))
        .containsExactlyElementsOf(namesOf(AutraMerchantNotFound.values()));
    assertThat(valuesOf(AutraSaleDecline.SETTING))
        .containsExactlyElementsOf(namesOf(AutraSaleDecline.values()));
    assertThat(valuesOf(AutraAcquirerReport.SETTING))
        .containsExactlyElementsOf(namesOf(AutraAcquirerReport.values()));
  }

  @Test
  void every_value_a_field_offers_is_one_this_provider_takes() {
    takes(AutraOauthFailure.SETTING, AutraOauthFailure::of);
    takes(AutraOauthFieldCasing.SETTING, AutraOauthFieldCasing::of);
    takes(AutraRequestShape.PRE_AUTHORIZATION_SETTING, AutraRequestShape::of);
    takes(AutraRequestShape.VOID_SETTING, AutraRequestShape::of);
    takes(AutraMerchantNotFound.SETTING, AutraMerchantNotFound::of);
    takes(AutraSaleDecline.SETTING, AutraSaleDecline::of);
    takes(AutraAcquirerReport.SETTING, AutraAcquirerReport::of);
  }

  @ParameterizedTest
  @CsvSource({
    AutraMerchant.CLIENT_ID_PROPERTY,
    AutraMerchant.CLIENT_SECRET_PROPERTY,
    AutraMerchant.DOCUMENT_ID_PROPERTY,
    AutraAuthorizerMessage.SETTING,
    AutraWebhookSecret.SETTING
  })
  void a_field_whose_values_are_not_a_closed_set_offers_none_and_stays_a_box(String key) {
    assertThat(field(key).acceptedValues())
        .as(
            "three credentials, a tenant secret, and a msg autra publishes as a grammar rather"
                + " than a list: none of them is a set this provider could write out")
        .isEmpty();
  }

  @Test
  void the_msg_this_provider_publishes_as_a_grammar_shows_an_example_of_that_grammar() {
    ConfigurableField msg = field(AutraAuthorizerMessage.SETTING);

    assertThat(msg.acceptedValues())
        .as("autra publishes a shape for this, not a list, so a tester types it")
        .isEmpty();
    assertThat(msg.example()).isEqualTo(AutraAuthorizerMessage.DEFAULT);
    assertThat(msg.whatItIsFor()).contains("two-digit issuer code");
  }

  @Test
  void the_three_statuses_an_unknown_merchant_answers_at_are_declared_with_the_route_each_is() {
    assertThat(AutraMerchantNotFound.acceptedValues())
        .as(
            "a form offers the sentence and never the enum name, so the three routes autra"
                + " answers an unknown merchant on are written out here as a tester reads them")
        .containsExactly(
            new AcceptedValue(
                "FORBIDDEN",
                "an unknown merchant comes back 403, as autra's card-payment reference answers"
                    + " it"),
            new AcceptedValue(
                "NOT_FOUND",
                "an unknown merchant comes back 404, as autra's Pix route and both read routes"
                    + " answer it"),
            new AcceptedValue(
                "UNPROCESSABLE",
                "an unknown merchant comes back 422, as autra's acquiring error table answers"
                    + " it"));
  }

  @Test
  void the_two_casings_a_token_response_spells_its_fields_in_are_declared_field_by_field() {
    assertThat(AutraOauthFieldCasing.acceptedValues())
        .containsExactly(
            new AcceptedValue(
                "CAMEL_CASE",
                "the token response spells its three fields accessToken, tokenType and expiresIn"),
            new AcceptedValue(
                "SNAKE_CASE",
                "the token response spells them access_token, token_type and expires_in"));
  }

  @Test
  void the_two_request_shapes_are_declared_as_the_document_each_one_comes_from() {
    assertThat(AutraRequestShape.acceptedValues())
        .containsExactly(
            new AcceptedValue(
                "PUBLISHED_SCHEMA",
                "the request must carry the fields autra's OpenAPI document declares, and one"
                    + " shaped the other way is refused"),
            new AcceptedValue(
                "PUBLISHED_GUIDE_EXAMPLE",
                "the request must carry the fields autra's prose guide calls this endpoint with,"
                    + " and one shaped the other way is refused"));
  }

  @Test
  void the_two_answers_a_declined_sale_comes_back_as_are_declared_with_the_body_each_carries() {
    assertThat(AutraSaleDecline.acceptedValues())
        .containsExactly(
            new AcceptedValue(
                "TWO_HUNDRED_WITH_ERRORS",
                "a declined card sale comes back 200, and the body carries paymentID beside"
                    + " errors[]"),
            new AcceptedValue(
                "UNPROCESSABLE_WITH_ERRORS",
                "a declined card sale comes back 422, and the body is the error envelope alone"
                    + " with no paymentID in it"));
  }

  private static void takes(String key, BiFunction<String, String, Object> parser) {
    for (String offered : valuesOf(key)) {
      assertThatNoException()
          .as(key + " offers " + offered)
          .isThrownBy(() -> parser.apply(key, offered));
    }
  }

  private static List<String> namesOf(Enum<?>[] values) {
    List<String> names = new ArrayList<>();
    for (Enum<?> value : values) {
      names.add(value.name());
    }
    return names;
  }

  private static List<String> valuesOf(String key) {
    List<String> values = new ArrayList<>();
    for (AcceptedValue accepted : field(key).acceptedValues()) {
      values.add(accepted.value());
    }
    return values;
  }

  private static List<String> sentencesOf(String key) {
    List<String> said = new ArrayList<>();
    for (AcceptedValue accepted : field(key).acceptedValues()) {
      said.add(accepted.whatItMeans());
    }
    return said;
  }

  private static ConfigurableField field(String key) {
    return AutraMerchant.ACCOUNT_FIELDS.field(key).orElseThrow();
  }
}
