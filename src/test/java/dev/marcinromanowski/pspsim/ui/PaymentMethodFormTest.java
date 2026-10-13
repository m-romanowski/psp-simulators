package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PaymentMethodFormTest {

  private static final ProviderId PROVIDER = ProviderId.of("aprovider");

  private static final String RUN = "payment-method-form-test";

  private static final String A_SENTENCE = "What this provider does with the value.";

  private static final String ANOTHER_SENTENCE = "What this provider does with the other value.";

  private static final ProviderPaymentMethodFields ONE_ID_REQUIRED_AND_ONE_NOT =
      new ProviderPaymentMethodFields(
          PROVIDER,
          List.of(
              new ConfigurableField(
                  ProviderPaymentMethodFields.PAYMENT_METHOD_ID,
                  "payment_method",
                  A_SENTENCE,
                  true,
                  false,
                  List.of(),
                  "a value a test does not read"),
              new ConfigurableField(
                  ProviderPaymentMethodFields.CUSTOMER_ID,
                  "customer",
                  ANOTHER_SENTENCE,
                  false,
                  false,
                  List.of(),
                  "a value a test does not read")),
          Map.of(ProviderPaymentMethodFields.TOKEN, "this provider mints no token of its own"),
          Set.of());

  @Test
  void the_type_a_form_was_asked_for_is_what_says_whether_it_describes_a_card() {
    assertThat(aFormSeeding("card").theTypeChosenDescribesACard())
        .as("the card boxes are read for a card")
        .isTrue();
    assertThat(aFormSeeding("wallet").theTypeChosenDescribesACard())
        .as("a wallet carries no card, and a type that describes none is refused for carrying one")
        .isFalse();
  }

  @Test
  void a_form_asks_for_what_this_simulator_owns_and_then_for_what_the_provider_declares() {
    assertThat(aFormSeeding("card").everyFieldThisFormAsksFor())
        .extracting(PaymentMethodForm.Field::key)
        .containsExactly(
            "merchantId",
            "ownerId",
            "type",
            "alias",
            "brand",
            "last4",
            "expMonth",
            "expYear",
            "funding",
            "defaultScenario",
            ProviderPaymentMethodFields.PAYMENT_METHOD_ID,
            ProviderPaymentMethodFields.CUSTOMER_ID);
  }

  @Test
  void a_card_is_refused_without_five_boxes_a_wallet_is_refused_without_none_of_them() {
    PaymentMethodForm.View aCard = aFormSeeding("card");
    PaymentMethodForm.View aWallet = aFormSeeding("wallet");

    assertThat(aCard.fieldsThisFormRequires())
        .as(
            "merchantId, ownerId, type and alias always, the five card properties while the type"
                + " describes a card, and the one provider-native id this provider requires")
        .isEqualTo(10);
    assertThat(aWallet.fieldsThisFormRequires())
        .as("the same four, none of the card five, and the same provider-native id")
        .isEqualTo(5);
    assertThat(aCard.fieldsThisFormHas()).isEqualTo(12);
    assertThat(aWallet.fieldsThisFormHas())
        .as("a wallet is asked for the same boxes; five of them are simply not refused empty")
        .isEqualTo(12);
  }

  @Test
  void a_box_the_seed_refuses_empty_says_so_and_a_box_it_does_not_says_nothing() {
    PaymentMethodForm.View aCard = aFormSeeding("card");

    assertThat(aCard.fieldNamed("merchantId").required())
        .as("a seed with no merchantId is refused, whatever the type is")
        .isTrue();
    assertThat(aCard.fieldNamed("merchantId").key()).isEqualTo("merchantId");
    assertThat(aCard.fieldNamed("merchantId").label()).isEqualTo("account");
    assertThat(aCard.fieldNamed("merchantId").whatItIsFor()).contains("merchantId");
    assertThat(aCard.fieldNamed("defaultScenario").required())
        .as("a fixture no scenario is written against is seeded like any other")
        .isFalse();
    assertThat(aCard.fieldNamed("defaultScenario").key()).isEqualTo("defaultScenario");
  }

  @Test
  void a_card_box_is_refused_empty_only_while_the_type_chosen_describes_a_card() {
    assertThat(aFormSeeding("card").fieldNamed("brand").required()).isTrue();
    assertThat(aFormSeeding("card").fieldNamed("last4").required()).isTrue();
    assertThat(aFormSeeding("wallet").fieldNamed("brand").required()).isFalse();
    assertThat(aFormSeeding("wallet").fieldNamed("last4").required()).isFalse();
    assertThat(aFormSeeding("wallet").fieldNamed("brand").key()).isEqualTo("brand");
  }

  @Test
  void a_box_this_form_never_asks_for_is_refused_rather_than_drawn_with_a_label_and_a_badge() {
    PaymentMethodForm.View aCard = aFormSeeding("card");

    assertThatCode(() -> aCard.fieldNamed("cvc"))
        .as(
            "no request reaches this: every key the template passes is a literal and every key"
                + " everyFieldThisFormAsksFor passes comes off the same table, so only a typo in"
                + " the template arrives here. It is kept because a key this form never asks for"
                + " would otherwise draw a box with an empty label and no badge, on a form whose"
                + " rule is that an absent badge means nothing refuses that box")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cvc")
        .hasMessageContaining("a box the seed never reads");
  }

  private static PaymentMethodForm.View aFormSeeding(String type) {
    return PaymentMethodForm.of(
        "aprovider", RUN, ONE_ID_REQUIRED_AND_ONE_NOT, Map.of("type", type), List.of(), null);
  }
}
