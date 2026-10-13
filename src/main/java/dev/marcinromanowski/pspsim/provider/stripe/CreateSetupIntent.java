package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

final class CreateSetupIntent {

  static final int ID_ATTEMPTS = 5;

  static final String ID_PREFIX = "seti_sim_";

  static final int ID_LENGTH = 16;

  static final String THE_SELECTION_PARAMETERS_IT_ACCEPTS =
      StripeFields.AUTOMATIC_PAYMENT_METHODS
          + ", "
          + StripeFields.ALLOWED_PAYMENT_METHOD_TYPES
          + ", "
          + StripeFields.EXCLUDED_PAYMENT_METHOD_TYPES
          + " and "
          + StripeFields.PAYMENT_METHOD_CONFIGURATION
          + " as the payment-method selection parameters provider stripe publishes for this"
          + " endpoint. "
          + StripeFields.PAYMENT_METHOD_TYPES
          + " is published on the SetupIntent object and on no parameter list of this endpoint, so"
          + " this simulator answers it rather than reading it";

  private final StripeSetupIntents intents;
  private final IdGenerator ids;

  CreateSetupIntent(StripeSetupIntents intents, IdGenerator ids) {
    this.intents = intents;
    this.ids = ids;
  }

  StripeOperationResult handle(
      StripeForm form,
      TestRunId testRunId,
      StripeAccount account,
      Instant now,
      ObjectOrigin askedBy) {
    StripeError unreadable = whatThisRequestDoesNotCarry(form);
    if (unreadable != null) {
      return StripeOperationResult.refusedBeforeExecuting(
          StripeRefusals.theRequestWasRefused(), unreadable);
    }
    StripeSetupUsage usage = requireUsage(form);
    String paymentMethod = form.first(StripeFields.PAYMENT_METHOD);
    for (int attempt = 0; attempt < ID_ATTEMPTS; attempt++) {
      String setupIntentId = ID_PREFIX + ids.hex(ID_LENGTH);
      StripeSetupIntent intent =
          new StripeSetupIntent(
              testRunId,
              setupIntentId,
              account.accountId(),
              form.first(StripeFields.CUSTOMER),
              paymentMethod,
              usage,
              statusOf(paymentMethod),
              StripeClientSecret.of(setupIntentId, ids),
              null,
              askedBy,
              now,
              now);
      if (intents.insertIfAbsent(intent)) {
        return StripeOperationResult.succeeded(setupIntentId, StripeSetupIntentView.of(intent));
      }
    }
    throw new IllegalStateException(
        "no Stripe SetupIntent id could be claimed in " + ID_ATTEMPTS + " attempts");
  }

  private static StripeSetupStatus statusOf(@Nullable String paymentMethod) {
    return paymentMethod == null
        ? StripeSetupStatus.REQUIRES_PAYMENT_METHOD
        : StripeSetupStatus.REQUIRES_CONFIRMATION;
  }

  private static @Nullable StripeError whatThisRequestDoesNotCarry(StripeForm form) {
    if (form.carriesAnyFormOf(StripeFields.PAYMENT_METHOD_TYPES)) {
      return StripeErrors.parameterUnknown(
          StripeFields.PAYMENT_METHOD_TYPES, THE_SELECTION_PARAMETERS_IT_ACCEPTS);
    }
    if (usageOf(form) == null) {
      return StripeErrors.unreadableParameter(
          StripeFields.USAGE, StripeSetupUsage.theUsagesTheVendorPublishes());
    }
    return null;
  }

  private static @Nullable StripeSetupUsage usageOf(StripeForm form) {
    String declared = form.first(StripeFields.USAGE);
    if (declared == null) {
      return StripeSetupUsage.PUBLISHED_DEFAULT;
    }
    return StripeSetupUsage.of(declared).orElse(null);
  }

  private static StripeSetupUsage requireUsage(StripeForm form) {
    StripeSetupUsage usage = usageOf(form);
    if (usage == null) {
      throw new IllegalStateException("read usage before checking it was readable");
    }
    return usage;
  }
}
