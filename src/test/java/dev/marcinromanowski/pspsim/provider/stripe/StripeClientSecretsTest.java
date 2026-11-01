package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.TheSpellingsOfAHeaderName.aHeaderOfThisNameUnderAnySpelling;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.PUBLISHABLE_KEY;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.SECRET_KEY;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.aVisa;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.bodyOf;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StripeClientSecretsTest {

  @ParameterizedTest
  @CsvSource({
    "payment_intent,No such payment_intent: 'pi_missing'",
    "setup_intent,No such setupintent: 'seti_missing'"
  })
  void a_missing_intent_is_refused_before_an_existing_key_can_turn_it_into_an_idempotency_conflict(
      String kind, String message) {
    StripeCardWorld world = new StripeCardWorld();
    StripeApiResponse original = world.createMethodUnderTheKey(SECRET_KEY, aVisa());
    assertThat(original.httpStatus()).isEqualTo(200);

    StripeApiResponse missing =
        "payment_intent".equals(kind)
            ? world.confirmPaymentUnderTheKey(
                PUBLISHABLE_KEY,
                "pi_missing",
                Map.of(
                    "client_secret",
                    "pi_missing_secret_no_intent",
                    "payment_method",
                    "pm_card_visa"))
            : world.confirmSetupUnderTheKey(
                PUBLISHABLE_KEY,
                "seti_missing",
                Map.of(
                    "client_secret",
                    "seti_missing_secret_no_intent",
                    "payment_method",
                    "pm_card_visa"));

    assertThat(missing.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(missing))
        .isEqualTo(
            "{\"error\":{\"type\":\"invalid_request_error\",\"code\":\"resource_missing\",\"doc_url\":\"https://docs.stripe.com/error-codes#resource-missing\",\"message\":\""
                + message
                + "\",\"param\":\"intent\"}}");
    assertThat(missing.headers())
        .doesNotHave(aHeaderOfThisNameUnderAnySpelling("Idempotent-Replayed"));
    assertThat(world.intents.all()).isEmpty();
    assertThat(world.setupIntents.all()).isEmpty();
    assertThat(world.charges.all()).isEmpty();
    assertThat(world.theMethodsStored())
        .extracting(StripePaymentMethod::paymentMethodId)
        .containsExactly("pm_sim_00000000000000000001");

    StripeApiResponse replayed = world.createMethodUnderTheKey(SECRET_KEY, aVisa());
    assertThat(replayed.httpStatus()).isEqualTo(200);
    assertThat(replayed.headers()).containsEntry("Idempotent-Replayed", java.util.List.of("true"));
    assertThat(bodyOf(replayed)).isEqualTo(bodyOf(original));
  }
}
