package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.spi.PairingSetting;
import dev.marcinromanowski.pspsim.kernel.spi.PairingValue;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPairing;
import java.util.List;

final class AutraPairing {

  static final String BASE_PATH = "/v1";

  static final String HOW_A_REQUEST_NAMES_ITS_ACCOUNT =
      "an Autra application exchanges its client credentials for a bearer token first —"
          + " POST /v1/oauth/token?grant_type=client_credentials carrying Authorization: Basic"
          + " base64(clientId:clientSecret) — and then sends Authorization: Bearer <accessToken>"
          + " on every /v1/acquiring route, each of which also carries documentId in its body;"
          + " provider autra declares clientSecret secret and clientId and documentId plain";

  static final ProviderPairing PAIRING =
      new ProviderPairing(
          AutraApi.AUTRA,
          BASE_PATH,
          HOW_A_REQUEST_NAMES_ITS_ACCOUNT,
          List.of(
              new PairingSetting("AUTRA_BASE_URL", new PairingValue.TheProviderBaseUrl()),
              new PairingSetting(
                  "AUTRA_TOKEN_PATH",
                  new PairingValue.Given("/v1/oauth/token?grant_type=client_credentials")),
              new PairingSetting(
                  "AUTRA_CLIENT_ID",
                  new PairingValue.FromAccountField(AutraMerchant.CLIENT_ID_PROPERTY)),
              new PairingSetting(
                  "AUTRA_CLIENT_SECRET",
                  new PairingValue.FromAccountField(AutraMerchant.CLIENT_SECRET_PROPERTY)),
              new PairingSetting(
                  "AUTRA_DOCUMENT_ID",
                  new PairingValue.FromAccountField(AutraMerchant.DOCUMENT_ID_PROPERTY)),
              new PairingSetting("SIMULATOR_ACCOUNT_ID", new PairingValue.TheAccountId()),
              new PairingSetting("SIMULATOR_TEST_RUN_HEADER", new PairingValue.TheTestRunHeader()),
              new PairingSetting("SIMULATOR_TEST_RUN_ID", new PairingValue.TheTestRunId())));

  private AutraPairing() {}
}
