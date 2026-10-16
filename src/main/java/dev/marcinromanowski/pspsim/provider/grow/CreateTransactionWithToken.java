package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.idempotency.FingerprintedRequest;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedOutcome;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedObservation;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

final class CreateTransactionWithToken {

  static final int ID_ATTEMPTS = 5;

  private static final int TRANSACTION_ID_DIGITS = 6;

  private static final int TRANSACTION_TOKEN_LENGTH = 18;

  private static final int ASMACHTA_DIGITS = 8;

  private static final Pattern UNSIGNED_WHOLE_NUMBER = Pattern.compile("[1-9][0-9]{0,9}");

  private static final long WIDEST_THIRTY_TWO_BIT_VALUE = 4_294_967_295L;

  private static final String NOTHING_TO_GROW = "0";

  private static final DateTimeFormatter PAYMENT_DATE =
      DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ROOT).withZone(ZoneOffset.UTC);

  private final GrowPaymentProcesses processes;
  private final GrowTransactions transactions;
  private final IdGenerator ids;
  private final ScenarioEngine scenarios;
  private final GrowScalarType scalarType;

  CreateTransactionWithToken(
      GrowPaymentProcesses processes,
      GrowTransactions transactions,
      IdGenerator ids,
      ScenarioEngine scenarios,
      GrowScalarType scalarType) {
    this.processes = processes;
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
    Amount charged = Amount.of(orEmpty(form.first(GrowFields.SUM)));
    if (charged.number().filter(value -> value.signum() > 0).isEmpty()) {
      return failure(GrowRefusals.theFormWasRefused(), GrowErrors.amountNotValid());
    }

    for (String required : GrowFields.REQUIRED_ON_CREATE_TRANSACTION_WITH_TOKEN) {
      if (!form.has(required)) {
        return failure(GrowRefusals.theFormWasRefused(), GrowErrors.missingRequiredField(required));
      }
    }

    String uniqueIdentifier = form.first(GrowFields.TRANSACTION_UNIQUE_IDENTIFIER);
    if (uniqueIdentifier == null || NOTHING_TO_GROW.equals(uniqueIdentifier)) {
      return failure(GrowRefusals.theFormWasRefused(), GrowErrors.theUserDoesNotExist());
    }
    if (!isAWholeNumberGrowCanHold(uniqueIdentifier)) {
      return failure(GrowRefusals.theFormWasRefused(), GrowErrors.invalidUniqueIdentifier());
    }

    if (credentialMatches.size() != 1) {
      return failure(
          GrowRefusals.theCredentialsReachNoMerchant(),
          GrowErrors.noBusinessForTheUserIdOfAStoredCardCharge());
    }
    GrowAccount account = credentialMatches.get(0);

    String cardToken = form.required(GrowFields.CARD_TOKEN);
    Optional<GrowRecordedCard> tokenised =
        theCardTheTokenWasSavedFor(testRunId, account, cardToken);
    if (tokenised.isEmpty()) {
      return failure(
          RefusalSubject.noObjectAnsweredTo(GrowFields.CARD_TOKEN), GrowErrors.unknownCardToken());
    }
    GrowRecordedCard card = tokenised.get();

    String transactionId = aTransactionIdClaimedForThisDecision(testRunId);
    GrowStoredCardCharge submission = submissionOf(form, testRunId, account, transactionId, now);
    return GrowOperationResult.decidedBy(
        scenarios.decide(requestOf(headers, testRunId, account, cardToken, charged, transactionId)),
        () -> charge(form, transactionId, submission, charged, now, account, card, askedBy),
        code -> declined(submission, code, account, form, now));
  }

  private static boolean isAWholeNumberGrowCanHold(String uniqueIdentifier) {
    if (!UNSIGNED_WHOLE_NUMBER.matcher(uniqueIdentifier).matches()) {
      return false;
    }
    return Long.parseLong(uniqueIdentifier) <= WIDEST_THIRTY_TWO_BIT_VALUE;
  }

  private static String orEmpty(@Nullable String value) {
    return value == null ? "" : value;
  }

  private String aTransactionIdClaimedForThisDecision(TestRunId testRunId) {
    for (int attempt = 0; attempt < ID_ATTEMPTS; attempt++) {
      String transactionId = ids.digits(TRANSACTION_ID_DIGITS);
      if (transactions.claimTransactionId(testRunId, transactionId)) {
        return transactionId;
      }
    }
    throw new IllegalStateException(
        "no Grow transactionId could be claimed in " + ID_ATTEMPTS + " attempts");
  }

  private Optional<GrowRecordedCard> theCardTheTokenWasSavedFor(
      TestRunId testRunId, GrowAccount account, String cardToken) {
    return processes
        .findByCardToken(testRunId, account.accountId(), cardToken)
        .flatMap(saved -> Optional.ofNullable(saved.outcome()))
        .map(GrowProcessOutcome::cardCharged);
  }

  private GrowOperationResult charge(
      GrowForm form,
      String transactionId,
      GrowStoredCardCharge submission,
      Amount charged,
      Instant now,
      GrowAccount account,
      GrowRecordedCard card,
      ObjectOrigin askedBy) {
    GrowTransaction row = chargedRow(form, transactionId, submission, charged, now, card, askedBy);
    return switch (transactions.charge(row, submission)) {
      case APPLIED ->
          admitted(
              submission,
              account,
              form,
              now,
              row.transactionId(),
              GrowOperationResult.succeeded(
                  row.transactionId(),
                  GrowEnvelope.success(GrowTransactionRecord.storedCardCharge(row), scalarType)));
      case UNIQUE_IDENTIFIER_TAKEN ->
          refused(submission, account, form, now, GrowKeyMechanisms.UNIQUE_IDENTIFIER);
      case GROUP_ALREADY_CHARGED ->
          refused(submission, account, form, now, GrowKeyMechanisms.GROUP_IDENTIFIER);
      case TRANSACTION_ID_TAKEN ->
          throw new IllegalStateException(
              "the Grow transactionId this decision was taken under was claimed by another"
                  + " request before the stored-card charge could be stored");
    };
  }

  private GrowOperationResult declined(
      GrowStoredCardCharge submission,
      String providerCode,
      GrowAccount account,
      GrowForm form,
      Instant now) {
    return switch (transactions.submit(submission.submittedWithoutACharge())) {
      case APPLIED ->
          admitted(
              submission,
              account,
              form,
              now,
              null,
              failure(
                  RefusalSubject.noObjectWasNamed(
                      GrowRefusals.A_SCENARIO_DECLINED_BEFORE_A_TRANSACTION_WAS_STORED),
                  GrowErrors.chosenByScenario(providerCode)));
      case UNIQUE_IDENTIFIER_TAKEN ->
          refused(submission, account, form, now, GrowKeyMechanisms.UNIQUE_IDENTIFIER);
      case GROUP_ALREADY_CHARGED ->
          refused(submission, account, form, now, GrowKeyMechanisms.GROUP_IDENTIFIER);
    };
  }

  private GrowOperationResult admitted(
      GrowStoredCardCharge submission,
      GrowAccount account,
      GrowForm form,
      Instant now,
      @Nullable String providerObjectId,
      GrowOperationResult result) {
    record(
        submission,
        account,
        form,
        now,
        GrowKeyMechanisms.UNIQUE_IDENTIFIER,
        submission.uniqueIdentifier(),
        KeyedOutcome.RAN_THE_OPERATION,
        providerObjectId,
        true,
        result);
    String group = submission.groupIdentifier();
    if (group != null) {
      record(
          submission,
          account,
          form,
          now,
          GrowKeyMechanisms.GROUP_IDENTIFIER,
          group,
          KeyedOutcome.RAN_THE_OPERATION,
          providerObjectId,
          true,
          result);
    }
    return result;
  }

  private GrowOperationResult refused(
      GrowStoredCardCharge submission,
      GrowAccount account,
      GrowForm form,
      Instant now,
      String keyName) {
    GrowOperationResult result =
        failure(
            RefusalSubject.anObjectItWillNotName(theKeyThatIsAlreadyHeld(keyName)),
            GrowErrors.alreadyCarriedOut());
    String keyValue =
        GrowKeyMechanisms.UNIQUE_IDENTIFIER.equals(keyName)
            ? submission.uniqueIdentifier()
            : String.valueOf(submission.groupIdentifier());
    record(
        submission,
        account,
        form,
        now,
        keyName,
        keyValue,
        KeyedOutcome.REFUSED_AS_A_DUPLICATE,
        null,
        false,
        result);
    return result;
  }

  private void record(
      GrowStoredCardCharge submission,
      GrowAccount account,
      GrowForm form,
      Instant now,
      String keyName,
      String keyValue,
      KeyedOutcome outcome,
      @Nullable String providerObjectId,
      boolean stateChanged,
      GrowOperationResult result) {
    transactions.observed(
        new KeyedObservation(
            submission.testRunId(),
            account.accountId(),
            keyName,
            keyValue,
            GrowOperations.CREATE_TRANSACTION_WITH_TOKEN,
            outcome,
            new FingerprintedRequest(
                GrowLightApi.WIRE_METHOD,
                GrowOperations.CREATE_TRANSACTION_WITH_TOKEN,
                form.values()),
            new KeyedAnswer(GrowLightApi.GROW_HTTP_STATUS, result.body().flatten()),
            providerObjectId,
            stateChanged,
            null,
            now));
  }

  private GrowTransaction chargedRow(
      GrowForm form,
      String transactionId,
      GrowStoredCardCharge submission,
      Amount charged,
      Instant now,
      GrowRecordedCard card,
      ObjectOrigin askedBy) {
    return new GrowTransaction(
        submission.testRunId(),
        GrowTransactionKind.TOKEN_PAYMENT,
        transactionId,
        ids.hex(TRANSACTION_TOKEN_LENGTH),
        submission.merchantId(),
        null,
        null,
        ids.digits(ASMACHTA_DIGITS),
        PAYMENT_DATE.format(now),
        form.required(GrowFields.DESCRIPTION),
        form.required(GrowFields.FULL_NAME),
        form.required(GrowFields.PHONE),
        form.first(GrowFields.EMAIL),
        card,
        charged,
        Ledger.empty().with(capture(charged, now)),
        askedBy,
        now,
        now);
  }

  private static MonetaryOperation capture(Amount charged, Instant now) {
    return new MonetaryOperation(
        1,
        OperationKind.CAPTURE,
        charged,
        true,
        null,
        GrowNativeStatus.PAID_CODE,
        NormalizedStatus.SUCCEEDED,
        now);
  }

  private static GrowStoredCardCharge submissionOf(
      GrowForm form, TestRunId testRunId, GrowAccount account, String transactionId, Instant now) {
    return new GrowStoredCardCharge(
        testRunId,
        account.accountId(),
        form.required(GrowFields.TRANSACTION_UNIQUE_IDENTIFIER),
        form.first(GrowFields.TRANSACTION_GROUP_IDENTIFIER),
        transactionId,
        customFieldsOf(form),
        now);
  }

  private static Map<String, String> customFieldsOf(GrowForm form) {
    Map<String, String> customFields = new LinkedHashMap<>();
    for (String name : GrowFields.CUSTOM_FIELDS) {
      String value = form.first(name);
      if (value != null) {
        customFields.put(name, value);
      }
    }
    return customFields;
  }

  private static ScenarioRequest requestOf(
      Map<String, List<String>> headers,
      TestRunId testRunId,
      GrowAccount account,
      String cardToken,
      Amount charged,
      String transactionId) {
    return new ScenarioRequest(
        GrowLightApi.GROW,
        testRunId,
        GrowStoryKey.ofTransactionId(transactionId),
        account.accountId(),
        null,
        null,
        null,
        cardToken,
        charged,
        null,
        GrowOperations.CREATE_TRANSACTION_WITH_TOKEN,
        headers);
  }

  private static String theKeyThatIsAlreadyHeld(String keyName) {
    return GrowKeyMechanisms.UNIQUE_IDENTIFIER.equals(keyName)
        ? GrowRefusals.THE_UNIQUE_IDENTIFIER_IS_ALREADY_HELD
        : GrowRefusals.THE_GROUP_IS_ALREADY_CHARGED;
  }

  private GrowOperationResult failure(RefusalSubject refusal, GrowError error) {
    return GrowOperationResult.failed(refusal, GrowEnvelope.failure(error, scalarType));
  }
}
