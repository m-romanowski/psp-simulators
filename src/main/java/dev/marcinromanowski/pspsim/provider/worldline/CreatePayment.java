package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDecision;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class CreatePayment {

  static final int PAYMENT_ID_ATTEMPTS = 5;

  static final int LONGEST_MERCHANT_REFERENCE = 40;

  static final String A_BODY_THIS_ENDPOINT_CANNOT_READ =
      "the request body is not a JSON object this endpoint can read";

  static final String THE_AMOUNT_IS_REQUIRED =
      "order.amountOfMoney.amount is required and is a whole number of minor units. "
          + WorldlineAmounts.MINOR_UNITS;

  static final String THE_CURRENCY_IS_REQUIRED =
      "order.amountOfMoney.currencyCode is required and provider worldline publishes it as"
          + " exactly three characters";

  static final String THE_REFERENCE_IS_TOO_LONG =
      "order.references.merchantReference is published with maxLength 40 and this one is longer";

  static final String ONE_INSTRUMENT_AND_ONLY_ONE =
      "send cardPaymentMethodSpecificInput.token or hostedTokenizationId, and one of the two is"
          + " required. Provider worldline publishes the second as the bridge from a finished"
          + " tokenization: \"Use this field after a successful Hosted Tokenization session to"
          + " create a payment with the tokenized payment method details\", and the prose says"
          + " the same — \"you can also send the hostedTokenizationId instead of the tokenId\"."
          + " It publishes no rule for a request carrying both, so this simulator refuses one"
          + " rather than choosing between two instruments a caller named at once";

  static final String NO_SESSION_ANSWERS_TO_THAT_HOSTED_TOKENIZATION_ID =
      "hostedTokenizationId names no hosted tokenization session of this merchant. "
          + WorldlineAnswers.NO_ERRORS_ID_IS_PUBLISHED_FOR_AN_UNKNOWN_HOSTED_TOKENIZATION;

  static final String THE_TOKENIZATION_IS_NOT_FINISHED =
      "hostedTokenizationId names a hosted tokenization session no payer has finished, so there is"
          + " no token on it for this payment to charge";

  static final String NO_TOKEN_ANSWERS_TO_THAT_VALUE =
      "cardPaymentMethodSpecificInput.token names no token this test run holds: worldline mints"
          + " one when a payer finishes a hosted tokenization session, and this simulator also"
          + " accepts one seeded as a payment-method fixture of the run";

  static final String THE_AUTHORIZATION_MODE_IS_NOT_PUBLISHED =
      "cardPaymentMethodSpecificInput.authorizationMode is published as an enumeration of "
          + "exactly three values and this request names another. The three are ";

  static final String AN_OMITTED_AUTHORIZATION_MODE =
      "provider worldline publishes no default for cardPaymentMethodSpecificInput.authorizationMode"
          + " and does publish what happens when the value cannot be specified at all: \"In case"
          + " the acquirer does not allow this to be specified the authorizationMode is"
          + " 'unspecified', which behaves similar to a final authorization\". A request that"
          + " omits the field is therefore treated here as FINAL_AUTHORIZATION, which is an"
          + " inference from that sentence and not a published default";

  private final WorldlinePayments payments;
  private final WorldlineTokenizations sessions;
  private final WorldlinePayerCards cards;
  private final WorldlineIdempotenceKeys keys;
  private final WorldlineAnswers answers;
  private final IdGenerator ids;
  private final ScenarioEngine scenarios;

  CreatePayment(
      WorldlinePayments payments,
      WorldlineTokenizations sessions,
      WorldlinePayerCards cards,
      WorldlineIdempotenceKeys keys,
      WorldlineAnswers answers,
      IdGenerator ids,
      ScenarioEngine scenarios) {
    this.payments = payments;
    this.sessions = sessions;
    this.cards = cards;
    this.keys = keys;
    this.answers = answers;
    this.ids = ids;
    this.scenarios = scenarios;
  }

  WorldlineOperationResult handle(
      WorldlineApiCall call,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      WorldlineMerchant merchant,
      Instant now,
      ObjectOrigin askedBy) {
    return WorldlineIdempotentCall.under(
        keys,
        answers,
        call,
        headers,
        testRunId,
        merchant,
        WorldlineOperations.CREATE_PAYMENT,
        now,
        () -> theAnswerItWouldGive(call, headers, testRunId, merchant, now, askedBy));
  }

  private WorldlineOperationResult theAnswerItWouldGive(
      WorldlineApiCall call,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      WorldlineMerchant merchant,
      Instant now,
      ObjectOrigin askedBy) {
    Optional<WorldlineJson.JsonObject> parsed = call.body();
    if (parsed.isEmpty()) {
      return answers.refusedValue(null, A_BODY_THIS_ENDPOINT_CANNOT_READ);
    }
    WorldlineJson.JsonObject body = parsed.get();
    WorldlineOperationResult refused = theRefusalOf(body);
    return refused == null ? charged(body, headers, testRunId, merchant, now, askedBy) : refused;
  }

  private @Nullable WorldlineOperationResult theRefusalOf(WorldlineJson.JsonObject body) {
    Optional<String> amount = theAmountIn(body);
    if (amount.isEmpty()
        || WorldlineAmounts.wholeMinorUnits(amount.get())
            .filter(WorldlineAmounts::isWithinThePublishedBounds)
            .isEmpty()) {
      return answers.refusedValue(
          WorldlineFields.ORDER + "." + WorldlineFields.AMOUNT_OF_MONEY, THE_AMOUNT_IS_REQUIRED);
    }
    Optional<String> currency = theCurrencyIn(body);
    if (currency.filter(WorldlineAmounts::isAThreeLetterCode).isEmpty()) {
      return answers.refusedValue(
          WorldlineFields.ORDER + "." + WorldlineFields.AMOUNT_OF_MONEY, THE_CURRENCY_IS_REQUIRED);
    }
    String reference = theMerchantReferenceIn(body);
    if (reference != null && reference.length() > LONGEST_MERCHANT_REFERENCE) {
      return answers.refusedValue(
          WorldlineFields.ORDER
              + "."
              + WorldlineFields.REFERENCES
              + "."
              + WorldlineFields.MERCHANT_REFERENCE,
          THE_REFERENCE_IS_TOO_LONG);
    }
    boolean token = theTokenIn(body) != null;
    boolean hosted = theHostedTokenizationIdIn(body) != null;
    if (token == hosted) {
      return answers.refusedValue(
          WorldlineFields.CARD_PAYMENT_METHOD_SPECIFIC_INPUT + "." + WorldlineFields.TOKEN,
          ONE_INSTRUMENT_AND_ONLY_ONE);
    }
    String mode = theAuthorizationModeIn(body);
    if (mode != null && WorldlineAuthorizationMode.of(mode).isEmpty()) {
      return answers.refusedValue(
          WorldlineFields.CARD_PAYMENT_METHOD_SPECIFIC_INPUT
              + "."
              + WorldlineFields.AUTHORIZATION_MODE,
          THE_AUTHORIZATION_MODE_IS_NOT_PUBLISHED
              + WorldlineAuthorizationMode.thePublishedValues());
    }
    return null;
  }

  private WorldlineOperationResult charged(
      WorldlineJson.JsonObject body,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      WorldlineMerchant merchant,
      Instant now,
      ObjectOrigin askedBy) {
    WorldlineInstrument instrument = theInstrumentOf(body, testRunId, merchant);
    WorldlineOperationResult unresolved = instrument.refusal();
    if (unresolved != null) {
      return unresolved;
    }
    String paymentId = aPaymentIdClaimedFor(testRunId, now);
    ScenarioDecision decision =
        scenarios.decide(requestOf(body, headers, testRunId, merchant, instrument, paymentId));
    if (decision instanceof ScenarioDecision.Preempt preempted) {
      return WorldlineOperationResult.answeredInsteadOf(preempted.scenarioId(), preempted.answer());
    }
    return WorldlineOperationResult.decidedBy(
        decision,
        () -> accepted(body, testRunId, merchant, instrument, paymentId, now, askedBy),
        code -> declined(body, testRunId, merchant, instrument, paymentId, now, askedBy, code));
  }

  private static ScenarioRequest requestOf(
      WorldlineJson.JsonObject body,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      WorldlineMerchant merchant,
      WorldlineInstrument instrument,
      String paymentId) {
    return new ScenarioRequest(
        WorldlineApi.WORLDLINE,
        testRunId,
        WorldlineStoryKey.ofPayment(paymentId),
        merchant.accountId(),
        theMerchantReferenceIn(body),
        null,
        theMerchantCustomerIdIn(body),
        instrument.token(),
        WorldlineAmounts.asAnAmount(theMinorUnitsIn(body)),
        theCurrencyIn(body).orElse(null),
        WorldlineOperations.CREATE_PAYMENT,
        headers);
  }

  private WorldlineOperationResult accepted(
      WorldlineJson.JsonObject body,
      TestRunId testRunId,
      WorldlineMerchant merchant,
      WorldlineInstrument instrument,
      String paymentId,
      Instant now,
      ObjectOrigin askedBy) {
    WorldlineAuthorizationMode mode = theAuthorizationModeOrTheUnspecifiedBehaviour(body);
    long minorUnits = theMinorUnitsIn(body);
    WorldlinePayment payment =
        stored(
            body,
            testRunId,
            merchant,
            instrument,
            paymentId,
            mode,
            minorUnits,
            mode.capturesAtOnce() ? WorldlineStatus.CAPTURED : WorldlineStatus.PENDING_CAPTURE,
            mode.capturesAtOnce()
                ? WorldlineStatus.CATEGORY_COMPLETED
                : WorldlineStatus.CATEGORY_PENDING_MERCHANT,
            mode.capturesAtOnce()
                ? WorldlineStatus.CODE_CAPTURED
                : WorldlineStatus.CODE_PENDING_CAPTURE,
            mode.capturesAtOnce(),
            theLedgerOf(mode, minorUnits, now),
            now,
            askedBy);
    return WorldlineOperationResult.succeeded(
            paymentId,
            WorldlineBody.of(
                WorldlineAnswers.CREATED,
                theCreateResponseOf(payment),
                Map.of(
                    WorldlineFields.LOCATION,
                    List.of(
                        WorldlineApiController.thePaymentResourceOf(
                            merchant.merchantId(), paymentId)))))
        .alsoCarrying(WorldlineFields.CONTENT_TYPE, WorldlineFields.JSON);
  }

  private WorldlineOperationResult declined(
      WorldlineJson.JsonObject body,
      TestRunId testRunId,
      WorldlineMerchant merchant,
      WorldlineInstrument instrument,
      String paymentId,
      Instant now,
      ObjectOrigin askedBy,
      String code) {
    WorldlineAuthorizationMode mode = theAuthorizationModeOrTheUnspecifiedBehaviour(body);
    WorldlinePayment payment =
        stored(
            body,
            testRunId,
            merchant,
            instrument,
            paymentId,
            mode,
            theMinorUnitsIn(body),
            WorldlineStatus.REJECTED,
            WorldlineStatus.CATEGORY_UNSUCCESSFUL,
            WorldlineStatus.CODE_REJECTED,
            true,
            Ledger.empty(),
            now,
            askedBy);
    return answers.declinedPayment(
        paymentId,
        code,
        WorldlineRefusals.A_DECLINED_PAYMENT_IS_NAMED_BY_THE_ENVELOPE_ITSELF,
        theCreateResponseOf(payment));
  }

  private static Ledger theLedgerOf(WorldlineAuthorizationMode mode, long minorUnits, Instant now) {
    Ledger ledger =
        Ledger.empty()
            .with(
                new MonetaryOperation(
                    1,
                    OperationKind.AUTHORIZATION,
                    WorldlineAmounts.asAnAmount(minorUnits),
                    Boolean.valueOf(mode.capturesAtOnce()),
                    null,
                    WorldlineStatus.PENDING_CAPTURE,
                    NormalizedStatus.READY_FOR_CAPTURE,
                    now));
    if (!mode.capturesAtOnce()) {
      return ledger;
    }
    return ledger.with(
        new MonetaryOperation(
            2,
            OperationKind.CAPTURE,
            WorldlineAmounts.asAnAmount(minorUnits),
            Boolean.TRUE,
            null,
            WorldlineStatus.CAPTURED,
            NormalizedStatus.SUCCEEDED,
            now));
  }

  private WorldlinePayment stored(
      WorldlineJson.JsonObject body,
      TestRunId testRunId,
      WorldlineMerchant merchant,
      WorldlineInstrument instrument,
      String paymentId,
      WorldlineAuthorizationMode mode,
      long minorUnits,
      String nativeStatus,
      String statusCategory,
      int statusCode,
      boolean authorizationClosed,
      Ledger ledger,
      Instant now,
      ObjectOrigin askedBy) {
    WorldlinePayment payment =
        new WorldlinePayment(
            testRunId,
            paymentId,
            List.of(),
            merchant.accountId(),
            merchant.merchantId(),
            theMerchantReferenceIn(body),
            theMerchantCustomerIdIn(body),
            instrument.token(),
            instrument.hostedTokenizationId(),
            instrument.cardNumber(),
            instrument.expiryDate(),
            WorldlineAmounts.asAnAmount(minorUnits),
            theCurrencyIn(body).orElseThrow(),
            mode,
            nativeStatus,
            statusCategory,
            statusCode,
            authorizationClosed,
            ledger,
            askedBy,
            now,
            now);
    if (!payments.insertIfAbsent(payment)) {
      throw new IllegalStateException(
          "the Worldline payment.id this decision was taken under was claimed by another request"
              + " before the payment could be stored");
    }
    return payment;
  }

  private String aPaymentIdClaimedFor(TestRunId testRunId, Instant now) {
    for (int attempt = 0; attempt < PAYMENT_ID_ATTEMPTS; attempt++) {
      String paymentId =
          WorldlineIdentifiers.paymentIdOf(
              WorldlineIdentifiers.aPayId(ids), WorldlineIdentifiers.THE_FIRST_PAYIDSUB);
      if (payments.claimIdentifier(
          testRunId, paymentId, paymentId, WorldlineIdentifiers.THE_FIRST_PAYIDSUB, now)) {
        return paymentId;
      }
    }
    throw new IllegalStateException(
        "no Worldline payment.id could be claimed in " + PAYMENT_ID_ATTEMPTS + " attempts");
  }

  private WorldlineInstrument theInstrumentOf(
      WorldlineJson.JsonObject body, TestRunId testRunId, WorldlineMerchant merchant) {
    String hostedTokenizationId = theHostedTokenizationIdIn(body);
    if (hostedTokenizationId != null) {
      Optional<WorldlineTokenization> session =
          sessions
              .findById(testRunId, hostedTokenizationId)
              .filter(held -> held.accountId().equals(merchant.accountId()));
      if (session.isEmpty()) {
        return WorldlineInstrument.refused(
            answers.refusedValue(
                WorldlineFields.HOSTED_TOKENIZATION_ID,
                NO_SESSION_ANSWERS_TO_THAT_HOSTED_TOKENIZATION_ID));
      }
      WorldlineTokenization held = session.get();
      if (!held.holdsAToken()) {
        return WorldlineInstrument.refused(
            answers.refusedValue(
                WorldlineFields.HOSTED_TOKENIZATION_ID, THE_TOKENIZATION_IS_NOT_FINISHED));
      }
      return new WorldlineInstrument(
          String.valueOf(held.token()),
          hostedTokenizationId,
          held.cardNumber(),
          held.expiryDate(),
          null);
    }
    String token = String.valueOf(theTokenIn(body));
    Optional<WorldlineTokenization> minted = sessions.holdingTheToken(testRunId, token);
    if (minted.isPresent()) {
      WorldlineTokenization held = minted.get();
      return new WorldlineInstrument(
          token, held.hostedTokenizationId(), held.cardNumber(), held.expiryDate(), null);
    }
    Optional<WorldlinePayerCard> seeded = cards.holdingTheToken(testRunId, token);
    if (seeded.isPresent()) {
      WorldlinePayerCard card = seeded.get();
      return new WorldlineInstrument(
          token,
          null,
          card.theCardNumberAsWorldlineWritesIt(),
          card.theExpiryDateAsWorldlineWritesIt(),
          null);
    }
    return WorldlineInstrument.refused(
        answers.refusedValue(
            WorldlineFields.CARD_PAYMENT_METHOD_SPECIFIC_INPUT + "." + WorldlineFields.TOKEN,
            NO_TOKEN_ANSWERS_TO_THAT_VALUE));
  }

  record WorldlineInstrument(
      String token,
      @Nullable String hostedTokenizationId,
      @Nullable String cardNumber,
      @Nullable String expiryDate,
      @Nullable WorldlineOperationResult refusal) {

    static WorldlineInstrument refused(WorldlineOperationResult refusal) {
      return new WorldlineInstrument("", null, null, null, refusal);
    }
  }

  static WorldlineJson.JsonObject theCreateResponseOf(WorldlinePayment payment) {
    Map<String, WorldlineJson> response = new LinkedHashMap<>();
    response.put(WorldlineFields.CREATION_OUTPUT, WorldlineJson.empty());
    response.put(WorldlineFields.PAYMENT, thePaymentOf(payment));
    return WorldlineJson.object(response);
  }

  static WorldlineJson.JsonObject thePaymentOf(WorldlinePayment payment) {
    Map<String, WorldlineJson> statusOutput = new LinkedHashMap<>();
    statusOutput.put(WorldlineFields.STATUS_CATEGORY, WorldlineJson.text(payment.statusCategory()));
    statusOutput.put(
        WorldlineFields.STATUS_CODE, WorldlineJson.number(Integer.toString(payment.statusCode())));
    statusOutput.put(
        WorldlineFields.IS_CANCELLABLE, WorldlineJson.flag(payment.authorizationOpen()));
    statusOutput.put(
        WorldlineFields.IS_AUTHORIZED,
        WorldlineJson.flag(!WorldlineStatus.REJECTED.equals(payment.nativeStatus())));
    statusOutput.put(
        WorldlineFields.IS_REFUNDABLE,
        WorldlineJson.flag(WorldlineStatus.CAPTURED.equals(payment.nativeStatus())));
    Map<String, WorldlineJson> object = new LinkedHashMap<>();
    object.put(WorldlineFields.ID, WorldlineJson.text(payment.paymentId()));
    object.put(WorldlineFields.STATUS, WorldlineJson.text(payment.nativeStatus()));
    object.put(WorldlineFields.STATUS_OUTPUT, WorldlineJson.object(statusOutput));
    object.put(WorldlineFields.PAYMENT_OUTPUT, thePaymentOutputOf(payment));
    return WorldlineJson.object(object);
  }

  private static WorldlineJson thePaymentOutputOf(WorldlinePayment payment) {
    Map<String, WorldlineJson> output = new LinkedHashMap<>();
    output.put(WorldlineFields.AMOUNT_OF_MONEY, theAmountOfMoneyOf(payment));
    output.put(
        WorldlineFields.PAYMENT_METHOD,
        WorldlineJson.text(WorldlineFields.CARD_AS_A_PAYMENT_METHOD));
    Map<String, WorldlineJson> card = new LinkedHashMap<>();
    card.put(WorldlineFields.CARD_NUMBER, WorldlineJson.text(String.valueOf(payment.cardNumber())));
    card.put(WorldlineFields.EXPIRY_DATE, WorldlineJson.text(String.valueOf(payment.expiryDate())));
    Map<String, WorldlineJson> cardOutput = new LinkedHashMap<>();
    cardOutput.put(WorldlineFields.CARD, WorldlineJson.object(card));
    cardOutput.put(WorldlineFields.TOKEN, WorldlineJson.text(String.valueOf(payment.token())));
    output.put(
        WorldlineFields.CARD_PAYMENT_METHOD_SPECIFIC_OUTPUT, WorldlineJson.object(cardOutput));
    return WorldlineJson.object(output);
  }

  static WorldlineJson theAmountOfMoneyOf(WorldlinePayment payment) {
    return theAmountOfMoney(
        WorldlineAmounts.minorUnitsOf(payment.amount()), payment.currencyCode());
  }

  static WorldlineJson theAmountOfMoney(long minorUnits, String currencyCode) {
    Map<String, WorldlineJson> amountOfMoney = new LinkedHashMap<>();
    amountOfMoney.put(WorldlineFields.AMOUNT, WorldlineJson.number(Long.toString(minorUnits)));
    amountOfMoney.put(WorldlineFields.CURRENCY_CODE, WorldlineJson.text(currencyCode));
    return WorldlineJson.object(amountOfMoney);
  }

  static Optional<String> theAmountIn(WorldlineJson.JsonObject body) {
    return theAmountOfMoneyIn(body).flatMap(money -> money.scalar(WorldlineFields.AMOUNT));
  }

  static long theMinorUnitsIn(WorldlineJson.JsonObject body) {
    return theAmountIn(body).flatMap(WorldlineAmounts::wholeMinorUnits).orElse(Long.valueOf(0L));
  }

  static Optional<String> theCurrencyIn(WorldlineJson.JsonObject body) {
    return theAmountOfMoneyIn(body)
        .flatMap(money -> money.text(WorldlineFields.CURRENCY_CODE))
        .filter(sent -> !sent.isBlank());
  }

  private static Optional<WorldlineJson.JsonObject> theAmountOfMoneyIn(
      WorldlineJson.JsonObject body) {
    return body.object(WorldlineFields.ORDER)
        .flatMap(order -> order.object(WorldlineFields.AMOUNT_OF_MONEY));
  }

  static @Nullable String theMerchantReferenceIn(WorldlineJson.JsonObject body) {
    return body.object(WorldlineFields.ORDER)
        .flatMap(order -> order.object(WorldlineFields.REFERENCES))
        .flatMap(references -> references.text(WorldlineFields.MERCHANT_REFERENCE))
        .filter(sent -> !sent.isBlank())
        .orElse(null);
  }

  static @Nullable String theMerchantCustomerIdIn(WorldlineJson.JsonObject body) {
    return body.object(WorldlineFields.ORDER)
        .flatMap(order -> order.object(WorldlineFields.CUSTOMER))
        .flatMap(customer -> customer.text(WorldlineFields.MERCHANT_CUSTOMER_ID))
        .filter(sent -> !sent.isBlank())
        .orElse(null);
  }

  static @Nullable String theTokenIn(WorldlineJson.JsonObject body) {
    return body.object(WorldlineFields.CARD_PAYMENT_METHOD_SPECIFIC_INPUT)
        .flatMap(input -> input.text(WorldlineFields.TOKEN))
        .filter(sent -> !sent.isBlank())
        .orElse(null);
  }

  static @Nullable String theHostedTokenizationIdIn(WorldlineJson.JsonObject body) {
    return body.text(WorldlineFields.HOSTED_TOKENIZATION_ID)
        .filter(sent -> !sent.isBlank())
        .orElse(null);
  }

  static @Nullable String theAuthorizationModeIn(WorldlineJson.JsonObject body) {
    return body.object(WorldlineFields.CARD_PAYMENT_METHOD_SPECIFIC_INPUT)
        .flatMap(input -> input.text(WorldlineFields.AUTHORIZATION_MODE))
        .filter(sent -> !sent.isBlank())
        .orElse(null);
  }

  static WorldlineAuthorizationMode theAuthorizationModeOrTheUnspecifiedBehaviour(
      WorldlineJson.JsonObject body) {
    String named = theAuthorizationModeIn(body);
    return named == null
        ? WorldlineAuthorizationMode.FINAL_AUTHORIZATION
        : WorldlineAuthorizationMode.of(named).orElseThrow();
  }
}
