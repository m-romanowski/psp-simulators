package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

sealed interface StripeNamedPaymentMethod {

  record NoneNamed() implements StripeNamedPaymentMethod {}

  record Held(StripePaymentMethod method) implements StripeNamedPaymentMethod {}

  record Minted(StripePaymentMethod method) implements StripeNamedPaymentMethod {}

  record Unknown(String paymentMethodId) implements StripeNamedPaymentMethod {}

  sealed interface Outcome {

    record Named(StripeNamedPaymentMethod method) implements Outcome {}

    record Refused(StripeError error) implements Outcome {}
  }

  default @Nullable StripePaymentMethod method() {
    return null;
  }

  default @Nullable String id() {
    return switch (this) {
      case NoneNamed _ -> null;
      case Held held -> held.method().paymentMethodId();
      case Minted minted -> minted.method().paymentMethodId();
      case Unknown unknown -> unknown.paymentMethodId();
    };
  }

  default @Nullable String theIdARuleNames() {
    StripePaymentMethod method = method();
    return method == null ? id() : method.theIdARuleNames();
  }

  default @Nullable StripePaymentMethod minted() {
    return this instanceof Minted minted ? minted.method() : null;
  }

  default Optional<StripePaymentMethodType> type() {
    return switch (this) {
      case Held held -> Optional.of(held.method().type());
      case Minted minted -> Optional.of(minted.method().type());
      case NoneNamed _, Unknown _ -> Optional.empty();
    };
  }

  default boolean detached() {
    return this instanceof Held held && held.method().detached();
  }

  default @Nullable StripeError theRefusalOfAnIdThatReadsAsACardNumber() {
    if (this instanceof Unknown unknown
        && StripeFields.readsAsACardNumber(unknown.paymentMethodId())) {
      return StripeErrors.noSuchPaymentMethod(unknown.paymentMethodId());
    }
    return null;
  }

  static @Nullable StripeError theRefusalOfACustomerThatReadsAsACardNumber(StripeForm form) {
    String customer = form.first(StripeFields.CUSTOMER);
    return customer != null && StripeFields.readsAsACardNumber(customer)
        ? StripeErrors.noSuchCustomer(customer)
        : null;
  }

  default @Nullable StripeError theRefusalOfAMethodNoOneHolds() {
    if (this instanceof Unknown unknown
        && !StripePaymentMethodLookup.aPublishedTestMethod(unknown.paymentMethodId())) {
      return StripeErrors.noSuchPaymentMethod(unknown.paymentMethodId());
    }
    return null;
  }

  default @Nullable StripeError theRefusalItsHolderEarnsOnAPayment(
      @Nullable String customerOfTheIntent) {
    if (!(this instanceof Held held)) {
      return null;
    }
    StripePaymentMethod method = held.method();
    if (method.detached()) {
      return StripeErrors.paymentMethodRefused(
          StripePaymentMethodLookup.A_DETACHED_METHOD_IS_REFUSED_RATHER_THAN_IGNORED);
    }
    if (!method.attached() || Objects.equals(method.customerId(), customerOfTheIntent)) {
      return null;
    }
    return StripeErrors.paymentMethodRefused(
        customerOfTheIntent == null
            ? StripePaymentMethodLookup.A_METHOD_OF_A_CUSTOMER_PAYS_FOR_NO_INTENT_WITHOUT_ONE
            : StripePaymentMethodLookup.A_METHOD_OF_ANOTHER_CUSTOMER_PAYS_FOR_NO_INTENT);
  }

  default void storedBy(StripePaymentMethodMinting minting) {
    if (this instanceof Minted minted) {
      minting.stored(minted.method());
    }
  }

  static Outcome of(
      StripeForm form,
      @Nullable String alreadyNamed,
      StripePaymentMethodMinting minting,
      TestRunId testRunId,
      StripeAccount account,
      ObjectOrigin askedBy,
      Instant now) {
    StripeParameter data = StripeNewPaymentMethod.thePaymentMethodDataIn(form);
    if (data != null) {
      if (form.has(StripeFields.PAYMENT_METHOD)) {
        return new Outcome.Refused(
            StripeErrors.parametersExclusive(
                StripeFields.PAYMENT_METHOD, StripeNewPaymentMethod.PAYMENT_METHOD_DATA));
      }
      return switch (StripeNewPaymentMethod.ofThePaymentMethodData(data, now)) {
        case StripeNewPaymentMethod.Outcome.Refused refused -> new Outcome.Refused(refused.error());
        case StripeNewPaymentMethod.Outcome.Described described ->
            new Outcome.Named(
                new Minted(
                    minting.described(testRunId, account, described.description(), askedBy, now)));
      };
    }
    String sent = form.first(StripeFields.PAYMENT_METHOD);
    return new Outcome.Named(
        theOneNamed(sent == null ? alreadyNamed : sent, minting, testRunId, account, askedBy, now));
  }

  private static StripeNamedPaymentMethod theOneNamed(
      @Nullable String paymentMethodId,
      StripePaymentMethodMinting minting,
      TestRunId testRunId,
      StripeAccount account,
      ObjectOrigin askedBy,
      Instant now) {
    if (paymentMethodId == null) {
      return new NoneNamed();
    }
    Optional<StripePaymentMethod> held =
        StripePaymentMethodLookup.of(minting.store(), testRunId, account, paymentMethodId);
    if (held.isPresent()) {
      return new Held(held.get());
    }
    return StripeTestCards.byPaymentMethodId(paymentMethodId)
        .<StripeNamedPaymentMethod>map(
            published ->
                new Minted(
                    minting.clonedFrom(
                        paymentMethodId, published, testRunId, account, askedBy, now)))
        .orElseGet(() -> new Unknown(paymentMethodId));
  }
}
