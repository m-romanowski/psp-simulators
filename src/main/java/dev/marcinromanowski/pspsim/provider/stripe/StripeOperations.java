package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeyLifetime;
import dev.marcinromanowski.pspsim.kernel.spi.FailureCodes;
import dev.marcinromanowski.pspsim.kernel.spi.MatchFact;
import dev.marcinromanowski.pspsim.kernel.spi.ProcessingStates;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOperation;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderVocabulary;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class StripeOperations {

  private StripeOperations() {}

  static final String CREATE = "POST /v1/payment_intents";

  static final String CONFIRM = "POST /v1/payment_intents/{id}/confirm";

  static final String CAPTURE = "POST /v1/payment_intents/{id}/capture";

  static final String CANCEL = "POST /v1/payment_intents/{id}/cancel";

  static final String REFUND = "POST /v1/refunds";

  static final String RETRIEVE = "GET /v1/payment_intents/{id}";

  static final String RETRIEVE_CHARGE = "GET /v1/charges/{id}";

  static final String CREATE_CUSTOMER = "POST /v1/customers";

  static final String RETRIEVE_CUSTOMER = "GET /v1/customers/{id}";

  static final String UPDATE_CUSTOMER = "POST /v1/customers/{id}";

  static final String DELETE_CUSTOMER = "DELETE /v1/customers/{id}";

  static final String LIST_PAYMENT_METHODS = "GET /v1/payment_methods";

  static final String LIST_CUSTOMER_PAYMENT_METHODS = "GET /v1/customers/{id}/payment_methods";

  static final String RETRIEVE_PAYMENT_METHOD = "GET /v1/payment_methods/{id}";

  static final String DETACH_PAYMENT_METHOD = "POST /v1/payment_methods/{id}/detach";

  static final String CREATE_SETUP_INTENT = "POST /v1/setup_intents";

  static final String CONFIRM_SETUP_INTENT = "POST /v1/setup_intents/{id}/confirm";

  static final String CREATE_EPHEMERAL_KEY = "POST /v1/ephemeral_keys";

  static final String SETUP_INTENT =
      "the SetupIntent the call is about, which provider stripe names by the id it draws when the"
          + " setup is created and which the confirmation that attaches the payment method to the"
          + " customer addresses";

  static final String PAYMENT_INTENT =
      "the PaymentIntent the call is about, which provider stripe names by the id it draws when"
          + " the intent is created and which every later call of that payment addresses";

  private static final String NO_ORDER_ID =
      "provider stripe publishes no order identifier on any endpoint of this contract: a merchant's"
          + " own reference travels in metadata or description, and neither of them addresses"
          + " anything the API can be asked for";

  private static final String DECLINE_CODES =
      "provider stripe answers a payment failure as an error object whose type is card_error, and"
          + " a scenario names the failure by the decline the published test cards produce:"
          + " generic_decline and insufficient_funds are answered as code card_declined with that"
          + " decline_code beside it, expired_card, incorrect_cvc and processing_error are answered"
          + " as codes of their own, and authentication_required is answered with the published"
          + " decline_code authentication_not_handled. A bare card_declined is not among them,"
          + " because Stripe publishes that a declined card always carries the decline_code that"
          + " says why";

  static final ProviderVocabulary VOCABULARY =
      new ProviderVocabulary(
          StripeApi.STRIPE,
          List.of(
              new ProviderOperation(
                  CREATE,
                  Set.of(
                      MatchFact.MERCHANT_ID,
                      MatchFact.AMOUNT,
                      MatchFact.CURRENCY,
                      MatchFact.CUSTOMER_ID,
                      MatchFact.PAYMENT_METHOD_ID),
                  PAYMENT_INTENT,
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE),
              new ProviderOperation(
                  CONFIRM,
                  Set.of(
                      MatchFact.MERCHANT_ID,
                      MatchFact.PROVIDER_OBJECT_ID,
                      MatchFact.PAYMENT_METHOD_ID),
                  PAYMENT_INTENT,
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE),
              new ProviderOperation(
                  CAPTURE,
                  Set.of(MatchFact.MERCHANT_ID, MatchFact.PROVIDER_OBJECT_ID, MatchFact.AMOUNT),
                  PAYMENT_INTENT,
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE),
              new ProviderOperation(
                  CANCEL,
                  Set.of(MatchFact.MERCHANT_ID, MatchFact.PROVIDER_OBJECT_ID),
                  PAYMENT_INTENT,
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE),
              new ProviderOperation(
                  REFUND,
                  Set.of(MatchFact.MERCHANT_ID, MatchFact.PROVIDER_OBJECT_ID, MatchFact.AMOUNT),
                  PAYMENT_INTENT,
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE),
              new ProviderOperation(
                  CONFIRM_SETUP_INTENT,
                  Set.of(
                      MatchFact.MERCHANT_ID,
                      MatchFact.PROVIDER_OBJECT_ID,
                      MatchFact.CUSTOMER_ID,
                      MatchFact.PAYMENT_METHOD_ID),
                  SETUP_INTENT,
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE)),
          Map.of(MatchFact.ORDER_ID, NO_ORDER_ID),
          new FailureCodes(StripeDecline.theCodesAScenarioMayName(), DECLINE_CODES),
          ProcessingStates.onTheseOperations(CREATE, CONFIRM));
}
