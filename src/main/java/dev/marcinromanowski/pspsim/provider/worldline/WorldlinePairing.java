package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.spi.PairingSetting;
import dev.marcinromanowski.pspsim.kernel.spi.PairingValue;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPairing;
import java.util.List;

final class WorldlinePairing {

  static final String BASE_PATH = "/v2";

  static final String HOW_A_REQUEST_NAMES_ITS_ACCOUNT =
      "a Worldline request carries Authorization: GCS v1HMAC:<apiKey>:<signature>, where the"
          + " signature is the Base64 of an HMAC-SHA256, keyed with apiSecret, over the method,"
          + " the content type, the Date header, the sorted x-gcs- headers and the signed path,"
          + " one per line; the merchantId is also the first path segment after /v2. Provider"
          + " worldline declares merchantId and apiKey plain and apiSecret secret, so the key an"
          + " application hashes with is named here and never printed";

  static final ProviderPairing PAIRING =
      new ProviderPairing(
          WorldlineApi.WORLDLINE,
          BASE_PATH,
          HOW_A_REQUEST_NAMES_ITS_ACCOUNT,
          List.of(
              new PairingSetting("WORLDLINE_BASE_URL", new PairingValue.TheProviderBaseUrl()),
              new PairingSetting(
                  "WORLDLINE_MERCHANT_ID",
                  new PairingValue.FromAccountField(WorldlineMerchant.MERCHANT_ID_PROPERTY)),
              new PairingSetting(
                  "WORLDLINE_API_KEY",
                  new PairingValue.FromAccountField(WorldlineMerchant.API_KEY_PROPERTY)),
              new PairingSetting(
                  "WORLDLINE_API_SECRET",
                  new PairingValue.FromAccountField(WorldlineMerchant.API_SECRET_PROPERTY)),
              new PairingSetting(
                  "WORLDLINE_AUTHORIZATION_SCHEME", new PairingValue.Given("GCS v1HMAC")),
              new PairingSetting("SIMULATOR_ACCOUNT_ID", new PairingValue.TheAccountId()),
              new PairingSetting("SIMULATOR_TEST_RUN_HEADER", new PairingValue.TheTestRunHeader()),
              new PairingSetting("SIMULATOR_TEST_RUN_ID", new PairingValue.TheTestRunId())));

  private WorldlinePairing() {}
}
