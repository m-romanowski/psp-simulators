package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.LedgerClaim;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedObservation;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class CompleteOrReverse {

  private final AzericardPayments payments;
  private final ScenarioEngine scenarios;

  CompleteOrReverse(AzericardPayments payments, ScenarioEngine scenarios) {
    this.payments = payments;
    this.scenarios = scenarios;
  }

  AzericardOperationResult handle(
      AzericardMessage message,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      AzericardTerminal terminal,
      Instant now) {
    Optional<AzericardPayment> addressed =
        payments.findAddressed(
            testRunId, terminal.terminalId(), message.order(), message.rrn(), message.intRef());
    if (addressed.isEmpty()) {
      return refused(
          RefusalSubject.noObjectAnsweredTo(AzericardFields.INT_REF),
          message,
          terminal,
          now,
          AzericardOutcomes.RC_NO_SUCH_TRANSACTION);
    }
    AzericardPayment payment = addressed.get();
    AzericardOperationResult result =
        answeredFor(message, headers, testRunId, terminal, payment, now);
    correlated(message, testRunId, terminal, payment, now, result);
    return result;
  }

  private AzericardOperationResult answeredFor(
      AzericardMessage message,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      AzericardTerminal terminal,
      AzericardPayment payment,
      Instant now) {
    Optional<BigDecimal> requested = positive(message.amount());
    if (requested.isEmpty() || !payment.currency().equals(message.currency())) {
      return refused(
          RefusalSubject.theObject(payment.intRef()),
          message,
          terminal,
          now,
          AzericardOutcomes.RC_NOT_ADMITTED);
    }
    BigDecimal admitted = theAmountThisPaymentAdmits(payment, message.transactionType());
    if (!admits(message.transactionType(), requested.get(), admitted)) {
      return refused(
          RefusalSubject.theObject(payment.intRef()),
          message,
          terminal,
          now,
          AzericardOutcomes.RC_NOT_ADMITTED);
    }
    return AzericardOperationResult.decidedBy(
        scenarios.decide(requestOf(message, headers, testRunId, terminal, payment)),
        () -> apply(message, terminal, payment, requested.get(), admitted, now),
        code -> declined(message, terminal, payment, now, code));
  }

  private void correlated(
      AzericardMessage message,
      TestRunId testRunId,
      AzericardTerminal terminal,
      AzericardPayment payment,
      Instant now,
      AzericardOperationResult result) {
    record(
        message,
        testRunId,
        terminal,
        now,
        AzericardKeyMechanisms.RRN,
        payment.rrn(),
        payment.intRef(),
        result);
    record(
        message,
        testRunId,
        terminal,
        now,
        AzericardKeyMechanisms.INT_REF,
        payment.intRef(),
        payment.intRef(),
        result);
  }

  private void record(
      AzericardMessage message,
      TestRunId testRunId,
      AzericardTerminal terminal,
      Instant now,
      String keyName,
      String keyValue,
      String providerObjectId,
      AzericardOperationResult result) {
    payments.observed(
        new KeyedObservation(
            testRunId,
            terminal.accountId(),
            keyName,
            keyValue,
            message.operationName(),
            ExchangeVerdict.FIRST_REQUEST,
            message.fingerprinted(),
            answeredNothing(result)
                ? null
                : new KeyedAnswer(
                    AzericardGateway.GATEWAY_HTTP_STATUS,
                    result.body().contentType(),
                    result.body().fields()),
            providerObjectId,
            result.outcome() == ProviderOutcome.SUCCESS,
            null,
            now));
  }

  private static boolean answeredNothing(AzericardOperationResult result) {
    return result.insteadOfTheOperation() != null;
  }

  private static BigDecimal theAmountThisPaymentAdmits(
      AzericardPayment payment, AzericardTransactionType type) {
    if (type.reversesThePayment()) {
      return payment.reversibleAmount();
    }
    return payment.acceptsACompletion() ? payment.capturableRemainder() : BigDecimal.ZERO;
  }

  private static boolean admits(
      AzericardTransactionType type, BigDecimal requested, BigDecimal admitted) {
    return type.reversesThePayment()
        ? requested.compareTo(admitted) == 0
        : requested.compareTo(admitted) <= 0;
  }

  private static Optional<BigDecimal> positive(String amount) {
    return Amount.of(amount).number().filter(value -> value.signum() > 0);
  }

  private static ScenarioRequest requestOf(
      AzericardMessage message,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      AzericardTerminal terminal,
      AzericardPayment payment) {
    return new ScenarioRequest(
        AzericardGateway.AZERICARD,
        testRunId,
        AzericardStoryKey.ofPayment(terminal.terminalId(), message.order()),
        terminal.accountId(),
        message.order(),
        payment.intRef(),
        null,
        null,
        Amount.of(message.amount()),
        message.currency(),
        message.operationName(),
        headers);
  }

  private AzericardOperationResult apply(
      AzericardMessage message,
      AzericardTerminal terminal,
      AzericardPayment payment,
      BigDecimal requested,
      BigDecimal admitted,
      Instant now) {
    AzericardTransactionType type = message.transactionType();
    if (type.reversesThePayment() && payment.aReversalWouldReleaseTheHold()) {
      return appliedAs(payments.reverseTheHold(payment, now), message, terminal, payment, now);
    }
    Boolean closesAuthorization =
        type.reversesThePayment()
            ? null
            : terminal
                .partialCompletion()
                .closesTheAuthorizationAfter(requested.compareTo(admitted) == 0);
    MonetaryOperation operation =
        new MonetaryOperation(
            payment.ledger().nextSequence(),
            type.movement().writes(),
            Amount.of(message.amount()),
            closesAuthorization,
            null,
            AzericardOutcomes.ACTION_SUCCEEDED
                + AzericardPayment.NATIVE_STATUS_SEPARATOR
                + AzericardOutcomes.RC_APPROVED,
            appliedStatusOf(type, closesAuthorization),
            now);
    return appliedAs(payments.append(payment, operation, now), message, terminal, payment, now);
  }

  private static AzericardOperationResult appliedAs(
      LedgerClaim applied,
      AzericardMessage message,
      AzericardTerminal terminal,
      AzericardPayment payment,
      Instant now) {
    return switch (applied) {
      case APPLIED ->
          AzericardOperationResult.succeeded(
              payment.intRef(),
              answer(
                  message,
                  terminal,
                  payment,
                  now,
                  AzericardOutcomes.DESC_APPROVED,
                  AzericardOutcomes.ACTION_SUCCEEDED,
                  AzericardOutcomes.RC_APPROVED,
                  approvalOf(payment)));
      case CLAIM_TAKEN ->
          refused(
              RefusalSubject.theObject(payment.intRef()),
              message,
              terminal,
              now,
              AzericardOutcomes.RC_NOT_ADMITTED);
      case OBJECT_GONE ->
          refused(
              RefusalSubject.noObjectAnsweredTo(AzericardFields.INT_REF),
              message,
              terminal,
              now,
              AzericardOutcomes.RC_NO_SUCH_TRANSACTION);
    };
  }

  private static NormalizedStatus appliedStatusOf(
      AzericardTransactionType type, @Nullable Boolean closesAuthorization) {
    if (type.reversesThePayment() || Boolean.TRUE.equals(closesAuthorization)) {
      return type.movement().whenApproved();
    }
    return NormalizedStatus.PARTIALLY_CAPTURED;
  }

  private static AzericardOperationResult declined(
      AzericardMessage message,
      AzericardTerminal terminal,
      AzericardPayment payment,
      Instant now,
      String responseCode) {
    return AzericardOperationResult.failed(
        RefusalSubject.theObject(payment.intRef()),
        answer(
            message,
            terminal,
            payment,
            now,
            AzericardOutcomes.NOTHING_PUBLISHED,
            AzericardOutcomes.ACTION_DECLINED,
            responseCode,
            AzericardOutcomes.ABSENT));
  }

  private static AzericardOperationResult refused(
      RefusalSubject refusal,
      AzericardMessage message,
      AzericardTerminal terminal,
      Instant now,
      String responseCode) {
    return AzericardOperationResult.failed(
        refusal,
        new AzericardResponse(
                AzericardOutcomes.NOTHING_PUBLISHED,
                message.terminal(),
                message.trtype(),
                message.order(),
                message.amount(),
                AzericardOutcomes.ABSENT,
                AzericardOutcomes.ACTION_ERROR,
                responseCode,
                AzericardOutcomes.ABSENT,
                message.rrn(),
                message.intRef(),
                AzericardTimestamp.write(now),
                message.nonce())
            .signedWith(terminal.gatewayKey()));
  }

  private static String approvalOf(AzericardPayment payment) {
    String approval = payment.approval();
    return approval == null ? AzericardOutcomes.ABSENT : approval;
  }

  private static AzericardBody answer(
      AzericardMessage message,
      AzericardTerminal terminal,
      AzericardPayment payment,
      Instant now,
      String desc,
      String action,
      String responseCode,
      String approval) {
    return new AzericardResponse(
            desc,
            message.terminal(),
            message.trtype(),
            message.order(),
            message.amount(),
            message.currency(),
            action,
            responseCode,
            approval,
            payment.rrn(),
            payment.intRef(),
            AzericardTimestamp.write(now),
            message.nonce())
        .signedWith(terminal.gatewayKey());
  }
}
