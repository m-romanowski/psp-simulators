package dev.marcinromanowski.pspsim.kernel.scenario;

import dev.marcinromanowski.pspsim.kernel.fault.FaultedDelivery;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFaultKind;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

public sealed interface ScenarioAction {

  @Nullable String theProviderCodeItComesToRestWith();

  sealed interface OnTheRequest extends ScenarioAction {}

  sealed interface OnTheDelivery extends ScenarioAction {}

  record FixedSuccess() implements OnTheRequest {

    @Override
    public @Nullable String theProviderCodeItComesToRestWith() {
      return null;
    }
  }

  record TerminalFailure(String providerCode) implements OnTheRequest {

    public TerminalFailure {
      if (providerCode.isBlank()) {
        throw new IllegalArgumentException(
            "action.providerCode must name the failure code the provider answers with");
      }
    }

    @Override
    public String theProviderCodeItComesToRestWith() {
      return providerCode;
    }
  }

  record TransportFaultAlways(TransportFault fault) implements OnTheRequest {

    @Override
    public @Nullable String theProviderCodeItComesToRestWith() {
      return null;
    }
  }

  record TransportFaultInsteadOfOperation(TransportFault fault) implements OnTheRequest {

    public TransportFaultInsteadOfOperation {
      if (fault.answeredWithoutTheOperation()
          instanceof TransportFault.WithoutTheOperation.NeedsTheRenderedBytes needing) {
        throw new IllegalArgumentException(
            "action.fault "
                + fault.kind()
                + " "
                + needing.whatItDoesWithThem()
                + ", and an action applied instead of the operation renders none; only "
                + TransportFaultKind.SERVER_ERROR
                + " answers without them");
      }
    }

    public FaultedDelivery answer() {
      return ((TransportFault.WithoutTheOperation.Answered) fault.answeredWithoutTheOperation())
          .delivery();
    }

    @Override
    public @Nullable String theProviderCodeItComesToRestWith() {
      return null;
    }
  }

  record ProcessingThenTerminal(Duration settlesAfter, @Nullable String providerCode)
      implements OnTheRequest {

    public static final Duration LONGEST_WAIT = Duration.ofHours(1);

    public ProcessingThenTerminal {
      if (settlesAfter.isZero() || settlesAfter.isNegative()) {
        throw new IllegalArgumentException(
            "action.settlesAfterMs must hold the operation in its processing state for a positive"
                + " number of milliseconds, or the answer this rule reports as pending has already"
                + " come to rest by the time the caller reads it and nothing was ever in flight");
      }
      if (settlesAfter.compareTo(LONGEST_WAIT) > 0) {
        throw new IllegalArgumentException(
            "action.settlesAfterMs must not hold an outcome back longer than "
                + LONGEST_WAIT
                + ", because an outcome scheduled past the test run that asked for it is an"
                + " outcome that never lands, and a payment left in flight for good is a payment"
                + " no assertion can close");
      }
      if (providerCode != null && providerCode.isBlank()) {
        throw new IllegalArgumentException(
            "action.providerCode must name the failure code the scheduled outcome comes to rest"
                + " with, or be absent, in which case the outcome comes to rest as a success");
      }
    }

    @Override
    public @Nullable String theProviderCodeItComesToRestWith() {
      return providerCode;
    }
  }

  record DeliveryDelayed(Duration held) implements OnTheDelivery {

    public static final Duration LONGEST_HOLD = Duration.ofHours(1);

    public DeliveryDelayed {
      if (held.isZero() || held.isNegative()) {
        throw new IllegalArgumentException(
            "action.delayMs must hold a delivery back by a positive number of milliseconds, or the"
                + " delivery this rule shapes leaves when it would have left anyway");
      }
      if (held.compareTo(LONGEST_HOLD) > 0) {
        throw new IllegalArgumentException(
            "action.delayMs must not hold a delivery back longer than "
                + LONGEST_HOLD
                + ", because a delivery held past the test run that asked for it is a delivery"
                + " dropped, which is what "
                + ScenarioGrammar.DELIVERY_DROPPED
                + " says out loud");
      }
    }

    @Override
    public @Nullable String theProviderCodeItComesToRestWith() {
      return null;
    }
  }

  record DeliveryDuplicated() implements OnTheDelivery {

    @Override
    public @Nullable String theProviderCodeItComesToRestWith() {
      return null;
    }
  }

  record DeliveryDropped() implements OnTheDelivery {

    @Override
    public @Nullable String theProviderCodeItComesToRestWith() {
      return null;
    }
  }
}
