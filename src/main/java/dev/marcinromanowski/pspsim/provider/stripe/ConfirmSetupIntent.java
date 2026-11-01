package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

final class ConfirmSetupIntent {

  private final StripeSetupIntents intents;
  private final StripeCustomers customers;
  private final ScenarioEngine scenarios;
  private final StripePaymentMethodMinting minting;

  ConfirmSetupIntent(
      StripeSetupIntents intents,
      StripePaymentMethods paymentMethods,
      StripeCustomers customers,
      IdGenerator ids,
      ScenarioEngine scenarios) {
    this.intents = intents;
    this.customers = customers;
    this.scenarios = scenarios;
    this.minting = new StripePaymentMethodMinting(paymentMethods, ids);
  }

  StripeSetupIntents store() {
    return intents;
  }

  StripeOperationResult handle(
      StripeCoreRequest request,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      StripeAccount account,
      String setupIntentId,
      Instant now) {
    Optional<StripeSetupIntent> addressed =
        StripeSetupLookup.of(intents, testRunId, account, setupIntentId);
    if (addressed.isEmpty()) {
      return StripeOperationResult.refusedBeforeExecuting(
          RefusalSubject.noObjectAnsweredTo(StripeSetupLookup.SETUP_INTENT_PARAMETER),
          StripeErrors.resourceMissing(StripeSetupLookup.SETUP_INTENT_PARAMETER));
    }
    StripeSetupIntent stored = addressed.get();
    StripeNamedPaymentMethod named;
    switch (StripeNamedPaymentMethod.of(
        request.form(),
        stored.lastSetupError() == null ? stored.paymentMethodId() : null,
        minting,
        testRunId,
        account,
        ObjectOrigin.PROVIDER_API,
        now)) {
      case StripeNamedPaymentMethod.Outcome.Refused refused -> {
        return refusedOnItsParametersAbout(stored, refused.error());
      }
      case StripeNamedPaymentMethod.Outcome.Named theOne -> named = theOne.method();
    }
    if (!stored.status().confirmable()) {
      return StripeSetupLookup.unexpectedState(stored, "confirmed");
    }
    StripeSelection selection;
    switch (StripeSelections.onConfirmingASetup(request, stored)) {
      case StripeSelections.Outcome.Refused refused -> {
        return refusedOnItsParametersAbout(stored, refused.error());
      }
      case StripeSelections.Outcome.Selected selected -> selection = selected.selection();
    }
    StripeError nowhereToReturn =
        StripeReturnUrl.theRefusalASetupConfirmationEarns(request.form(), selection);
    if (nowhereToReturn != null) {
      return refusedOnItsParametersAbout(stored, nowhereToReturn);
    }
    StripeSetupIntent intent = stored.selecting(selection);
    String paymentMethod = named.id();
    if (paymentMethod == null) {
      return StripeOperationResult.refusedBeforeExecuting(
          RefusalSubject.theObject(intent.setupIntentId()),
          StripeErrors.parameterMissing(StripeFields.PAYMENT_METHOD));
    }
    StripePaymentMethod method = named.method();
    if (method == null) {
      return refusedAbout(stored, StripeErrors.noSuchPaymentMethod(paymentMethod));
    }
    StripeError refusal =
        whatTheAttachmentThisSetupWouldMintEarns(testRunId, account, intent, method);
    if (refusal != null) {
      return StripeOperationResult.refused(
          RefusalSubject.theObject(intent.setupIntentId()), refusal);
    }
    Optional<StripePaymentMethodType> notOffered =
        named.type().filter(type -> !selection.offers(type));
    if (notOffered.isPresent()) {
      return refusedAbout(stored, notAllowed(notOffered.get(), stored.selection(), selection));
    }
    return StripeCardOutcomes.decidedBy(
        scenarios.decide(requestOf(headers, testRunId, account, intent, named.theIdARuleNames())),
        named.method(),
        StripeOperations.CONFIRM_SETUP_INTENT,
        () -> setUp(intent, paymentMethod, named.minted(), now),
        decline -> fail(intent, paymentMethod, named.minted(), decline, now));
  }

  private static StripeError notAllowed(
      StripePaymentMethodType type, StripeSelection kept, StripeSelection selection) {
    return kept.equals(selection)
        ? StripeErrors.paymentMethodNotAllowedForTheSetup(
            type.wireValue(), selection.offeredAsProse())
        : StripeErrors.paymentMethodNotAllowedByTheSelectionSentForTheSetup(
            type.wireValue(), selection.offeredAsProse());
  }

  private static StripeOperationResult refusedOnItsParametersAbout(
      StripeSetupIntent stored, StripeError error) {
    return StripeOperationResult.refusedBeforeExecuting(
        RefusalSubject.theObject(stored.setupIntentId()),
        error.aboutTheSetup(StripeSetupIntentView.theTruncatedFormAnErrorCarries(stored)));
  }

  private static StripeOperationResult refusedAbout(StripeSetupIntent stored, StripeError error) {
    return StripeOperationResult.refused(
        RefusalSubject.theObject(stored.setupIntentId()),
        error.aboutTheSetup(StripeSetupIntentView.theTruncatedFormAnErrorCarries(stored)));
  }

  private @Nullable StripeError whatTheAttachmentThisSetupWouldMintEarns(
      TestRunId testRunId,
      StripeAccount account,
      StripeSetupIntent intent,
      StripePaymentMethod method) {
    if (method.detached()) {
      return StripeErrors.paymentMethodRefused(
          StripePaymentMethodLookup.A_DETACHED_METHOD_IS_REFUSED_RATHER_THAN_IGNORED);
    }
    String customerId = intent.customerId();
    if (customerId == null) {
      return method.attached()
          ? StripeErrors.paymentMethodRefused(
              StripeSetupLookup.A_METHOD_OF_A_CUSTOMER_SETS_UP_NO_INTENT_WITHOUT_ONE)
          : null;
    }
    Optional<StripeCustomer> owner =
        StripeCustomerLookup.of(customers, testRunId, account, customerId);
    if (owner.isEmpty()) {
      return StripeErrors.resourceMissing(StripeFields.CUSTOMER);
    }
    if (owner.get().deleted()) {
      return StripeErrors.furtherOperationRefused(
          StripeCustomerObject.A_DELETED_CUSTOMER_TAKES_NO_FURTHER_OPERATION);
    }
    if (method.attached() && !customerId.equals(method.customerId())) {
      return StripeErrors.paymentMethodRefused(
          StripeSetupLookup.A_METHOD_ANOTHER_CUSTOMER_HOLDS_IS_REFUSED);
    }
    return null;
  }

  private static ScenarioRequest requestOf(
      Map<String, List<String>> headers,
      TestRunId testRunId,
      StripeAccount account,
      StripeSetupIntent intent,
      @Nullable String paymentMethod) {
    return new ScenarioRequest(
        StripeApi.STRIPE,
        testRunId,
        StripeStoryKey.ofSetupIntent(intent.setupIntentId()),
        account.accountId(),
        null,
        intent.setupIntentId(),
        intent.customerId(),
        paymentMethod,
        null,
        null,
        StripeOperations.CONFIRM_SETUP_INTENT,
        headers);
  }

  private StripeOperationResult setUp(
      StripeSetupIntent intent,
      String paymentMethod,
      @Nullable StripePaymentMethod minted,
      Instant now) {
    return answeredFor(
        intents.succeeded(intent, paymentMethod, intent.customerId(), minted, now),
        intent,
        () ->
            StripeOperationResult.succeeded(
                intent.setupIntentId(),
                StripeSetupIntentView.of(intent.succeededWith(paymentMethod, now))));
  }

  private StripeOperationResult fail(
      StripeSetupIntent intent,
      String paymentMethod,
      @Nullable StripePaymentMethod minted,
      StripeDecline decline,
      Instant now) {
    return answeredFor(
        intents.failed(intent, decline, paymentMethod, minted, now),
        intent,
        () ->
            StripeOperationResult.refusedAfterChangingState(
                RefusalSubject.theObject(intent.setupIntentId()),
                StripeErrors.declined(decline)
                    .aboutTheSetup(
                        StripeSetupIntentView.of(intent.failedWith(decline, paymentMethod, now)))));
  }

  private static StripeOperationResult answeredFor(
      StripeSetupIntents.Setup applied,
      StripeSetupIntent intent,
      Supplier<StripeOperationResult> whenApplied) {
    return switch (applied) {
      case APPLIED -> whenApplied.get();
      case THE_METHOD_IS_NO_LONGER_ATTACHABLE ->
          StripeOperationResult.refused(
              RefusalSubject.theObject(intent.setupIntentId()),
              StripeErrors.paymentMethodRefused(
                  StripeSetupLookup.THE_METHOD_IS_NO_LONGER_ATTACHABLE));
      case THE_SETUP_MOVED_ON ->
          StripeSetupLookup.unexpectedState(intent, "changed by two requests at once");
      case OBJECT_GONE ->
          StripeOperationResult.refused(
              RefusalSubject.noObjectAnsweredTo(StripeSetupLookup.SETUP_INTENT_PARAMETER),
              StripeErrors.resourceMissing(StripeSetupLookup.SETUP_INTENT_PARAMETER));
    };
  }
}
