package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.AFieldATesterFillsIn;
import dev.marcinromanowski.pspsim.kernel.spi.AValueAFieldOffers;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredOrigination;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOriginationFields;
import dev.marcinromanowski.pspsim.kernel.spi.ThePayerStep;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;

final class WorldlineOrigination {

  static final String A_TOKENIZATION = WorldlineOperations.CREATE_HOSTED_TOKENIZATION;

  static final String A_PAYMENT = WorldlineOperations.CREATE_PAYMENT;

  static final String WIRE_METHOD = "POST";

  private static final String IT_IS_LISTED_ALREADY =
      " The object exists the moment this screen answers: it is on the Payments screen now, under"
          + " the identifier above, and the actions offered there act on it.";

  private static final ThePayerStep THE_HOSTED_TOKENIZATION_PAGE =
      new ThePayerStep.APageThisSimulatorServes(WorldlineHostedTokenizationPage.ROUTE);

  private static final ThePayerStep THE_CARD_IS_ALREADY_STORED =
      new ThePayerStep.NoPageThisSimulatorServes(
          "Nothing waits for a payer here. A payment charges a card that was stored before it, so"
              + " worldline answers this call with the payment already decided and there is no"
              + " page and no payer step at all.");

  private static final AFieldATesterFillsIn TOKENS =
      AFieldATesterFillsIn.typed(
          WorldlineFields.TOKENS,
          "tokens the page should already know about, comma separated",
          false,
          "Worldline publishes invalidTokens and expiredCardTokens on the create response and"
              + " publishes no request field they could be about, so this simulator reads a"
              + " tokens array on the request and answers invalidTokens with the ones this test"
              + " run holds no card under. That is an inference from the two published response"
              + " arrays and not a published request field.",
          null);

  private static final AFieldATesterFillsIn AMOUNT =
      AFieldATesterFillsIn.typed(
          WorldlineFields.AMOUNT,
          "amount in minor units",
          true,
          "Worldline types order.amountOfMoney.amount as integer/int64 and glosses it \"Amount in"
              + " the smallest currency unit\", with a published minimum of 0 and a published"
              + " maximum of 999999999999. 5000 is fifty of a two-decimal currency, not five"
              + " thousand.",
          "5000");

  private static final AFieldATesterFillsIn CURRENCY_CODE =
      AFieldATesterFillsIn.typed(
          WorldlineFields.CURRENCY_CODE,
          "currency code",
          true,
          "Worldline publishes order.amountOfMoney.currencyCode as required and exactly three"
              + " characters, and writes PLN in the examples this contract was read from.",
          "PLN");

  private static final AFieldATesterFillsIn MERCHANT_REFERENCE =
      AFieldATesterFillsIn.typed(
          WorldlineFields.MERCHANT_REFERENCE,
          "the merchant's own order reference",
          false,
          "Worldline carries it at order.references.merchantReference with maxLength 40 and"
              + " publishes no uniqueness rule for it anywhere, so this simulator refuses no"
              + " repeat of one.",
          "order-123");

  private static final AFieldATesterFillsIn MERCHANT_CUSTOMER_ID =
      AFieldATesterFillsIn.typed(
          WorldlineFields.MERCHANT_CUSTOMER_ID,
          "the merchant's own identifier for the shopper",
          false,
          "Worldline carries it at order.customer.merchantCustomerId. Nothing on this contract"
              + " looks a token up by it and nothing answers with it, so it is a value a scenario"
              + " rule can be matched on and nothing else.",
          "customer-123");

  private static final AFieldATesterFillsIn TOKEN =
      AFieldATesterFillsIn.typed(
          WorldlineFields.TOKEN,
          "the token of the stored card to charge",
          false,
          "Worldline charges a stored card by cardPaymentMethodSpecificInput.token. A finished"
              + " hosted tokenization session of this simulator hands one back and shows it on"
              + " that session's own screen; a payment-method fixture seeded into this run"
              + " carries one too. Send this or the hosted tokenization id, never both.",
          null);

  private static final AFieldATesterFillsIn HOSTED_TOKENIZATION_ID =
      AFieldATesterFillsIn.typed(
          WorldlineFields.HOSTED_TOKENIZATION_ID,
          "the hosted tokenization session to charge the card of",
          false,
          "Worldline publishes this as the bridge from a finished tokenization: \"Use this field"
              + " after a successful Hosted Tokenization session to create a payment with the"
              + " tokenized payment method details\". Send this or the token, never both.",
          null);

  private static final AFieldATesterFillsIn AUTHORIZATION_MODE =
      AFieldATesterFillsIn.chosenFrom(
          WorldlineFields.AUTHORIZATION_MODE,
          "authorisation mode",
          true,
          "Worldline publishes this as an enumeration of three values with different capture and"
              + " cancellation rights, and a partial capture is available on exactly one of them."
              + " "
              + WorldlineAuthorizationMode.THE_FOURTH_CASE_HAS_NO_NAME,
          theModesItOffers());

  private static List<AValueAFieldOffers> theModesItOffers() {
    List<AValueAFieldOffers> offered = new ArrayList<>();
    for (WorldlineAuthorizationMode mode : WorldlineAuthorizationMode.values()) {
      offered.add(new AValueAFieldOffers(mode.name(), mode.name() + " — " + mode.whatItDoes()));
    }
    return List.copyOf(offered);
  }

  static final ProviderOriginationFields DECLARED =
      new ProviderOriginationFields(
          WorldlineApi.WORLDLINE,
          List.of(
              new DeclaredOrigination(
                  A_TOKENIZATION,
                  "Open a hosted tokenization page so a payer can store a card",
                  "A hosted tokenization session waiting for its payer, with the"
                      + " hostedTokenizationUrl worldline publishes, the deprecated-and-populated"
                      + " partialRedirectUrl beside it, and three hours to live — \"Sessions have"
                      + " a maximum life span of 3 hours\". Worldline publishes that page on its"
                      + " own host and this simulator serves it under its own base URL instead, so"
                      + " the hostedTokenizationUrl and the partialRedirectUrl beside it point"
                      + " here. What tokenizing a card leaves behind is the token a payment"
                      + " charges. No money moves."
                      + IT_IS_LISTED_ALREADY,
                  THE_HOSTED_TOKENIZATION_PAGE,
                  List.of(TOKENS)),
              new DeclaredOrigination(
                  A_PAYMENT,
                  "Create a payment against a stored card",
                  "What this leaves behind is decided by the authorisation mode. Under"
                      + " PRE_AUTHORIZATION or FINAL_AUTHORIZATION it is a payment at"
                      + " PENDING_CAPTURE with statusOutput.statusCode 5, holding the amount and"
                      + " charging nothing yet; capture it or cancel it from the Payments screen,"
                      + " and each of those mints a second payment.id the object then also"
                      + " answers to. Under SALE it is a payment worldline \"Blocks and captures"
                      + " ... in one go\", already captured, with nothing left to capture or"
                      + " cancel."
                      + IT_IS_LISTED_ALREADY,
                  THE_CARD_IS_ALREADY_STORED,
                  List.of(
                      AMOUNT,
                      CURRENCY_CODE,
                      AUTHORIZATION_MODE,
                      TOKEN,
                      HOSTED_TOKENIZATION_ID,
                      MERCHANT_REFERENCE,
                      MERCHANT_CUSTOMER_ID))),
          theFieldsTheSimulatorFillsIn());

  private static SequencedMap<String, String> theFieldsTheSimulatorFillsIn() {
    LinkedHashMap<String, String> filled = new LinkedHashMap<>();
    filled.put(
        WorldlineMerchant.MERCHANT_ID_PROPERTY,
        "worldline puts the merchant in the path of every route of this contract, and that"
            + " merchantId is configured on the account this payment is started on.");
    filled.put(
        WorldlineFields.AUTHORIZATION,
        "worldline authenticates every call with \"Authorization: GCS v1HMAC:<your-api-key>:"
            + "<base64-encoded-signature>\", and a payment started from this screen never reaches"
            + " the wire, so there is nothing for it to be signed with and nothing to verify.");
    return filled;
  }

  private WorldlineOrigination() {}

  static WorldlineApiCall tokenizationCallOf(
      OriginationRequest request, WorldlineMerchant merchant) {
    Map<String, WorldlineJson> body = new LinkedHashMap<>();
    request
        .value(WorldlineFields.TOKENS)
        .ifPresent(tokens -> body.put(WorldlineFields.TOKENS, theTokensIn(tokens)));
    return WorldlineApiCall.of(
        WIRE_METHOD,
        WorldlineApiController.theHostedTokenizationsOf(merchant.merchantId()),
        Map.of(),
        Map.of(),
        WorldlineJsonWriter.toText(WorldlineJson.object(body)));
  }

  private static WorldlineJson theTokensIn(String commaSeparated) {
    List<WorldlineJson> tokens = new ArrayList<>();
    for (String token : commaSeparated.split(",", -1)) {
      if (!token.isBlank()) {
        tokens.add(WorldlineJson.text(token.strip()));
      }
    }
    return WorldlineJson.array(tokens);
  }

  static WorldlineApiCall paymentCallOf(OriginationRequest request, WorldlineMerchant merchant) {
    Map<String, WorldlineJson> amountOfMoney = new LinkedHashMap<>();
    amountOfMoney.put(
        WorldlineFields.AMOUNT,
        WorldlineJson.numberOrText(request.value(WorldlineFields.AMOUNT).orElse("0")));
    amountOfMoney.put(
        WorldlineFields.CURRENCY_CODE,
        WorldlineJson.text(request.value(WorldlineFields.CURRENCY_CODE).orElse("")));
    Map<String, WorldlineJson> order = new LinkedHashMap<>();
    order.put(WorldlineFields.AMOUNT_OF_MONEY, WorldlineJson.object(amountOfMoney));
    request
        .value(WorldlineFields.MERCHANT_REFERENCE)
        .ifPresent(
            reference ->
                order.put(
                    WorldlineFields.REFERENCES,
                    WorldlineJson.object(
                        Map.of(
                            WorldlineFields.MERCHANT_REFERENCE, WorldlineJson.text(reference)))));
    request
        .value(WorldlineFields.MERCHANT_CUSTOMER_ID)
        .ifPresent(
            customer ->
                order.put(
                    WorldlineFields.CUSTOMER,
                    WorldlineJson.object(
                        Map.of(
                            WorldlineFields.MERCHANT_CUSTOMER_ID, WorldlineJson.text(customer)))));
    Map<String, WorldlineJson> cardInput = new LinkedHashMap<>();
    request
        .value(WorldlineFields.TOKEN)
        .ifPresent(token -> cardInput.put(WorldlineFields.TOKEN, WorldlineJson.text(token)));
    cardInput.put(
        WorldlineFields.AUTHORIZATION_MODE,
        WorldlineJson.text(request.value(WorldlineFields.AUTHORIZATION_MODE).orElse("")));
    Map<String, WorldlineJson> body = new LinkedHashMap<>();
    body.put(WorldlineFields.ORDER, WorldlineJson.object(order));
    request
        .value(WorldlineFields.HOSTED_TOKENIZATION_ID)
        .ifPresent(
            hosted -> body.put(WorldlineFields.HOSTED_TOKENIZATION_ID, WorldlineJson.text(hosted)));
    body.put(WorldlineFields.CARD_PAYMENT_METHOD_SPECIFIC_INPUT, WorldlineJson.object(cardInput));
    return WorldlineApiCall.of(
        WIRE_METHOD,
        WorldlineApiController.thePaymentsOf(merchant.merchantId()),
        Map.of(),
        Map.of(),
        WorldlineJsonWriter.toText(WorldlineJson.object(body)));
  }

  static OriginationOutcome outcomeOf(WorldlineOperationResult result, OriginationRequest request) {
    if (result.outcome() == ProviderOutcome.UNKNOWN) {
      return new OriginationOutcome.AnsweredByAScenarioInstead(String.valueOf(result.scenarioId()));
    }
    if (result.outcome() != ProviderOutcome.SUCCESS) {
      return new OriginationOutcome.RefusedByTheProvider(theWordsOf(result), result.scenarioId());
    }
    return new OriginationOutcome.Originated(
        String.valueOf(result.providerObjectId()),
        DECLARED.wayNamed(request.startedAs()).orElseThrow().whatItLeavesBehind(),
        result.scenarioId());
  }

  static String theWordsOf(WorldlineOperationResult result) {
    Map<String, List<String>> answered = result.body().fields();
    return "provider worldline answered HTTP "
        + result.body().httpStatus()
        + " with "
        + WorldlineFields.ERRORS
        + "[]."
        + WorldlineFields.ERROR_CODE
        + "="
        + firstOf(answered, WorldlineFields.ERRORS + "." + WorldlineFields.ERROR_CODE)
        + ", "
        + WorldlineFields.ERRORS
        + "[]."
        + WorldlineFields.ID
        + "="
        + firstOf(answered, WorldlineFields.ERRORS + "." + WorldlineFields.ID)
        + " and "
        + WorldlineFields.ERRORS
        + "[]."
        + WorldlineFields.MESSAGE
        + "="
        + firstOf(answered, WorldlineFields.ERRORS + "." + WorldlineFields.MESSAGE);
  }

  private static String firstOf(Map<String, List<String>> answered, String field) {
    return answered.getOrDefault(field, List.of()).stream().findFirst().orElse("");
  }
}
