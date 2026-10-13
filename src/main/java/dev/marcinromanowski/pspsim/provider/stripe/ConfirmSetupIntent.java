package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
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
  private final StripePaymentMethods paymentMethods;
  private final StripeCustomers customers;
  private final ScenarioEngine scenarios;

  ConfirmSetupIntent(
      StripeSetupIntents intents,
      StripePaymentMethods paymentMethods,
      StripeCustomers customers,
      ScenarioEngine scenarios) {
    this.intents = intents;
    this.paymentMethods = paymentMethods;
    this.customers = customers;
    this.scenarios = scenarios;
  }

  StripeOperationResult handle(
      StripeForm form,
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
    StripeSetupIntent intent = addressed.get();
    if (!intent.status().confirmable()) {
      return StripeSetupLookup.unexpectedState(intent, "confirmed");
    }
    String paymentMethod = paymentMethodOf(form, intent);
    if (paymentMethod == null) {
      return StripeOperationResult.refused(
          RefusalSubject.theObject(intent.setupIntentId()),
          StripeErrors.parameterMissing(StripeFields.PAYMENT_METHOD));
    }
    StripeError refusal =
        whatTheAttachmentThisSetupWouldMintEarns(testRunId, account, intent, paymentMethod);
    if (refusal != null) {
      return StripeOperationResult.refused(
          RefusalSubject.theObject(intent.setupIntentId()), refusal);
    }
    return StripeOperationResult.decidedBy(
        scenarios.decide(requestOf(headers, testRunId, account, intent, paymentMethod)),
        () -> setUp(intent, paymentMethod, now),
        decline -> fail(intent, paymentMethod, decline, now));
  }

  private @Nullable StripeError whatTheAttachmentThisSetupWouldMintEarns(
      TestRunId testRunId, StripeAccount account, StripeSetupIntent intent, String paymentMethod) {
    Optional<StripePaymentMethod> named =
        StripePaymentMethodLookup.of(paymentMethods, testRunId, account, paymentMethod);
    if (named.isEmpty()) {
      return StripeErrors.resourceMissing(StripeFields.PAYMENT_METHOD);
    }
    StripePaymentMethod method = named.get();
    if (method.detached()) {
      return StripeErrors.paymentMethodRefused(
          StripePaymentMethodLookup.A_DETACHED_METHOD_IS_REFUSED_RATHER_THAN_IGNORED);
    }
    String customerId = intent.customerId();
    if (customerId == null) {
      return null;
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

  private static @Nullable String paymentMethodOf(StripeForm form, StripeSetupIntent intent) {
    String sent = form.first(StripeFields.PAYMENT_METHOD);
    return sent == null ? intent.paymentMethodId() : sent;
  }

  private static ScenarioRequest requestOf(
      Map<String, List<String>> headers,
      TestRunId testRunId,
      StripeAccount account,
      StripeSetupIntent intent,
      String paymentMethod) {
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

  private StripeOperationResult setUp(StripeSetupIntent intent, String paymentMethod, Instant now) {
    return answeredFor(
        intents.succeeded(intent, paymentMethod, intent.customerId(), now),
        intent,
        () ->
            StripeOperationResult.succeeded(
                intent.setupIntentId(),
                StripeSetupIntentView.of(intent.succeededWith(paymentMethod, now))));
  }

  private StripeOperationResult fail(
      StripeSetupIntent intent, String paymentMethod, StripeDecline decline, Instant now) {
    return answeredFor(
        intents.failed(intent, decline, paymentMethod, now),
        intent,
        () ->
            StripeOperationResult.refusedAfterChangingState(
                RefusalSubject.theObject(intent.setupIntentId()),
                StripeErrors.declined(decline)
                    .aboutTheSetup(
                        StripeSetupIntentView.theTruncatedFormAnErrorCarries(
                            intent.failedWith(decline, paymentMethod, now)))));
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
