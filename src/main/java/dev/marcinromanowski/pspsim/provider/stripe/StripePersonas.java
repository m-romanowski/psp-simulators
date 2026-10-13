package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioPreset;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredPersona;
import dev.marcinromanowski.pspsim.kernel.spi.PersonaFixture;
import dev.marcinromanowski.pspsim.kernel.spi.PersonaIntent;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPersonas;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class StripePersonas {

  static final String THE_OWNER = "a-customer-stripe-holds-a-card-for";

  static final String NOTHING_IS_PUBLISHED_FOR_IT = "unpublished";

  static final String THE_PUBLISHED_PAYMENT_METHOD_ID = "pm_card_visa";

  static final String THE_PUBLISHED_LAST4 = "4242";

  static final int A_FUTURE_EXPIRATION_MONTH = 12;

  static final int A_FUTURE_EXPIRATION_YEAR = 2030;

  static final String HOW_THE_IDS_ARE_MINTED =
      "the customer is minted by CreateCustomer.aCustomerId, the same minting POST /v1/customers"
          + " uses, so the id reads cus_sim_ and sixteen hexadecimal characters and this account's"
          + " own key retrieves it. The payment method carries the published identifier"
          + " "
          + THE_PUBLISHED_PAYMENT_METHOD_ID
          + " rather than a minted one, because this simulator mints no PaymentMethod id anywhere:"
          + " a payment method exists here only where a fixture seeded one, and an id invented for"
          + " it would be a shape stripe never published";

  static final String THE_CARD_FACTS_ARE_THE_PUBLISHED_SUCCESS_CARD =
      "the card facts are the published success card, whose pan ends 4242; the expiry reads"
          + " 12/2030 because stripe publishes no expiry per test card, and brand and funding read"
          + " "
          + NOTHING_IS_PUBLISHED_FOR_IT
          + " because the pages this contract was read from publish the names of those fields and"
          + " not their values for any one card";

  static final String AN_INSTRUMENT_BELONGS_TO_AN_ACCOUNT =
      "seed this persona into an account this test run configures for stripe: every route that"
          + " answers for a customer or a payment method resolves it through the secret key that"
          + " authenticated the call";

  static final String ONE_PUBLISHED_IDENTIFIER_MEANS_ONE_AT_A_TIME =
      "a test run holds one of these personas at a time, because all three seed the one published"
          + " "
          + THE_PUBLISHED_PAYMENT_METHOD_ID
          + " and a run claims a payment-method id for one instrument; seed the others into runs of"
          + " their own";

  private StripePersonas() {}

  static ProviderPersonas of(IdGenerator ids) {
    return new ProviderPersonas(
        StripeApi.STRIPE,
        List.of(
            personaOf(
                ids,
                PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS,
                "stripe-customer-with-visa-success",
                ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_SUCCESS,
                "A customer with a saved Visa this run will hold and capture. "
                    + THE_CARD_FACTS_ARE_THE_PUBLISHED_SUCCESS_CARD),
            personaOf(
                ids,
                PersonaIntent.A_SAVED_CARD_THAT_IS_DECLINED,
                "stripe-customer-with-visa-that-is-declined",
                ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_DECLINE,
                "A customer with a saved Visa this run will hold and then decline on capture. "
                    + THE_CARD_FACTS_ARE_THE_PUBLISHED_SUCCESS_CARD),
            personaOf(
                ids,
                PersonaIntent.A_SAVED_CARD_WHOSE_CAPTURE_ANSWER_IS_LOST,
                "stripe-customer-whose-capture-answer-is-lost",
                ScenarioPreset.CAPTURE_ACCEPTED_RESPONSE_LOST,
                "A customer with a saved Visa whose capture this run drops the connection on, so"
                    + " the caller never learns whether the money moved. "
                    + THE_CARD_FACTS_ARE_THE_PUBLISHED_SUCCESS_CARD)),
        Map.of());
  }

  private static DeclaredPersona personaOf(
      IdGenerator ids,
      PersonaIntent intent,
      String name,
      ScenarioPreset preset,
      String whatItGives) {
    return new DeclaredPersona(
        intent,
        name,
        whatItGives
            + " "
            + AN_INSTRUMENT_BELONGS_TO_AN_ACCOUNT
            + ". "
            + ONE_PUBLISHED_IDENTIFIER_MEANS_ONE_AT_A_TIME,
        List.of(preset),
        List.of(
            new PersonaFixture(
                name,
                THE_OWNER,
                PaymentMethodKind.CARD,
                theCardItPublishes(),
                List.of(
                    ProviderPaymentMethodFields.PAYMENT_METHOD_ID,
                    ProviderPaymentMethodFields.CUSTOMER_ID),
                HOW_THE_IDS_ARE_MINTED,
                () -> theIdsOf(ids),
                0,
                Map.of(
                    ProviderPaymentMethodFields.PAYMENT_METHOD_ID,
                    THE_PUBLISHED_PAYMENT_METHOD_ID))));
  }

  private static Map<String, String> theIdsOf(IdGenerator ids) {
    Map<String, String> minted = new LinkedHashMap<>();
    minted.put(ProviderPaymentMethodFields.PAYMENT_METHOD_ID, THE_PUBLISHED_PAYMENT_METHOD_ID);
    minted.put(ProviderPaymentMethodFields.CUSTOMER_ID, CreateCustomer.aCustomerId(ids));
    return minted;
  }

  private static FixtureCard theCardItPublishes() {
    return new FixtureCard(
        NOTHING_IS_PUBLISHED_FOR_IT,
        THE_PUBLISHED_LAST4,
        A_FUTURE_EXPIRATION_MONTH,
        A_FUTURE_EXPIRATION_YEAR,
        NOTHING_IS_PUBLISHED_FOR_IT);
  }
}
