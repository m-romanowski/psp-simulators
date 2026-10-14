package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.spi.PairingSetting;
import dev.marcinromanowski.pspsim.kernel.spi.PairingValue;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPairing;
import java.util.List;

final class TranzilaPairing {

  static final String BASE_PATH = "/v1";

  static final String HOW_A_REQUEST_NAMES_ITS_ACCOUNT =
      "a Tranzila request carries four headers: X-tranzila-api-app-key names the account,"
          + " X-tranzila-api-request-time and X-tranzila-api-nonce are fresh on every call, and"
          + " X-tranzila-api-access-token is the lowercase hex of an HMAC-SHA256 of the app key"
          + " keyed with the app secret, the request time and the nonce; the body names the"
          + " terminal in terminal_name. Provider tranzila declares terminalName and appKey plain"
          + " and appSecret secret, so the key an application hashes with is named here and never"
          + " printed";

  static final ProviderPairing PAIRING =
      new ProviderPairing(
          TranzilaApi.TRANZILA,
          BASE_PATH,
          HOW_A_REQUEST_NAMES_ITS_ACCOUNT,
          List.of(
              new PairingSetting("TRANZILA_API_BASE_URL", new PairingValue.TheProviderBaseUrl()),
              new PairingSetting(
                  "TRANZILA_API_V2_BASE_URL",
                  new PairingValue.AnAddressOfThisSimulator(TranzilaApiController.VERSION_2)),
              new PairingSetting(
                  "TRANZILA_DIRECTNG_BASE_URL",
                  new PairingValue.AnAddressOfThisSimulator(TranzilaDirectNgPage.HOST)),
              new PairingSetting(
                  "TRANZILA_HF_BASE_URL",
                  new PairingValue.AnAddressOfThisSimulator(TranzilaHostedFieldsPage.HOST)),
              new PairingSetting(
                  "TRANZILA_TERMINAL_NAME",
                  new PairingValue.FromAccountField(TranzilaTerminal.TERMINAL_NAME_PROPERTY)),
              new PairingSetting(
                  "TRANZILA_APP_KEY",
                  new PairingValue.FromAccountField(TranzilaTerminal.APP_KEY_PROPERTY)),
              new PairingSetting(
                  "TRANZILA_APP_SECRET",
                  new PairingValue.FromAccountField(TranzilaTerminal.APP_SECRET_PROPERTY)),
              new PairingSetting("SIMULATOR_ACCOUNT_ID", new PairingValue.TheAccountId()),
              new PairingSetting("SIMULATOR_TEST_RUN_HEADER", new PairingValue.TheTestRunHeader()),
              new PairingSetting("SIMULATOR_TEST_RUN_ID", new PairingValue.TheTestRunId())));

  private TranzilaPairing() {}
}
