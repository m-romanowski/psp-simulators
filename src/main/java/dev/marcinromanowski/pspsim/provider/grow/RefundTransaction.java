package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class RefundTransaction {

  static final int ID_ATTEMPTS = 5;

  private static final int TRANSACTION_ID_DIGITS = 6;

  private static final int ASMACHTA_DIGITS = 8;

  private static final DateTimeFormatter PAYMENT_DATE =
      DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ROOT).withZone(ZoneOffset.UTC);

  private static final GrowJson REFUNDED_STATUS_CODE =
      GrowJson.number(Long.parseLong(GrowNativeStatus.REFUNDED_CODE));

  private static final String CARD_TRANSACTION_TYPE_ID = "1";

  private static final String REGULAR_PAYMENT_TYPE = "2";

  private final GrowTransactions transactions;
  private final IdGenerator ids;
  private final ScenarioEngine scenarios;
  private final GrowScalarType scalarType;

  RefundTransaction(
      GrowTransactions transactions,
      IdGenerator ids,
      ScenarioEngine scenarios,
      GrowScalarType scalarType) {
    this.transactions = transactions;
    this.ids = ids;
    this.scenarios = scenarios;
    this.scalarType = scalarType;
  }

  GrowOperationResult handle(
      GrowForm form,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      List<GrowAccount> credentialMatches,
      Instant now,
      ObjectOrigin askedBy) {
    for (String required : GrowFields.REQUIRED_ON_REFUND_TRANSACTION) {
      if (!form.has(required)) {
        return failure(GrowRefusals.theFormWasRefused(), GrowErrors.missingRequiredField(required));
      }
    }

    if (credentialMatches.size() != 1) {
      return failure(
          GrowRefusals.theCredentialsReachNoMerchant(), theCredentialShapeIsRefusedAs(form));
    }
    GrowAccount account = credentialMatches.get(0);

    Optional<GrowTransaction> found =
        transactions.find(
            testRunId,
            account.accountId(),
            form.required(GrowFields.TRANSACTION_ID),
            form.required(GrowFields.TRANSACTION_TOKEN));
    if (found.isEmpty()) {
      return failure(
          RefusalSubject.noObjectAnsweredTo(GrowFields.TRANSACTION_ID),
          GrowErrors.invalidLookupCredentials());
    }
    GrowTransaction charged = found.get();

    if (!charged.isReleased()) {
      return failure(theTransaction(charged), GrowErrors.notCarriedOut());
    }
    if (!charged.acceptsAnotherRefund(account.refundAllowance())) {
      return failure(theTransaction(charged), GrowErrors.alreadyRefunded());
    }

    Amount requested = Amount.of(form.required(GrowFields.REFUND_SUM));
    Optional<BigDecimal> refunded = usable(requested);
    if (refunded.isEmpty()) {
      return failure(theTransaction(charged), GrowErrors.refundAmountNotValid());
    }
    Optional<BigDecimal> left = charged.ledger().refundableRemainder();
    if (left.isEmpty() || refunded.get().compareTo(left.get()) > 0) {
      return failure(theTransaction(charged), account.refundExcessRejection().rejection());
    }
    if (charged.wasChargedOnTheDayOf(now) && refunded.get().compareTo(left.get()) < 0) {
      return failure(theTransaction(charged), GrowErrors.partialRefundOfATransactionMadeToday());
    }
    if (!account.merchantBalance().fundsARefund()) {
      return failure(theTransaction(charged), GrowErrors.noBalanceToFundTheRefund());
    }

    return GrowOperationResult.decidedBy(
        scenarios.decide(requestOf(headers, testRunId, account, charged, requested)),
        () -> refund(charged, requested, now, askedBy),
        code -> failure(theTransaction(charged), GrowErrors.chosenByScenario(code)));
  }

  private static ScenarioRequest requestOf(
      Map<String, List<String>> headers,
      TestRunId testRunId,
      GrowAccount account,
      GrowTransaction charged,
      Amount requested) {
    return new ScenarioRequest(
        GrowLightApi.GROW,
        testRunId,
        GrowStoryKey.ofTransaction(charged),
        account.accountId(),
        null,
        charged.transactionId(),
        null,
        null,
        requested,
        null,
        GrowOperations.REFUND_TRANSACTION,
        headers);
  }

  private static GrowError theCredentialShapeIsRefusedAs(GrowForm form) {
    if (form.has(GrowFields.USER_ID)) {
      return GrowErrors.invalidBusinessIdentificationCode();
    }
    if (form.has(GrowFields.PAGE_CODE)) {
      return GrowErrors.invalidLookupCredentials();
    }
    return GrowErrors.eitherUserIdOrPageCodeIsRequired();
  }

  private static Optional<BigDecimal> usable(Amount amount) {
    return amount.number().filter(value -> value.signum() > 0);
  }

  private GrowOperationResult refund(
      GrowTransaction charged, Amount refunded, Instant now, ObjectOrigin askedBy) {
    String asmachta = ids.digits(ASMACHTA_DIGITS);
    for (int attempt = 0; attempt < ID_ATTEMPTS; attempt++) {
      String refundId = ids.digits(TRANSACTION_ID_DIGITS);
      MonetaryOperation operation = refundOperation(charged, refundId, refunded, now);
      GrowTransaction row = refundRow(charged, refundId, asmachta, refunded, now, askedBy);
      switch (transactions.refund(charged.with(operation, now), operation, row)) {
        case APPLIED -> {
          return GrowOperationResult.succeeded(refundId, successBody(charged, row, refunded));
        }
        case ID_TAKEN -> {}
        case CLAIM_TAKEN -> {
          return failure(theTransaction(charged), GrowErrors.alreadyRefunded());
        }
        case OBJECT_GONE -> {
          return failure(
              RefusalSubject.noObjectAnsweredTo(GrowFields.TRANSACTION_ID),
              GrowErrors.invalidLookupCredentials());
        }
      }
    }
    throw new IllegalStateException(
        "no unused Grow refund transactionId was found in " + ID_ATTEMPTS + " attempts");
  }

  private static MonetaryOperation refundOperation(
      GrowTransaction charged, String refundId, Amount refunded, Instant now) {
    return new MonetaryOperation(
        charged.ledger().nextSequence(),
        OperationKind.REFUND,
        refunded,
        null,
        refundId,
        GrowNativeStatus.REFUNDED_CODE,
        NormalizedStatus.SUCCEEDED,
        now);
  }

  private static GrowTransaction refundRow(
      GrowTransaction charged,
      String refundId,
      String asmachta,
      Amount refunded,
      Instant now,
      ObjectOrigin askedBy) {
    return new GrowTransaction(
        charged.testRunId(),
        GrowTransactionKind.REFUND,
        refundId,
        null,
        charged.merchantId(),
        null,
        charged.transactionId(),
        asmachta,
        PAYMENT_DATE.format(now),
        charged.description(),
        charged.payerFullName(),
        charged.payerPhone(),
        charged.payerEmail(),
        charged.card(),
        refunded,
        Ledger.empty(),
        askedBy,
        now,
        now);
  }

  private GrowJson successBody(GrowTransaction charged, GrowTransaction row, Amount refunded) {
    GrowRecordedCard card = row.card();
    return GrowEnvelope.success(
        GrowJson.object(
            GrowResponseFields.REFUNDED_TRANSACTION_ID,
                GrowJson.number(Long.parseLong(charged.transactionId())),
            GrowResponseFields.ASMACHTA, GrowJson.text(row.asmachta()),
            GrowResponseFields.CARD_SUFFIX, GrowJson.text(card.suffix()),
            GrowResponseFields.CARD_TYPE, GrowJson.text(card.type()),
            GrowResponseFields.CARD_TYPE_CODE, GrowJson.text(card.typeCode()),
            GrowResponseFields.CARD_BRAND, GrowJson.text(card.brand()),
            GrowResponseFields.CARD_BRAND_CODE, GrowJson.text(card.brandCode()),
            GrowResponseFields.CARD_EXP, GrowJson.text(card.expiry()),
            GrowResponseFields.STATUS, GrowJson.text(GrowNativeStatus.REFUNDED),
            GrowResponseFields.STATUS_CODE, REFUNDED_STATUS_CODE,
            GrowResponseFields.TRANSACTION_TYPE_ID, GrowJson.text(CARD_TRANSACTION_TYPE_ID),
            GrowResponseFields.PAYMENT_TYPE, GrowJson.text(REGULAR_PAYMENT_TYPE),
            GrowResponseFields.SUM, GrowJson.text(refunded.value()),
            GrowResponseFields.PAYMENT_DATE, GrowJson.text(row.paymentDate()),
            GrowResponseFields.DESCRIPTION, GrowJson.text(row.description()),
            GrowResponseFields.FULL_NAME, GrowJson.text(row.payerFullName()),
            GrowResponseFields.PAYER_PHONE, GrowJson.text(row.payerPhone()),
            GrowResponseFields.PAYER_EMAIL, GrowJson.text(orEmpty(row.payerEmail())),
            GrowResponseFields.TRANSACTION_ID, GrowJson.text(row.transactionId())),
        scalarType);
  }

  private static RefusalSubject theTransaction(GrowTransaction charged) {
    return RefusalSubject.theObject(charged.transactionId());
  }

  private GrowOperationResult failure(RefusalSubject refusal, GrowError error) {
    return GrowOperationResult.failed(refusal, GrowEnvelope.failure(error, scalarType));
  }

  private static String orEmpty(@Nullable String value) {
    return value == null ? "" : value;
  }
}
