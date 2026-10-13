package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class LookUpStatus {

  private final AzericardPayments payments;
  private final ScenarioEngine scenarios;

  LookUpStatus(AzericardPayments payments, ScenarioEngine scenarios) {
    this.payments = payments;
    this.scenarios = scenarios;
  }

  AzericardOperationResult handle(
      AzericardMessage message,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      AzericardTerminal terminal,
      Instant now) {
    return AzericardOperationResult.decidedBy(
        scenarios.decide(requestOf(message, headers, testRunId, terminal)),
        () -> report(message, testRunId, terminal, now),
        code -> declined(message, terminal, now, code));
  }

  private static ScenarioRequest requestOf(
      AzericardMessage message,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      AzericardTerminal terminal) {
    return new ScenarioRequest(
        AzericardGateway.AZERICARD,
        testRunId,
        AzericardStoryKey.ofPayment(terminal.terminalId(), message.order()),
        terminal.accountId(),
        message.order(),
        null,
        null,
        null,
        null,
        null,
        message.operationName(),
        headers);
  }

  private AzericardOperationResult report(
      AzericardMessage message, TestRunId testRunId, AzericardTerminal terminal, Instant now) {
    List<AzericardPayment> addressed =
        payments.byOrder(testRunId, terminal.terminalId(), message.order());
    if (addressed.isEmpty()) {
      return refused(
          RefusalSubject.noObjectAnsweredTo(AzericardFields.ORDER),
          message,
          terminal,
          now,
          AzericardOutcomes.RC_TRANSACTION_CONTEXT_MISMATCH,
          AzericardOutcomes.DESC_TRANSACTION_CONTEXT_MISMATCH);
    }
    List<AzericardPayment> answerable = new ArrayList<>();
    for (AzericardPayment payment : addressed) {
      if (AzericardTimestamp.withinTheStatusWindow(payment.createdAt(), now)) {
        answerable.add(payment);
      }
    }
    if (answerable.isEmpty()) {
      return refused(
          theOrderAnsweredBy(addressed),
          message,
          terminal,
          now,
          AzericardOutcomes.RC_OUTSIDE_THE_STATUS_WINDOW,
          AzericardOutcomes.NOTHING_PUBLISHED);
    }
    if (answerable.size() > 1) {
      return refused(
          RefusalSubject.severalObjectsAnsweredTo(AzericardFields.ORDER, answerable.size()),
          message,
          terminal,
          now,
          AzericardOutcomes.RC_MORE_THAN_ONE_TRANSACTION,
          AzericardOutcomes.NOTHING_PUBLISHED);
    }
    return answered(message, terminal, now, answerable.get(0));
  }

  private static AzericardOperationResult answered(
      AzericardMessage message, AzericardTerminal terminal, Instant now, AzericardPayment payment) {
    String approval = payment.approval();
    AzericardStatusResponse reported =
        new AzericardStatusResponse(
            message.terminal(),
            payment.action(),
            payment.responseCode(),
            payment.wasApproved()
                ? AzericardOutcomes.DESC_APPROVED
                : AzericardOutcomes.NOTHING_PUBLISHED,
            payment.amount().value(),
            payment.currency(),
            AzericardTimestamp.write(payment.createdAt()),
            payment.order(),
            approval == null ? AzericardOutcomes.ABSENT : approval,
            payment.rrn(),
            payment.intRef(),
            payment.transactionType().wireValue(),
            message.nonce(),
            AzericardTimestamp.write(now));
    return AzericardOperationResult.succeeded(
        payment.intRef(), reported.signedWith(terminal.gatewayKey()));
  }

  private static RefusalSubject theOrderAnsweredBy(List<AzericardPayment> addressed) {
    return addressed.size() == 1
        ? RefusalSubject.theObject(addressed.get(0).intRef())
        : RefusalSubject.severalObjectsAnsweredTo(AzericardFields.ORDER, addressed.size());
  }

  private static AzericardOperationResult declined(
      AzericardMessage message, AzericardTerminal terminal, Instant now, String responseCode) {
    return AzericardOperationResult.failed(
        RefusalSubject.noObjectWasNamed(
            AzericardRefusals.A_SCENARIO_DECLINED_BEFORE_A_PAYMENT_WAS_ADDRESSED),
        AzericardStatusResponse.refusing(
                message,
                AzericardOutcomes.ACTION_DECLINED,
                responseCode,
                AzericardOutcomes.NOTHING_PUBLISHED,
                AzericardTimestamp.write(now))
            .signedWith(terminal.gatewayKey()));
  }

  private static AzericardOperationResult refused(
      RefusalSubject refusal,
      AzericardMessage message,
      AzericardTerminal terminal,
      Instant now,
      String responseCode,
      String statusMsg) {
    return AzericardOperationResult.failed(
        refusal,
        AzericardStatusResponse.refusing(
                message,
                AzericardOutcomes.ACTION_ERROR,
                responseCode,
                statusMsg,
                AzericardTimestamp.write(now))
            .signedWith(terminal.gatewayKey()));
  }
}
