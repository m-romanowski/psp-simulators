package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.money.LedgerClaim;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class CapturePayment {

  static final String A_BODY_THIS_ENDPOINT_CANNOT_READ =
      "the request body is not a JSON object this endpoint can read";

  static final String NO_PAYMENT_ANSWERS_TO_THAT_ID =
      "paymentId names no payment of this merchant. Provider worldline mints a new payment.id for"
          + " every maintenance operation, and this simulator resolves any identifier a payment"
          + " has ever answered to back to that payment, so an identifier that reaches nothing"
          + " here reached nothing under any of its spellings";

  static final String MORE_THAN_WAS_AUTHORIZED =
      "amount is higher than what this authorization still has available. Provider worldline"
          + " publishes the rule as \"The amount can be lower than the amount that was authorized,"
          + " but not higher\" and publishes that repeated partial captures must not exceed the"
          + " original authorized amount";

  static final String THE_AMOUNT_IS_NOT_WITHIN_THE_PUBLISHED_BOUNDS =
      "amount is a whole number of minor units at the root of a capture request, which is the one"
          + " maintenance operation of this contract that does not take an amountOfMoney object. "
          + WorldlineAmounts.MINOR_UNITS;

  static final String ONLY_A_PRE_AUTHORIZATION_IS_CAPTURED_IN_PART =
      "a partial capture is available on exactly one of the published authorization modes."
          + " Provider worldline publishes PRE_AUTHORIZATION as \"Supports both full and partial"
          + " captures\" and FINAL_AUTHORIZATION as \"Supports only full captures\", and says of"
          + " the unnamed fourth case that it \"behaves similar to a final authorization\". This"
          + " payment was authorized under a mode that takes full captures only";

  static final String AN_OMITTED_AMOUNT_TAKES_THE_WHOLE_ORIGINAL_AND_CLOSES_IT =
      "provider worldline publishes what an omitted amount means and publishes it twice, and the"
          + " schema's wording is the wider of the two: \"If left empty, the full amount will be"
          + " captured and the request will be final. If the full amount is captured, the request"
          + " will also be final.\" The maintenance page states the first half alone, \"If left"
          + " empty, our platform will capture the full original amount\". This simulator takes"
          + " the schema's: an omitted amount captures the full original amount and closes the"
          + " transaction whatever isFinal said";

  private final WorldlinePayments payments;
  private final WorldlineAnswers answers;
  private final WorldlineIdempotenceKeys keys;
  private final ScenarioEngine scenarios;

  CapturePayment(
      WorldlinePayments payments,
      WorldlineAnswers answers,
      WorldlineIdempotenceKeys keys,
      ScenarioEngine scenarios) {
    this.payments = payments;
    this.answers = answers;
    this.keys = keys;
    this.scenarios = scenarios;
  }

  WorldlineOperationResult handle(
      WorldlineApiCall call,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      WorldlineMerchant merchant,
      String addressedBy,
      Instant now) {
    return WorldlineIdempotentCall.under(
        keys,
        answers,
        call,
        headers,
        testRunId,
        merchant,
        WorldlineOperations.CAPTURE_PAYMENT,
        now,
        () -> theAnswerItWouldGive(call, headers, testRunId, merchant, addressedBy, now));
  }

  private WorldlineOperationResult theAnswerItWouldGive(
      WorldlineApiCall call,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      WorldlineMerchant merchant,
      String addressedBy,
      Instant now) {
    if (call.carriesABody() && call.body().isEmpty()) {
      return answers.refusedValue(null, A_BODY_THIS_ENDPOINT_CANNOT_READ);
    }
    WorldlineJson.JsonObject body = call.body().orElseGet(WorldlineJson::empty);
    Optional<WorldlinePayment> found =
        payments
            .findById(testRunId, addressedBy)
            .filter(payment -> payment.accountId().equals(merchant.accountId()));
    if (found.isEmpty()) {
      return answers.noSuchPayment(NO_PAYMENT_ANSWERS_TO_THAT_ID);
    }
    WorldlinePayment payment = found.get();
    if (!payment.isAnAuthorizationWaitingToBeCaptured()) {
      return answers.actionNotAllowedOn(
          payment.paymentId(), payment.whyNoMaintenanceOperationIsAllowed());
    }
    Optional<String> asked = body.scalar(WorldlineFields.AMOUNT);
    if (asked.isPresent()
        && WorldlineAmounts.wholeMinorUnits(asked.get())
            .filter(WorldlineAmounts::isWithinThePublishedBounds)
            .isEmpty()) {
      return answers.refusedValue(
          WorldlineFields.AMOUNT, THE_AMOUNT_IS_NOT_WITHIN_THE_PUBLISHED_BOUNDS);
    }
    long authorized = WorldlineAmounts.minorUnitsOf(payment.amount());
    long amount =
        asked
            .flatMap(WorldlineAmounts::wholeMinorUnits)
            .orElse(Long.valueOf(authorized))
            .longValue();
    if (BigDecimal.valueOf(amount).compareTo(payment.capturableRemainder()) > 0) {
      return answers.refusedValue(WorldlineFields.AMOUNT, MORE_THAN_WAS_AUTHORIZED);
    }
    boolean takesTheWholeOriginal = asked.isEmpty() || amount == authorized;
    if (!takesTheWholeOriginal && !payment.authorizationMode().allowsAPartialCapture()) {
      return answers.actionNotAllowedOn(
          payment.paymentId(), ONLY_A_PRE_AUTHORIZATION_IS_CAPTURED_IN_PART);
    }
    boolean closes =
        takesTheWholeOriginal
            || isFinal(body)
            || BigDecimal.valueOf(amount).compareTo(payment.capturableRemainder()) == 0;
    return decided(headers, testRunId, merchant, payment, addressedBy, amount, closes, now);
  }

  static boolean isFinal(WorldlineJson.JsonObject body) {
    return body.flag(WorldlineFields.IS_FINAL).orElse(Boolean.FALSE).booleanValue();
  }

  private WorldlineOperationResult decided(
      Map<String, List<String>> headers,
      TestRunId testRunId,
      WorldlineMerchant merchant,
      WorldlinePayment payment,
      String addressedBy,
      long amount,
      boolean closes,
      Instant now) {
    return WorldlineOperationResult.decidedBy(
        scenarios.decide(requestOf(headers, testRunId, merchant, addressedBy, amount)),
        () -> captured(payment, amount, closes, merchant, now),
        code -> refused(payment, code, now));
  }

  private static ScenarioRequest requestOf(
      Map<String, List<String>> headers,
      TestRunId testRunId,
      WorldlineMerchant merchant,
      String addressedBy,
      long amount) {
    return new ScenarioRequest(
        WorldlineApi.WORLDLINE,
        testRunId,
        WorldlineStoryKey.ofPayment(addressedBy),
        merchant.accountId(),
        null,
        addressedBy,
        null,
        null,
        WorldlineAmounts.asAnAmount(amount),
        null,
        WorldlineOperations.CAPTURE_PAYMENT,
        headers);
  }

  private WorldlineOperationResult captured(
      WorldlinePayment payment,
      long amount,
      boolean closes,
      WorldlineMerchant merchant,
      Instant now) {
    WorldlineCaptureLanding landing = merchant.captureLanding();
    String minted = payment.theNextIdentifierAMaintenanceOperationMints();
    MonetaryOperation operation =
        new MonetaryOperation(
            payment.ledger().nextSequence(),
            OperationKind.CAPTURE,
            WorldlineAmounts.asAnAmount(amount),
            Boolean.valueOf(closes),
            null,
            landing.nativeStatus(),
            WorldlineStatus.normalizedPaymentStatus(landing.nativeStatus()),
            now);
    LedgerClaim applied =
        payments.applyMaintenance(
            payment,
            new WorldlinePayments.WorldlineMaintenance(
                minted,
                payment.maintenanceIds().size() + 1,
                operation,
                closes ? landing.nativeStatus() : WorldlineStatus.PENDING_CAPTURE,
                closes ? landing.statusCategory() : WorldlineStatus.CATEGORY_PENDING_MERCHANT,
                closes ? landing.statusCode() : WorldlineStatus.CODE_PENDING_CAPTURE,
                closes,
                now));
    if (applied != LedgerClaim.APPLIED) {
      return answers.actionNotAllowedOn(
          payment.paymentId(), payment.whyNoMaintenanceOperationIsAllowed());
    }
    return WorldlineOperationResult.succeeded(
        minted,
        WorldlineBody.of(
            WorldlineAnswers.CREATED,
            theCaptureResponseOf(
                minted,
                amount,
                payment.currencyCode(),
                landing.nativeStatus(),
                landing.statusCode())));
  }

  private WorldlineOperationResult refused(WorldlinePayment payment, String code, Instant now) {
    if (!WorldlineErrors.isAMaintenanceRefusalCode(code)) {
      return answers.refusedMaintenanceWithAnErrorCode(
          payment.paymentId(), code, WorldlineErrors.THE_DECLINE_CODES);
    }
    String minted = payment.theNextIdentifierAMaintenanceOperationMints();
    LedgerClaim applied =
        payments.applyMaintenance(
            payment,
            new WorldlinePayments.WorldlineMaintenance(
                minted,
                payment.maintenanceIds().size() + 1,
                null,
                WorldlineStatus.PENDING_CAPTURE,
                WorldlineStatus.CATEGORY_PENDING_MERCHANT,
                WorldlineStatus.CODE_PENDING_CAPTURE,
                false,
                now));
    if (applied != LedgerClaim.APPLIED) {
      return answers.actionNotAllowedOn(
          payment.paymentId(), payment.whyNoMaintenanceOperationIsAllowed());
    }
    return WorldlineOperationResult.succeeded(
        minted,
        WorldlineBody.of(
            WorldlineAnswers.CREATED,
            theCaptureResponseOf(
                minted,
                WorldlineAmounts.minorUnitsOf(payment.amount()),
                payment.currencyCode(),
                WorldlineStatus.REJECTED_CAPTURE,
                WorldlineStatus.CODE_REJECTED_CAPTURE)));
  }

  static WorldlineJson.JsonObject theCaptureResponseOf(
      String paymentId, long amount, String currencyCode, String status, int statusCode) {
    Map<String, WorldlineJson> captureOutput = new LinkedHashMap<>();
    captureOutput.put(
        WorldlineFields.AMOUNT_OF_MONEY, CreatePayment.theAmountOfMoney(amount, currencyCode));
    Map<String, WorldlineJson> statusOutput = new LinkedHashMap<>();
    statusOutput.put(
        WorldlineFields.STATUS_CODE, WorldlineJson.number(Integer.toString(statusCode)));
    Map<String, WorldlineJson> response = new LinkedHashMap<>();
    response.put(WorldlineFields.CAPTURE_OUTPUT, WorldlineJson.object(captureOutput));
    response.put(WorldlineFields.STATUS, WorldlineJson.text(status));
    response.put(WorldlineFields.STATUS_OUTPUT, WorldlineJson.object(statusOutput));
    response.put(WorldlineFields.ID, WorldlineJson.text(paymentId));
    return WorldlineJson.object(response);
  }
}
