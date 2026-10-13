package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.AFieldATesterFillsIn;
import dev.marcinromanowski.pspsim.kernel.spi.AValueAFieldOffers;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredOrigination;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOriginationFields;
import dev.marcinromanowski.pspsim.kernel.spi.ThePayerStep;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;

final class StripeOrigination {

  static final String A_HOLD = "capture_method=manual";

  static final String A_CHARGE = "capture_method=automatic";

  static final String A_CARD_SET_UP = "setup_intent";

  static final String A_CARD_SET_UP_AND_CONFIRMED = "setup_intent+confirm";

  static final String THE_SETUP_EXISTS_BUT_IS_NOT_CONFIRMED =
      " The SetupIntent itself was created and is on the Payments screen under the id it was given;"
          + " only the confirmation was refused, and nothing was attached to the customer.";

  private static final ThePayerStep THE_MERCHANTS_OWN_CALL_CONFIRMS_IT =
      new ThePayerStep.NoPageThisSimulatorServes(
          "Nothing waits for a payer here. Stripe publishes no page of its own for either of these"
              + " objects: what confirms one is a call the merchant's application makes with the"
              + " account's secret key, or a client secret it hands its own checkout, so this way"
              + " of starting leaves no page and no payer step at all.");

  private static final String IT_IS_LISTED_ALREADY_AS_A_SETUP =
      " The SetupIntent exists the moment this screen answers: it is on the Payments screen now,"
          + " under the id above, and the card it set up is on the Payment methods screen under"
          + " the customer it now belongs to.";

  private static final String IT_IS_LISTED_ALREADY =
      " The PaymentIntent exists the moment this screen answers: it is on the Payments screen now,"
          + " under the id above.";

  private static final AFieldATesterFillsIn AMOUNT =
      AFieldATesterFillsIn.typed(
          StripeFields.AMOUNT,
          "amount, in the currency's smallest unit",
          true,
          "Stripe counts money in the smallest unit of the currency, so 10.00 US dollars is 1000"
              + " and not 10. It refuses anything that is not a positive whole number.",
          "1000");

  private static final AFieldATesterFillsIn CURRENCY =
      AFieldATesterFillsIn.typed(
          StripeFields.CURRENCY,
          "currency",
          true,
          "The three-letter code Stripe charges in. It decides what the amount above counts in.",
          "usd");

  private static final AFieldATesterFillsIn CONFIRM =
      AFieldATesterFillsIn.chosenFrom(
          StripeFields.CONFIRM,
          "confirm it now, with a payment method",
          false,
          "Stripe can create a PaymentIntent and confirm it in the same call. Confirming it needs"
              + " a payment method, and a PaymentIntent created without one waits in"
              + " requires_payment_method until something confirms it.",
          List.of(
              new AValueAFieldOffers(
                  StripeFlag.TRUE, "yes, confirm it now with the payment method below"),
              new AValueAFieldOffers(
                  StripeFlag.FALSE, "no, leave it waiting for a payment method")));

  private static final AFieldATesterFillsIn PAYMENT_METHOD =
      AFieldATesterFillsIn.typed(
          StripeFields.PAYMENT_METHOD,
          "the payment method to charge",
          false,
          "The provider-native payment-method id of a card seeded on the Payment methods screen."
              + " Stripe requires one on a call that confirms, and refuses that call by name"
              + " without it.",
          StripePersonas.THE_PUBLISHED_PAYMENT_METHOD_ID);

  private static final AFieldATesterFillsIn CUSTOMER =
      AFieldATesterFillsIn.typed(
          StripeFields.CUSTOMER,
          "the customer the payment belongs to",
          false,
          "The provider-native customer id a seeded payment method is owned by. Stripe does not"
              + " require one to create a PaymentIntent.",
          "cus_simulated");

  private static final AFieldATesterFillsIn USAGE =
      AFieldATesterFillsIn.chosenFrom(
          StripeFields.USAGE,
          "how the card will be charged later",
          false,
          "Stripe records on the SetupIntent whether the later payment happens with the customer"
              + " present or not. It publishes off_session as the value it uses when the request"
              + " carries none.",
          List.of(
              new AValueAFieldOffers(
                  StripeSetupUsage.OFF_SESSION.wireValue(),
                  "off_session, charged when the customer is not there"),
              new AValueAFieldOffers(
                  StripeSetupUsage.ON_SESSION.wireValue(),
                  "on_session, charged while the customer is in the checkout")));

  private static final AFieldATesterFillsIn THE_METHOD_TO_SET_UP =
      AFieldATesterFillsIn.typed(
          StripeFields.PAYMENT_METHOD,
          "the payment method to set up",
          true,
          "The provider-native payment-method id of a card seeded on the Payment methods screen."
              + " This is the method the confirmation attaches to the customer, and stripe"
              + " publishes that a method already attached to another Customer cannot be used"
              + " here.",
          StripePersonas.THE_PUBLISHED_PAYMENT_METHOD_ID);

  private static final AFieldATesterFillsIn THE_METHOD_TO_SET_UP_LATER =
      AFieldATesterFillsIn.typed(
          StripeFields.PAYMENT_METHOD,
          "the payment method to set up, if you already know it",
          false,
          "The provider-native payment-method id of a card seeded on the Payment methods screen."
              + " Stripe does not require one to create a SetupIntent, and a confirmation may name"
              + " one instead.",
          StripePersonas.THE_PUBLISHED_PAYMENT_METHOD_ID);

  private static final AFieldATesterFillsIn THE_CUSTOMER_THE_CARD_IS_SAVED_TO =
      AFieldATesterFillsIn.typed(
          StripeFields.CUSTOMER,
          "the customer the card is saved to",
          true,
          "The provider-native customer id the confirmation attaches the payment method to."
              + " Stripe publishes that the SetupIntent's payment method is attached to the"
              + " Customer on successful setup, so this screen asks for one: a setup that named"
              + " no customer would attach the method to nobody.",
          "cus_simulated");

  private static final AFieldATesterFillsIn THE_CUSTOMER_THE_CARD_MAY_BE_SAVED_TO =
      AFieldATesterFillsIn.typed(
          StripeFields.CUSTOMER,
          "the customer the card would be saved to",
          false,
          "The provider-native customer id a later confirmation attaches the payment method to."
              + " Stripe does not require one to create a SetupIntent.",
          "cus_simulated");

  private static final List<AFieldATesterFillsIn> THE_FIELDS_A_PAYMENT_INTENT_CARRIES =
      List.of(AMOUNT, CURRENCY, CONFIRM, PAYMENT_METHOD, CUSTOMER);

  static final ProviderOriginationFields DECLARED =
      new ProviderOriginationFields(
          StripeApi.STRIPE,
          List.of(
              new DeclaredOrigination(
                  A_HOLD,
                  "Hold the funds on the card and capture them later",
                  "A PaymentIntent whose capture_method is manual. Confirmed, it stands in"
                      + " requires_capture and holds the funds until a capture or a cancel moves"
                      + " them; unconfirmed, it holds nothing yet."
                      + IT_IS_LISTED_ALREADY,
                  THE_MERCHANTS_OWN_CALL_CONFIRMS_IT,
                  THE_FIELDS_A_PAYMENT_INTENT_CARRIES),
              new DeclaredOrigination(
                  A_CHARGE,
                  "Take the funds as soon as it is confirmed",
                  "A PaymentIntent whose capture_method is automatic. Confirmed, it goes straight"
                      + " to succeeded and there is no hold left to capture; what is offered on it"
                      + " afterwards is a refund."
                      + IT_IS_LISTED_ALREADY,
                  THE_MERCHANTS_OWN_CALL_CONFIRMS_IT,
                  THE_FIELDS_A_PAYMENT_INTENT_CARRIES),
              new DeclaredOrigination(
                  A_CARD_SET_UP,
                  "Set a card up for a later charge and leave the setup waiting",
                  "A SetupIntent. It moves no money ever: stripe publishes it as the object that"
                      + " sets a payment method up for future payments, and until something"
                      + " confirms it nothing is attached to any customer. Confirming it is"
                      + " POST /v1/setup_intents/{setupIntentId}/confirm, which a merchant"
                      + " application drives with the account's secret key; this screen offers the"
                      + " other way of starting below, which confirms it for you."
                      + IT_IS_LISTED_ALREADY_AS_A_SETUP,
                  THE_MERCHANTS_OWN_CALL_CONFIRMS_IT,
                  List.of(
                      THE_CUSTOMER_THE_CARD_MAY_BE_SAVED_TO, THE_METHOD_TO_SET_UP_LATER, USAGE)),
              new DeclaredOrigination(
                  A_CARD_SET_UP_AND_CONFIRMED,
                  "Set a card up for a later charge and confirm it now",
                  "A confirmed SetupIntent, and the attachment it mints. It moves no money ever:"
                      + " on a successful setup stripe attaches the payment method to the"
                      + " customer, so the card is then listed under that customer and a"
                      + " PaymentIntent started here can charge it. The setup stands at succeeded"
                      + " and there is nothing left to confirm."
                      + IT_IS_LISTED_ALREADY_AS_A_SETUP,
                  THE_MERCHANTS_OWN_CALL_CONFIRMS_IT,
                  List.of(THE_CUSTOMER_THE_CARD_IS_SAVED_TO, THE_METHOD_TO_SET_UP, USAGE))),
          theFieldsTheSimulatorFillsIn());

  private static SequencedMap<String, String> theFieldsTheSimulatorFillsIn() {
    LinkedHashMap<String, String> filled = new LinkedHashMap<>();
    filled.put(
        StripeFields.CAPTURE_METHOD,
        "stripe decides on capture_method whether a PaymentIntent holds the funds or takes them,"
            + " and the way of starting the payment you picked above is that choice. A SetupIntent"
            + " carries no capture_method, because it moves no money at all.");
    filled.put(
        StripeFields.CLIENT_SECRET,
        "stripe publishes client_secret as the value a browser or a mobile application uses to"
            + " finish an intent with a publishable key. This simulator serves no publishable-key"
            + " surface, so it never puts a client_secret on a screen and never asks for one: the"
            + " confirmation this wizard runs is the published server-side confirm, authorised by"
            + " the secret key of the account you picked above.");
    return filled;
  }

  private StripeOrigination() {}

  static boolean setsACardUp(String startedAs) {
    return A_CARD_SET_UP.equals(startedAs) || A_CARD_SET_UP_AND_CONFIRMED.equals(startedAs);
  }

  static boolean confirmsTheSetupItself(String startedAs) {
    return A_CARD_SET_UP_AND_CONFIRMED.equals(startedAs);
  }

  static StripeForm formOf(OriginationRequest request) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    for (Map.Entry<String, String> value : request.values().entrySet()) {
      fields.put(value.getKey(), List.of(value.getValue()));
    }
    if (!setsACardUp(request.startedAs())) {
      fields.put(StripeFields.CAPTURE_METHOD, List.of(theMethodThatCarries(request).wireValue()));
    }
    return StripeForm.of(fields);
  }

  static StripeForm theConfirmationOf(String paymentMethod) {
    return StripeForm.of(Map.of(StripeFields.PAYMENT_METHOD, List.of(paymentMethod)));
  }

  private static StripeCaptureMethod theMethodThatCarries(OriginationRequest request) {
    return A_CHARGE.equals(request.startedAs())
        ? StripeCaptureMethod.AUTOMATIC
        : StripeCaptureMethod.MANUAL;
  }

  static OriginationOutcome outcomeOf(StripeOperationResult result, OriginationRequest request) {
    if (result.outcome() == ProviderOutcome.UNKNOWN) {
      return new OriginationOutcome.AnsweredByAScenarioInstead(String.valueOf(result.scenarioId()));
    }
    if (result.outcome() != ProviderOutcome.SUCCESS) {
      return new OriginationOutcome.RefusedByTheProvider(
          StripeObjectActions.theWordsOf(result), result.scenarioId());
    }
    return new OriginationOutcome.Originated(
        String.valueOf(result.providerObjectId()),
        DECLARED.wayNamed(request.startedAs()).orElseThrow().whatItLeavesBehind(),
        result.scenarioId());
  }

  static OriginationOutcome theConfirmationOutcomeOf(
      StripeOperationResult result,
      OriginationRequest request,
      OriginationOutcome.Originated created) {
    if (result.outcome() == ProviderOutcome.UNKNOWN) {
      return new OriginationOutcome.AnsweredByAScenarioInstead(String.valueOf(result.scenarioId()));
    }
    if (result.outcome() != ProviderOutcome.SUCCESS) {
      return new OriginationOutcome.RefusedByTheProvider(
          StripeObjectActions.theWordsOf(result) + THE_SETUP_EXISTS_BUT_IS_NOT_CONFIRMED,
          result.scenarioId());
    }
    return new OriginationOutcome.Originated(
        created.providerObjectId(),
        DECLARED.wayNamed(request.startedAs()).orElseThrow().whatItLeavesBehind(),
        result.scenarioId());
  }
}
