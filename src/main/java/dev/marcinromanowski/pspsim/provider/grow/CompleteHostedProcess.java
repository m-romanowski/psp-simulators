package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ScheduledWebhook;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class CompleteHostedProcess {

  static final String ALREADY_COMPLETED =
      "this hosted process has already reached an outcome. A payer completes a hosted page once,"
          + " and a second completion would charge a card the merchant has already been told"
          + " about; a duplicate or repeated callback for a process that is already at an outcome"
          + " is what POST /__simulator/grow/webhooks is for";

  static final String NO_SUCH_CARD = "no card of this test run is named ";

  static final String THE_CARDS_OFFERED = ". The cards a payer may name here are: ";

  static final String NO_ONE_TO_TELL =
      "this completion was refused before it changed anything, because the callback it would cause"
          + " has nowhere to go: ";

  static final String STILL_COMPLETABLE =
      ". Configure that callback target for this test run and complete this page again: the"
          + " payment process has reached no outcome and is still completable.";

  sealed interface Result {

    record Completed(
        GrowPaymentProcess process, PayerOutcome outcome, List<ScheduledWebhook> callbacks)
        implements Result {

      public Completed {
        callbacks = List.copyOf(callbacks);
      }

      public @Nullable String createdTransactionId() {
        GrowProcessOutcome reached = process.outcome();
        return reached == null ? null : reached.transactionId();
      }
    }

    record Refused(String inItsOwnWords) implements Result {}

    record NoSuchProcess() implements Result {}
  }

  private final GrowPaymentProcesses processes;
  private final GrowCallbacks callbacks;
  private final GrowPayerCards cards;

  CompleteHostedProcess(
      GrowPaymentProcesses processes, GrowCallbacks callbacks, GrowPayerCards cards) {
    this.processes = processes;
    this.callbacks = callbacks;
    this.cards = cards;
  }

  HostedCompletionOffer offeredOn(TestRunId testRunId, String providerObjectId) {
    return GrowHostedCompletionOffer.of(
        processes.findByProcessId(testRunId, providerObjectId).orElse(null),
        cards.offeredBy(testRunId));
  }

  Result byProcessId(
      TestRunId testRunId,
      String processId,
      PayerOutcome outcome,
      @Nullable String cardAlias,
      ObjectOrigin askedBy) {
    Optional<GrowPaymentProcess> found = processes.findByProcessId(testRunId, processId);
    if (found.isEmpty()) {
      return new Result.NoSuchProcess();
    }
    return complete(found.get(), outcome, cardAlias, askedBy);
  }

  Result byHostedLink(
      String hostedLink, PayerOutcome outcome, @Nullable String cardAlias, ObjectOrigin askedBy) {
    Optional<GrowPaymentProcess> found = processes.findByHostedLink(hostedLink);
    if (found.isEmpty()) {
      return new Result.NoSuchProcess();
    }
    return complete(found.get(), outcome, cardAlias, askedBy);
  }

  Result complete(
      GrowPaymentProcess process,
      PayerOutcome outcome,
      @Nullable String cardAlias,
      ObjectOrigin askedBy) {
    if (process.outcome() != null) {
      return new Result.Refused(ALREADY_COMPLETED);
    }
    TestRunId testRunId = process.testRunId();
    GrowPayerCard card = null;
    if (outcome.namesACard()) {
      Optional<GrowPayerCard> resolved = cards.resolve(testRunId, requireCard(cardAlias));
      if (resolved.isEmpty()) {
        return new Result.Refused(
            NO_SUCH_CARD
                + requireCard(cardAlias)
                + THE_CARDS_OFFERED
                + cards.aliasesOffered(testRunId));
      }
      card = resolved.get();
    }
    if (outcome == PayerOutcome.ABANDONED) {
      return theDeparture(process, card, askedBy);
    }
    return switch (callbacks.theCallbackTargetOf(process)) {
      case GrowCallbacks.CallbackTarget.Unaddressable unaddressable ->
          new Result.Refused(NO_ONE_TO_TELL + unaddressable.inItsOwnWords() + STILL_COMPLETABLE);
      case GrowCallbacks.CallbackTarget.Addressed addressed ->
          theOutcomeAndItsCallback(process, outcome, card, askedBy, addressed.url());
    };
  }

  private Result theDeparture(
      GrowPaymentProcess process, @Nullable GrowPayerCard card, ObjectOrigin askedBy) {
    return switch (callbacks.reach(process, GrowProcessOutcome.Kind.ABANDONED, card, askedBy)) {
      case GrowCallbacks.Reached.AtADifferentOutcome _ -> new Result.Refused(ALREADY_COMPLETED);
      case GrowCallbacks.Reached.AtTheOutcomeAsked left ->
          new Result.Completed(left.process(), PayerOutcome.ABANDONED, List.of());
    };
  }

  private Result theOutcomeAndItsCallback(
      GrowPaymentProcess process,
      PayerOutcome outcome,
      @Nullable GrowPayerCard card,
      ObjectOrigin askedBy,
      String target) {
    return switch (callbacks.reach(process, kindOf(outcome), card, askedBy)) {
      case GrowCallbacks.Reached.AtADifferentOutcome _ -> new Result.Refused(ALREADY_COMPLETED);
      case GrowCallbacks.Reached.AtTheOutcomeAsked reached ->
          new Result.Completed(
              reached.process(), outcome, callbacks.deliver(reached.process(), target, null, null));
    };
  }

  private static String requireCard(@Nullable String cardAlias) {
    if (cardAlias == null) {
      throw new IllegalStateException(
          "a payer who paid names a card, and this completion reached the provider without one");
    }
    return cardAlias;
  }

  private static GrowProcessOutcome.Kind kindOf(PayerOutcome outcome) {
    return switch (outcome) {
      case PAID -> GrowProcessOutcome.Kind.COMPLETED;
      case DECLINED -> GrowProcessOutcome.Kind.FAILED;
      case ABANDONED -> GrowProcessOutcome.Kind.ABANDONED;
    };
  }
}
