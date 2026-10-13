package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.spi.PairingSetting;
import dev.marcinromanowski.pspsim.kernel.spi.PairingValue;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPairing;
import java.util.List;

final class AzericardPairing {

  static final String BASE_PATH = "/cgi-bin/cgi_link";

  static final String HOW_A_REQUEST_NAMES_ITS_ACCOUNT =
      "an Azericard request carries no Authorization header: TERMINAL names the account in the"
          + " form-encoded body and P_SIGN proves it, an uppercase-hex SHA256withRSA signature"
          + " over the length-prefixed concatenation of that operation's fields. The gateway"
          + " verifies it with the merchant public key configured as apiKey, so the private half"
          + " an application signs with is held by the application and is not an account field of"
          + " this simulator at all; apiSecret is the gateway private key this simulator signs its"
          + " own callbacks with, and provider azericard declares it secret";

  static final ProviderPairing PAIRING =
      new ProviderPairing(
          AzericardGateway.AZERICARD,
          BASE_PATH,
          HOW_A_REQUEST_NAMES_ITS_ACCOUNT,
          List.of(
              new PairingSetting("AZERICARD_GATEWAY_URL", new PairingValue.TheProviderBaseUrl()),
              new PairingSetting(
                  "AZERICARD_TOKEN_GATEWAY_PATH", new PairingValue.Given("/token/cgi_link")),
              new PairingSetting(
                  "AZERICARD_CONTENT_TYPE",
                  new PairingValue.Given("application/x-www-form-urlencoded")),
              new PairingSetting(
                  "AZERICARD_TERMINAL",
                  new PairingValue.FromAccountField(AzericardTerminal.TERMINAL_PROPERTY)),
              new PairingSetting(
                  "AZERICARD_MERCHANT_PUBLIC_KEY_PEM",
                  new PairingValue.FromAccountField(AzericardTerminal.MERCHANT_KEY_PROPERTY)),
              new PairingSetting(
                  "AZERICARD_GATEWAY_PRIVATE_KEY_PEM",
                  new PairingValue.FromAccountField(AzericardTerminal.GATEWAY_KEY_PROPERTY)),
              new PairingSetting("SIMULATOR_ACCOUNT_ID", new PairingValue.TheAccountId()),
              new PairingSetting("SIMULATOR_TEST_RUN_HEADER", new PairingValue.TheTestRunHeader()),
              new PairingSetting("SIMULATOR_TEST_RUN_ID", new PairingValue.TheTestRunId())));

  private AzericardPairing() {}
}
