package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class StripeClientSecrets {

  static final String NO_CLIENT_SECRET_WAS_SENT =
      "provider stripe requires the client_secret of the intent a publishable key reads or"
          + " confirms, and this request sent none, so it was refused before any intent was"
          + " addressed";

  private final StripePaymentIntents intents;
  private final StripeSetupIntents setupIntents;

  StripeClientSecrets(StripePaymentIntents intents, StripeSetupIntents setupIntents) {
    this.intents = intents;
    this.setupIntents = setupIntents;
  }

  @Nullable StripeOperationResult theRefusalOf(
      StripePublishableAccess access,
      StripeForm carrying,
      TestRunId testRunId,
      StripeAccount account) {
    return switch (access) {
      case StripePublishableAccess.NoPublishableKeyReachesThisRoute _ ->
          StripeOperationResult.refusedBeforeExecuting(
              StripeRefusals.thePublishableKeyIsRefused(), StripeErrors.secretKeyRequired());
      case StripePublishableAccess.ThePaymentIntentItsClientSecretOpens payment ->
          judged(
              carrying,
              StripeLookup.of(intents, testRunId, account, payment.intentId())
                  .map(StripePaymentIntent::clientSecret),
              StripeErrors.noSuchPaymentIntent(payment.intentId()),
              StripeErrors.clientSecretDoesNotMatchThePayment());
      case StripePublishableAccess.TheSetupIntentItsClientSecretOpens setup ->
          judged(
              carrying,
              StripeSetupLookup.of(setupIntents, testRunId, account, setup.setupIntentId())
                  .map(StripeSetupIntent::clientSecret),
              StripeErrors.noSuchSetupIntent(setup.setupIntentId()),
              StripeErrors.clientSecretDoesNotMatchTheSetup());
    };
  }

  private static @Nullable StripeOperationResult judged(
      StripeForm carrying,
      Optional<String> held,
      StripeError noSuchIntent,
      StripeError doesNotMatch) {
    String sent = carrying.first(StripeFields.CLIENT_SECRET);
    if (sent == null || sent.isBlank()) {
      return StripeOperationResult.refusedBeforeExecuting(
          RefusalSubject.noObjectWasNamed(NO_CLIENT_SECRET_WAS_SENT),
          StripeErrors.clientSecretMissing());
    }
    if (held.isEmpty()) {
      return StripeOperationResult.refusedBeforeExecuting(
          RefusalSubject.noObjectAnsweredTo(StripeLookup.INTENT_PARAMETER), noSuchIntent);
    }
    if (held.get().equals(sent)) {
      return null;
    }
    return StripeOperationResult.refusedBeforeExecuting(
        RefusalSubject.noObjectAnsweredTo(StripeFields.CLIENT_SECRET), doesNotMatch);
  }
}
