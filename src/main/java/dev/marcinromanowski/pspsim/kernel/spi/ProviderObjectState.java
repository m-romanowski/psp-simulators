package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.DerivedBalances;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

public record ProviderObjectState(
    ProviderObjectIdentity identity,
    ProviderObjectKind kind,
    String nativeStatus,
    NormalizedStatus normalizedStatus,
    @Nullable Amount amount,
    @Nullable String currency,
    Ledger ledger,
    boolean authorizationOpen,
    boolean capturesAccepted,
    boolean refundsAccepted,
    Map<String, String> relatedObjects,
    ObjectOrigin origin,
    Instant updatedAt,
    HostedSessionFacts hostedSession) {

  static final String A_CAPTURE_WOULD_BE_TAKEN_ON_A_CLOSED_AUTHORIZATION =
      "capturesAccepted says a capture asked of this object now would be admitted, and"
          + " authorizationOpen says the object still holds an authorization nothing has closed,"
          + " so a state that accepts a capture on an authorization it reports as closed describes"
          + " a call that would take money out of a hold that is no longer there. A provider whose"
          + " capture path still admits the object reports the authorization open until it stops"
          + " admitting it";

  static final String A_REFUND_WOULD_RETURN_MONEY_THE_OBJECT_DOES_NOT_HAVE =
      "refundsAccepted says a refund asked of this object now would be admitted, and a refund"
          + " returns either money the object took and has not returned or money it is still"
          + " holding, so a state that accepts a refund while its operation list records neither"
          + " a readable positive captured-less-refunded remainder nor an authorization it reports"
          + " open describes a call that would return money the object does not have. A provider"
          + " whose refund path still admits the object records what that refund would return";

  public ProviderObjectState {
    relatedObjects = theObjectsItNames(relatedObjects);
    refuseAnOperationThatNamesTheObjectItIsOn(identity, ledger);
    refuseACaptureOnAnAuthorizationNothingHoldsOpen(authorizationOpen, capturesAccepted);
    refuseARefundOfMoneyTheObjectDoesNotHave(ledger, authorizationOpen, refundsAccepted);
  }

  public ProviderObjectState(
      ProviderObjectIdentity identity,
      ProviderObjectKind kind,
      String nativeStatus,
      NormalizedStatus normalizedStatus,
      @Nullable Amount amount,
      @Nullable String currency,
      Ledger ledger,
      boolean authorizationOpen,
      boolean capturesAccepted,
      boolean refundsAccepted,
      Map<String, String> relatedObjects,
      ObjectOrigin origin,
      Instant updatedAt) {
    this(
        identity,
        kind,
        nativeStatus,
        normalizedStatus,
        amount,
        currency,
        ledger,
        authorizationOpen,
        capturesAccepted,
        refundsAccepted,
        relatedObjects,
        origin,
        updatedAt,
        new HostedSessionFacts.NotAnObjectAPayerFinishes());
  }

  static final String A_NAMED_OBJECT_IS_A_NAME_AND_A_VALUE =
      "relatedObjects names one object of this provider per entry, and an entry whose name or"
          + " whose value is absent names nothing: it reaches a reader as the four letters null"
          + " where an identifier belongs. The copy is ordered, because a read, a screen and a"
          + " control answer all print these in the order the provider named them, and it refuses"
          + " a null half as the unordered copy it replaced did";

  static final String A_SECOND_NAMED_OBJECT_NEEDS_AN_ORDER_TO_PRINT_IT_IN =
      "relatedObjects names more than one object of this provider, and a read, a screen and a"
          + " control answer all print them in the order the provider named them, so the map that"
          + " carries them has to have an order. This one does not: Map.of, Map.copyOf, HashMap"
          + " and Collectors.toMap iterate in an order derived from the keys' hashes and a salt"
          + " drawn once per JVM, so a provider that builds its names in order and hands them over"
          + " in one of those prints them in one order on one run of this simulator and another on"
          + " the next, and a test written on the order it saw fails intermittently rather than"
          + " loudly. Hand over the LinkedHashMap the names were put into, rather than a copy that"
          + " drops the order";

  private static Map<String, String> theObjectsItNames(Map<String, String> named) {
    refuseAListOfNamedObjectsWithNoOrderToPrintThemIn(named);
    LinkedHashMap<String, String> ordered = new LinkedHashMap<>();
    named.forEach(
        (name, value) ->
            ordered.put(
                Objects.requireNonNull(name, A_NAMED_OBJECT_IS_A_NAME_AND_A_VALUE),
                Objects.requireNonNull(value, A_NAMED_OBJECT_IS_A_NAME_AND_A_VALUE)));
    return Collections.unmodifiableSequencedMap(ordered);
  }

  private static void refuseAListOfNamedObjectsWithNoOrderToPrintThemIn(Map<String, String> named) {
    if (named.size() > 1 && !(named instanceof SequencedMap)) {
      throw new IllegalArgumentException(A_SECOND_NAMED_OBJECT_NEEDS_AN_ORDER_TO_PRINT_IT_IN);
    }
  }

  public String providerObjectId() {
    return identity.canonical();
  }

  private static void refuseACaptureOnAnAuthorizationNothingHoldsOpen(
      boolean authorizationOpen, boolean capturesAccepted) {
    if (capturesAccepted && !authorizationOpen) {
      throw new IllegalArgumentException(A_CAPTURE_WOULD_BE_TAKEN_ON_A_CLOSED_AUTHORIZATION);
    }
  }

  private static void refuseARefundOfMoneyTheObjectDoesNotHave(
      Ledger ledger, boolean authorizationOpen, boolean refundsAccepted) {
    if (!refundsAccepted || authorizationOpen) {
      return;
    }
    if (ledger.refundableRemainder().filter(remainder -> remainder.signum() > 0).isEmpty()) {
      throw new IllegalArgumentException(A_REFUND_WOULD_RETURN_MONEY_THE_OBJECT_DOES_NOT_HAVE);
    }
  }

  private static void refuseAnOperationThatNamesTheObjectItIsOn(
      ProviderObjectIdentity identity, Ledger ledger) {
    for (MonetaryOperation operation : ledger.operations()) {
      String created = operation.createdObjectId();
      if (created != null && identity.answersTo(created)) {
        throw new IllegalArgumentException(
            "an operation's createdObjectId names a second object the operation brought into"
                + " existence, and operation "
                + operation.sequence()
                + " names an identifier the object whose ledger it is on already answers to,"
                + " which reports as a new object to look up something a reader is already"
                + " looking at");
      }
    }
  }

  public Optional<DerivedBalances> derivedBalances() {
    return ledger.balances(authorizationOpen);
  }
}
