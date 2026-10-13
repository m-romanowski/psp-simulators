package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.spi.WebhookNotSchedulableException;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import java.util.Optional;

final class UpdateMyUrl {

  private static final String NOTHING_ADDRESSED_THERE =
      "this simulator has addressed no grow callback to that url in this test run, and it will not"
          + " invent a notification body provider grow does not publish";

  private final WebhookDispatcher dispatcher;
  private final GrowScalarType scalarType;

  UpdateMyUrl(WebhookDispatcher dispatcher, GrowScalarType scalarType) {
    this.dispatcher = dispatcher;
    this.scalarType = scalarType;
  }

  GrowOperationResult handle(GrowForm form, TestRunId testRunId) {
    if (!form.has(GrowFields.URL)) {
      return GrowOperationResult.failed(
          GrowRefusals.theFormWasRefused(),
          GrowEnvelope.failure(GrowErrors.missingRequiredField(GrowFields.URL), scalarType));
    }

    WebhookDelivery resent =
        theNewestCallbackAddressedTo(testRunId, form.required(GrowFields.URL))
            .flatMap(
                addressed -> dispatcher.retry(GrowLightApi.GROW, testRunId, addressed.deliveryId()))
            .orElseThrow(() -> new WebhookNotSchedulableException(NOTHING_ADDRESSED_THERE));

    return GrowOperationResult.succeededAbout(
        resent.providerObjectId(), GrowEnvelope.success(GrowJson.text(""), scalarType));
  }

  private Optional<WebhookDelivery> theNewestCallbackAddressedTo(TestRunId testRunId, String url) {
    for (WebhookDelivery addressed : dispatcher.of(GrowLightApi.GROW, testRunId)) {
      if (url.equals(addressed.targetUrl())) {
        return Optional.of(addressed);
      }
    }
    return Optional.empty();
  }
}
