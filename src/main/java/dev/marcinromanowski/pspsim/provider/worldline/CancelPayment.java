package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.money.LedgerClaim;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class CancelPayment {

  static final String A_BODY_THIS_ENDPOINT_CANNOT_READ =
      "the request body is not a JSON object this endpoint can read";

  static final String THE_PAYMENT_IS_CLOSED =
      "Cancellation is not allowed because payment is closed. Provider worldline publishes that"
          + " sentence as one of the three meanings of 409 on this contract, and it is the one"
          + " this route serves";

  static final String BOTH_AMOUNT_PLACES_AT_ONCE =
      "provider worldline publishes the amount of a cancellation in two places and this request"
          + " carries both with different values. The published schema puts amountOfMoney at the"
          + " body root and every SDK sample builds the root form; the maintenance page's own"
          + " property table labels the same property order.amountOfMoney. This simulator reads"
          + " either one, so that a client built from either publication is answered, and refuses"
          + " a request that sends both and disagrees with itself, because worldline publishes no"
          + " precedence between them";

  static final String ONLY_A_PRE_AUTHORIZATION_IS_CANCELLED_IN_PART =
      "a partial cancellation is available on exactly one of the published authorization modes."
          + " Provider worldline publishes PRE_AUTHORIZATION as \"You can cancel the"
          + " authorisation any time, both fully and partially\" and FINAL_AUTHORIZATION as \"You"
          + " can cancel the authorisation any time, but only fully\", and says of the unnamed"
          + " fourth case that it \"behaves similar to a final authorization\"";

  static final String THIS_SLICE_CANCELS_IN_FULL_ONLY =
      "provider worldline publishes a partial cancellation of a PRE_AUTHORIZATION and this slice"
          + " serves a full one only. The reason is this simulator's and not the vendor's: the"
          + " monetary ledger every screen and every balance of this simulator is derived from"
          + " has an AUTHORIZATION, a CAPTURE and a REFUND and no operation that lowers an"
          + " authorization without capturing anything, so a partial cancellation could be"
          + " answered on the wire and would then not be true of the object the control read and"
          + " the Payments screen show. A cancellation naming the whole remaining authorized"
          + " amount, and a cancellation naming no amount at all, are both served";

  static final String THE_AMOUNT_IS_NOT_WITHIN_THE_PUBLISHED_BOUNDS =
      "amountOfMoney.amount is a whole number of minor units. " + WorldlineAmounts.MINOR_UNITS;

  static final String AN_OMITTED_AMOUNT_CANCELS_THE_WHOLE_ORIGINAL =
      "provider worldline publishes what an omitted amount means on this route: \"If left empty,"
          + " our platform will cancel the full original amount\"";

  static final String A_CANCELLATION_LANDS_AT_THE_SETTLED_CODE =
      "provider worldline publishes three cancel outcomes and two of them share a status name:"
          + " CANCELLED at statusCategory PENDING_MERCHANT and statusCode 61 \"Author. deletion"
          + " waiting\" or 62 \"Author. deletion uncertain\" while the acquirer is still"
          + " answering, CANCELLED at UNSUCCESSFUL and 6 once it has, and CANCELLATION_REJECTED"
          + " at UNSUCCESSFUL and 63. Code 61 \"resolves to either 6 or 63\". This simulator"
          + " answers 6, the settled one, because the two published ways a caller learns how a 61"
          + " resolved are a webhook and GET /payments/{paymentId}/details and this slice serves"
          + " neither, so a payment left at 61 here would stay at 61 for ever. No maintenance"
          + " response body is published anywhere for this route, so the whole of this answer is"
          + " an inference";

  private final WorldlinePayments payments;
  private final WorldlineAnswers answers;
  private final WorldlineIdempotenceKeys keys;
  private final ScenarioEngine scenarios;

  CancelPayment(
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
        WorldlineOperations.CANCEL_PAYMENT,
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
    Optional<WorldlineJson.JsonObject> atTheRoot = body.object(WorldlineFields.AMOUNT_OF_MONEY);
    Optional<WorldlineJson.JsonObject> underOrder =
        body.object(WorldlineFields.ORDER)
            .flatMap(order -> order.object(WorldlineFields.AMOUNT_OF_MONEY));
    if (atTheRoot.isPresent()
        && underOrder.isPresent()
        && !amountIn(atTheRoot).equals(amountIn(underOrder))) {
      return answers.refusedValue(WorldlineFields.AMOUNT_OF_MONEY, BOTH_AMOUNT_PLACES_AT_ONCE);
    }
    Optional<WorldlineJson.JsonObject> amountOfMoney =
        atTheRoot.isPresent() ? atTheRoot : underOrder;
    Optional<WorldlinePayment> found =
        payments
            .findById(testRunId, addressedBy)
            .filter(payment -> payment.accountId().equals(merchant.accountId()));
    if (found.isEmpty()) {
      return answers.noSuchPayment(CapturePayment.NO_PAYMENT_ANSWERS_TO_THAT_ID);
    }
    WorldlinePayment payment = found.get();
    if (!payment.isAnAuthorizationWaitingToBeCaptured()) {
      return answers.cancellationIsNotAllowed(payment.paymentId(), THE_PAYMENT_IS_CLOSED);
    }
    Optional<String> asked = amountOfMoney.flatMap(money -> money.number(WorldlineFields.AMOUNT));
    if (asked.isPresent()
        && WorldlineAmounts.wholeMinorUnits(asked.get())
            .filter(WorldlineAmounts::isWithinThePublishedBounds)
            .isEmpty()) {
      return answers.refusedValue(
          WorldlineFields.AMOUNT_OF_MONEY, THE_AMOUNT_IS_NOT_WITHIN_THE_PUBLISHED_BOUNDS);
    }
    long remaining = payment.capturableRemainder().longValue();
    long amount =
        asked
            .flatMap(WorldlineAmounts::wholeMinorUnits)
            .orElse(Long.valueOf(remaining))
            .longValue();
    if (amount != remaining) {
      return answers.actionNotAllowedOn(
          payment.paymentId(),
          payment.authorizationMode().allowsAPartialCancellation()
              ? THIS_SLICE_CANCELS_IN_FULL_ONLY
              : ONLY_A_PRE_AUTHORIZATION_IS_CANCELLED_IN_PART);
    }
    return decided(headers, testRunId, merchant, payment, addressedBy, amount, now);
  }

  private static String amountIn(Optional<WorldlineJson.JsonObject> amountOfMoney) {
    return amountOfMoney.flatMap(money -> money.number(WorldlineFields.AMOUNT)).orElse("");
  }

  private WorldlineOperationResult decided(
      Map<String, List<String>> headers,
      TestRunId testRunId,
      WorldlineMerchant merchant,
      WorldlinePayment payment,
      String addressedBy,
      long amount,
      Instant now) {
    return WorldlineOperationResult.decidedBy(
        scenarios.decide(requestOf(headers, testRunId, merchant, payment, addressedBy, amount)),
        () -> cancelled(payment, now),
        code -> refused(payment, code, now));
  }

  private static ScenarioRequest requestOf(
      Map<String, List<String>> headers,
      TestRunId testRunId,
      WorldlineMerchant merchant,
      WorldlinePayment payment,
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
        payment.currencyCode(),
        WorldlineOperations.CANCEL_PAYMENT,
        headers);
  }

  private WorldlineOperationResult cancelled(WorldlinePayment payment, Instant now) {
    String minted = payment.theNextIdentifierAMaintenanceOperationMints();
    LedgerClaim applied =
        payments.applyMaintenance(
            payment,
            new WorldlinePayments.WorldlineMaintenance(
                minted,
                payment.maintenanceIds().size() + 1,
                null,
                WorldlineStatus.CANCELLED,
                WorldlineStatus.CATEGORY_UNSUCCESSFUL,
                WorldlineStatus.CODE_CANCELLED,
                true,
                now));
    if (applied != LedgerClaim.APPLIED) {
      return answers.cancellationIsNotAllowed(payment.paymentId(), THE_PAYMENT_IS_CLOSED);
    }
    return WorldlineOperationResult.succeeded(
        minted,
        WorldlineBody.of(
            WorldlineAnswers.OK,
            theCancelResponseOf(
                minted,
                WorldlineStatus.CANCELLED,
                WorldlineStatus.CATEGORY_UNSUCCESSFUL,
                WorldlineStatus.CODE_CANCELLED)));
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
      return answers.cancellationIsNotAllowed(payment.paymentId(), THE_PAYMENT_IS_CLOSED);
    }
    return WorldlineOperationResult.succeeded(
        minted,
        WorldlineBody.of(
            WorldlineAnswers.OK,
            theCancelResponseOf(
                minted,
                WorldlineStatus.CANCELLATION_REJECTED,
                WorldlineStatus.CATEGORY_UNSUCCESSFUL,
                WorldlineStatus.CODE_CANCELLATION_REJECTED)));
  }

  static WorldlineJson.JsonObject theCancelResponseOf(
      String paymentId, String status, String statusCategory, int statusCode) {
    Map<String, WorldlineJson> statusOutput = new LinkedHashMap<>();
    statusOutput.put(WorldlineFields.STATUS_CATEGORY, WorldlineJson.text(statusCategory));
    statusOutput.put(
        WorldlineFields.STATUS_CODE, WorldlineJson.number(Integer.toString(statusCode)));
    Map<String, WorldlineJson> cancelled = new LinkedHashMap<>();
    cancelled.put(WorldlineFields.ID, WorldlineJson.text(paymentId));
    cancelled.put(WorldlineFields.STATUS, WorldlineJson.text(status));
    cancelled.put(WorldlineFields.STATUS_OUTPUT, WorldlineJson.object(statusOutput));
    Map<String, WorldlineJson> response = new LinkedHashMap<>();
    response.put(WorldlineFields.PAYMENT, WorldlineJson.object(cancelled));
    return WorldlineJson.object(response);
  }
}
