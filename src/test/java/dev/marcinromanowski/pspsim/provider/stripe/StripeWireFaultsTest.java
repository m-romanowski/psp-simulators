package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class StripeWireFaultsTest {

  @Test
  void every_address_this_build_serves_for_stripe_is_inside_a_namespace_stripe_claims() {
    assertThat(StripeWireFaults.ROUTES.covers(StripeApiController.PAYMENT_INTENTS)).isTrue();
    assertThat(StripeWireFaults.ROUTES.covers(StripeApiController.PAYMENT_INTENTS + "/pi_1"))
        .isTrue();
    assertThat(StripeWireFaults.ROUTES.covers(StripeApiController.SETUP_INTENTS)).isTrue();
    assertThat(StripeWireFaults.ROUTES.covers(StripeApiController.PAYMENT_METHODS)).isTrue();
    assertThat(StripeWireFaults.ROUTES.covers(StripeApiController.CUSTOMERS + "/cus_1")).isTrue();
    assertThat(StripeWireFaults.ROUTES.covers(StripeApiController.CHARGES + "/ch_1")).isTrue();
    assertThat(StripeWireFaults.ROUTES.covers(StripeApiController.REFUNDS)).isTrue();
    assertThat(StripeWireFaults.ROUTES.covers(StripeApiController.EPHEMERAL_KEYS)).isTrue();
    assertThat(StripeWireFaults.ROUTES.provider()).isEqualTo(StripeApi.STRIPE);
  }

  @Test
  void no_address_of_another_provider_or_of_the_screens_is_claimed_for_stripe() {
    assertThat(StripeWireFaults.ROUTES.covers("/v1/acquiring/pix/checkout"))
        .as(
            "more than one provider of this build answers under /v1, so stripe claims the seven second"
                + " segments its own contract publishes and never /v1 itself")
        .isFalse();
    assertThat(StripeWireFaults.ROUTES.covers("/v1/oauth/token")).isFalse();
    assertThat(StripeWireFaults.ROUTES.covers("/v1/payment_intentsexual")).isFalse();
    assertThat(StripeWireFaults.ROUTES.covers("/ui/objects")).isFalse();
    assertThat(StripeWireFaults.ROUTES.covers("/__simulator/stripe/state")).isFalse();
  }

  @Test
  void an_address_stripe_does_not_answer_is_404_in_stripes_own_envelope_and_carries_no_code() {
    ProviderWireAnswer.InThisProvidersOwnWords answered =
        (ProviderWireAnswer.InThisProvidersOwnWords)
            StripeWireFaults.theAnswerTo(WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE);

    assertThat(answered.httpStatus()).isEqualTo(StripeErrors.NOT_FOUND);
    assertThat(answered.contentType()).isEqualTo(StripeApi.CONTENT_TYPE);
    String body = new String(answered.body(), StandardCharsets.UTF_8);
    assertThat(body)
        .contains("\"type\":\"" + StripeErrorType.INVALID_REQUEST_ERROR.wireValue() + "\"")
        .contains(StripeWireFaults.NO_ROUTE_ANSWERS_THIS_ADDRESS)
        .as(
            "stripe publishes a code only for errors a client can handle programmatically and"
                + " none for an unrecognized address, and a code it did not publish would come"
                + " with a doc_url pointing at an anchor that does not exist")
        .doesNotContain("\"code\"")
        .doesNotContain("doc_url");
  }

  @Test
  void a_method_an_address_does_not_answer_is_404_because_405_is_outside_the_published_statuses() {
    ProviderWireAnswer.InThisProvidersOwnWords answered =
        (ProviderWireAnswer.InThisProvidersOwnWords)
            StripeWireFaults.theAnswerTo(WireFault.A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER);

    assertThat(answered.httpStatus()).isEqualTo(StripeErrors.NOT_FOUND);
    assertThat(new String(answered.body(), StandardCharsets.UTF_8))
        .contains(StripeWireFaults.THE_ADDRESS_DOES_NOT_ANSWER_THIS_METHOD);
  }

  @Test
  void a_failure_inside_this_simulator_is_not_dressed_as_an_answer_stripe_gave() {
    ProviderWireAnswer answered =
        StripeWireFaults.theAnswerTo(WireFault.A_FAILURE_NOTHING_ELSE_ANSWERED);

    assertThat(answered)
        .as(
            "the wire routes of this build answer an undecidable call in text/plain so it can"
                + " never be read as stripe's, and a failure raised before the route is the same"
                + " failure")
        .isInstanceOf(ProviderWireAnswer.NothingThisContractPublishes.class);
    assertThat(
            ((ProviderWireAnswer.NothingThisContractPublishes) answered)
                .whatThisContractPublishesInstead())
        .isEqualTo(StripeWireFaults.WHAT_THIS_CONTRACT_PUBLISHES_FOR_A_FAILURE_INSIDE_IT);
  }

  @Test
  void the_two_faults_stripe_answers_itself_say_different_things_to_the_client() {
    String address =
        new String(
            ((ProviderWireAnswer.InThisProvidersOwnWords)
                    StripeWireFaults.theAnswerTo(WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE))
                .body(),
            StandardCharsets.UTF_8);
    String method =
        new String(
            ((ProviderWireAnswer.InThisProvidersOwnWords)
                    StripeWireFaults.theAnswerTo(WireFault.A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER))
                .body(),
            StandardCharsets.UTF_8);

    assertThat(address).isNotEqualTo(method);
  }
}
