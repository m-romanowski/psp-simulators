package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StripeTestCardBehaviorTest {

  @ParameterizedTest
  @CsvSource({
    "GENERIC_DECLINE,card_declined,generic_decline",
    "INSUFFICIENT_FUNDS,card_declined,insufficient_funds",
    "LOST_CARD,card_declined,lost_card",
    "STOLEN_CARD,card_declined,stolen_card",
    "EXPIRED_CARD,expired_card,",
    "INCORRECT_CVC,incorrect_cvc,",
    "PROCESSING_ERROR,processing_error,",
    "VELOCITY_LIMIT,card_declined,card_velocity_exceeded",
    "CHARGE_CUSTOMER_FAIL,card_declined,generic_decline"
  })
  void a_stored_behavior_restores_the_decline_used_by_a_payment(
      String stored, String code, @Nullable String declineCode) {
    StripeTestCardBehavior behavior = StripeTestCardBehavior.storedAs(stored);
    assertThat(behavior).isNotNull();
    StripeDecline decline = behavior.outcomeOf("POST /v1/payment_intents/{id}/confirm");
    assertThat(decline).isNotNull();
    assertThat(decline.errorCode()).isEqualTo(code);
    assertThat(decline.declineCode()).isEqualTo(declineCode);
  }

  @Test
  void an_ordinary_stored_card_carries_no_implicit_behavior() {
    assertThat(StripeTestCardBehavior.storedAs(null)).isNull();
  }
}
