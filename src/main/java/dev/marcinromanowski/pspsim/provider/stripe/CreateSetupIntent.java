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

  private final StripeSetupIntents intents;
  private final IdGenerator ids;
  private final StripePaymentMethodMinting minting;

  CreateSetupIntent(
      StripeSetupIntents intents, StripePaymentMethods paymentMethods, IdGenerator ids) {
    this.intents = intents;
    this.ids = ids;
    this.minting = new StripePaymentMethodMinting(paymentMethods, ids);
  }

  StripeOperationResult handle(
      StripeCoreRequest request,
      TestRunId testRunId,
      StripeAccount account,
      Instant now,
      ObjectOrigin askedBy) {
    StripeForm form = request.form();
    StripeError unreadable = whatThisRequestDoesNotCarry(form);
    if (unreadable != null) {
      return refused(unreadable);
    }
    StripeSelection selection;
    switch (StripeSelections.onCreatingASetup(request)) {
      case StripeSelections.Outcome.Refused refused -> {
        return refused(refused.error());
      }
      case StripeSelections.Outcome.Selected selected -> selection = selected.selection();
    }
    StripeSetupUsage usage = requireUsage(form);
    StripeNamedPaymentMethod named;
    switch (StripeNamedPaymentMethod.of(form, null, minting, testRunId, account, askedBy, now)) {
      case StripeNamedPaymentMethod.Outcome.Refused refused -> {
        return refused(refused.error());
      }
      case StripeNamedPaymentMethod.Outcome.Named theOne -> named = theOne.method();
    }
    String paymentMethod = named.id();
    named.storedBy(minting);
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
              selection,
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

  private static StripeOperationResult refused(StripeError error) {
    return StripeOperationResult.refusedBeforeExecuting(
        StripeRefusals.theRequestWasRefused(), error);
  }

  private static StripeSetupStatus statusOf(@Nullable String paymentMethod) {
    return paymentMethod == null
        ? StripeSetupStatus.REQUIRES_PAYMENT_METHOD
        : StripeSetupStatus.REQUIRES_CONFIRMATION;
  }

  private static @Nullable StripeError whatThisRequestDoesNotCarry(StripeForm form) {
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
