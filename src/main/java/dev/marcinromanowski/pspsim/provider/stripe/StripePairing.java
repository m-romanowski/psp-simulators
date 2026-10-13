package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.spi.PairingSetting;
import dev.marcinromanowski.pspsim.kernel.spi.PairingValue;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPairing;
import java.util.List;

final class StripePairing {

  static final String BASE_PATH = "/v1";

  static final String HOW_A_REQUEST_NAMES_ITS_ACCOUNT =
      "a Stripe request carries Authorization: Bearer <secretKey>, or the same key as the HTTP"
          + " Basic username with an empty password, and the test-mode secret key is the only"
          + " thing that names the account: this contract publishes no merchant id, terminal id"
          + " or key pair. Provider stripe declares that key secret, so the one identifier an"
          + " application needs is the one thing this screen will not print";

  static final ProviderPairing PAIRING =
      new ProviderPairing(
          StripeApi.STRIPE,
          BASE_PATH,
          HOW_A_REQUEST_NAMES_ITS_ACCOUNT,
          List.of(
              new PairingSetting("STRIPE_API_BASE", new PairingValue.TheProviderBaseUrl()),
              new PairingSetting(
                  "STRIPE_SECRET_KEY",
                  new PairingValue.FromAccountField(StripeAccount.SECRET_KEY_PROPERTY)),
              new PairingSetting(
                  "STRIPE_WEBHOOK_SECRET",
                  new PairingValue.FromAccountField(StripeWebhookSigning.SECRET_SETTING)),
              new PairingSetting(
                  "STRIPE_CONTENT_TYPE", new PairingValue.Given(StripeWireRequest.FORM_URLENCODED)),
              new PairingSetting("SIMULATOR_ACCOUNT_ID", new PairingValue.TheAccountId()),
              new PairingSetting("SIMULATOR_TEST_RUN_HEADER", new PairingValue.TheTestRunHeader()),
              new PairingSetting("SIMULATOR_TEST_RUN_ID", new PairingValue.TheTestRunId())));

  private StripePairing() {}
}
