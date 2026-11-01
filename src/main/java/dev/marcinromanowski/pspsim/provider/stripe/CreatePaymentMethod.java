package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;

final class CreatePaymentMethod {

  private final StripePaymentMethodMinting minting;

  CreatePaymentMethod(StripePaymentMethods paymentMethods, IdGenerator ids) {
    this.minting = new StripePaymentMethodMinting(paymentMethods, ids);
  }

  StripeOperationResult handle(
      StripeCoreRequest request,
      TestRunId testRunId,
      StripeAccount account,
      Instant now,
      ObjectOrigin askedBy) {
    return switch (StripeNewPaymentMethod.ofTheRequest(request.form(), now)) {
      case StripeNewPaymentMethod.Outcome.Refused refused ->
          StripeOperationResult.refusedBeforeExecuting(
              StripeRefusals.theRequestWasRefused(), refused.error());
      case StripeNewPaymentMethod.Outcome.Described described -> {
        StripePaymentMethod minted =
            minting.described(testRunId, account, described.description(), askedBy, now);
        minting.stored(minted);
        yield StripeOperationResult.succeeded(
            minted.paymentMethodId(), StripePaymentMethodView.of(minted));
      }
    };
  }
}
