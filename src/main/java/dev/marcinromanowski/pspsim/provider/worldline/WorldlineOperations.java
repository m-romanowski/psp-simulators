package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeyLifetime;
import dev.marcinromanowski.pspsim.kernel.spi.FailureCodes;
import dev.marcinromanowski.pspsim.kernel.spi.MatchFact;
import dev.marcinromanowski.pspsim.kernel.spi.ProcessingStates;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOperation;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderVocabulary;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class WorldlineOperations {

  private WorldlineOperations() {}

  static final String PAYMENT_ID = "paymentId";

  static final String CREATE_HOSTED_TOKENIZATION = "POST /v2/{merchantId}/hostedtokenizations";

  static final String GET_HOSTED_TOKENIZATION =
      "GET /v2/{merchantId}/hostedtokenizations/{hostedTokenizationId}";

  static final String CREATE_PAYMENT = "POST /v2/{merchantId}/payments";

  static final String CAPTURE_PAYMENT = "POST /v2/{merchantId}/payments/{paymentId}/capture";

  static final String CANCEL_PAYMENT = "POST /v2/{merchantId}/payments/{paymentId}/cancel";

  static final String THE_TOKENIZATION_SESSION =
      "the hosted tokenization session the call is about, which provider worldline names by the"
          + " hostedTokenizationId it draws while answering the create and puts inside the"
          + " hostedTokenizationUrl a payer opens. That identifier is the one the read route takes"
          + " and the only one this session ever answers to: worldline mints a new identifier per"
          + " maintenance operation on a payment and publishes no maintenance operation on a"
          + " tokenization session at all";

  static final String THE_PAYMENT =
      "the payment the call is about, which provider worldline names by a payment.id of the"
          + " published form PAYID_PAYIDSUB. A story about it stands on the identifier the request"
          + " carried, and on the two maintenance routes that is an identifier the payment may"
          + " have replaced: \"Our platform returns a separate, unique payment.id for every"
          + " CreatePayment request and every maintenance operation\"";

  static final String NO_CUSTOMER_ON_A_TOKENIZATION =
      "provider worldline publishes order.customer.merchantCustomerId on a create-payment request"
          + " and this vocabulary carries it there. It publishes no customer object on the hosted"
          + " tokenization routes, so a rule naming a customer can never fire on one of those two";

  static final String NO_EVENT_TYPE_IN_THIS_SLICE =
      "provider worldline publishes thirteen payment webhook events and this slice serves none of"
          + " them: the operator scoped it to hosted tokenization, pre-authorization with its"
          + " capture and cancel, and the off-session charge, and put webhooks outside that."
          + " Nothing this module answers carries an event type, so a rule naming one would match"
          + " no request and shape no delivery";

  static final String NO_PROCESSING_OUTCOME_A_RULE_CAN_SETTLE =
      "provider worldline publishes accepted-but-unsettled statuses on both of the maintenance"
          + " operations this slice serves — CAPTURE_REQUESTED at statusCode 4, 91, 92 and 99, and"
          + " CANCELLED at 61 \"Author. deletion waiting\" and 62 \"Author. deletion uncertain\" —"
          + " and publishes exactly two ways for a caller to learn how one of them resolved: a"
          + " webhook, and GET /v2/{merchantId}/payments/{paymentId}/details. This slice serves"
          + " neither. A rule that scheduled a settled outcome behind one of those statuses would"
          + " therefore settle it where no surface of this module could show a tester the"
          + " difference, so no operation here is declared to answer with a processing state a"
          + " rule may settle. Which status a successful capture lands in is an account setting"
          + " instead, "
          + WorldlineCaptureLanding.SETTING;

  static final ProviderVocabulary VOCABULARY =
      new ProviderVocabulary(
          WorldlineApi.WORLDLINE,
          List.of(
              new ProviderOperation(
                  CREATE_HOSTED_TOKENIZATION,
                  Set.of(MatchFact.MERCHANT_ID),
                  THE_TOKENIZATION_SESSION,
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE),
              new ProviderOperation(
                  GET_HOSTED_TOKENIZATION,
                  Set.of(MatchFact.MERCHANT_ID, MatchFact.PROVIDER_OBJECT_ID),
                  THE_TOKENIZATION_SESSION,
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE),
              new ProviderOperation(
                  CREATE_PAYMENT,
                  Set.of(
                      MatchFact.MERCHANT_ID,
                      MatchFact.ORDER_ID,
                      MatchFact.AMOUNT,
                      MatchFact.CURRENCY,
                      MatchFact.CUSTOMER_ID,
                      MatchFact.PAYMENT_METHOD_ID),
                  THE_PAYMENT,
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE),
              new ProviderOperation(
                  CAPTURE_PAYMENT,
                  Set.of(MatchFact.MERCHANT_ID, MatchFact.PROVIDER_OBJECT_ID, MatchFact.AMOUNT),
                  THE_PAYMENT,
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_MAY_REPLACE),
              new ProviderOperation(
                  CANCEL_PAYMENT,
                  Set.of(
                      MatchFact.MERCHANT_ID,
                      MatchFact.PROVIDER_OBJECT_ID,
                      MatchFact.AMOUNT,
                      MatchFact.CURRENCY),
                  THE_PAYMENT,
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_MAY_REPLACE)),
          Map.of(MatchFact.EVENT_TYPE, NO_EVENT_TYPE_IN_THIS_SLICE),
          new FailureCodes(
              WorldlineErrors.theCodesAScenarioMayName(), WorldlineErrors.THE_DECLINE_CODES),
          ProcessingStates.noneOfThem(NO_PROCESSING_OUTCOME_A_RULE_CAN_SETTLE));
}
