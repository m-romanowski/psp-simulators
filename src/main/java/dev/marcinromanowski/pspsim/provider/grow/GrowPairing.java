package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.spi.PairingSetting;
import dev.marcinromanowski.pspsim.kernel.spi.PairingValue;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPairing;
import java.util.List;

final class GrowPairing {

  static final String BASE_PATH = "/api/light/server/1.0";

  static final String HOW_A_REQUEST_NAMES_ITS_ACCOUNT =
      "a Grow request carries no Authorization header at all: the account is named by the userId"
          + " and pageCode fields of the form-encoded body itself, and provider grow declares both"
          + " of them secret, so this screen names them and prints neither";

  static final ProviderPairing PAIRING =
      new ProviderPairing(
          GrowLightApi.GROW,
          BASE_PATH,
          HOW_A_REQUEST_NAMES_ITS_ACCOUNT,
          List.of(
              new PairingSetting("GROW_BASE_URL", new PairingValue.TheProviderBaseUrl()),
              new PairingSetting(
                  "GROW_CONTENT_TYPE", new PairingValue.Given("application/x-www-form-urlencoded")),
              new PairingSetting(
                  "GROW_USER_ID", new PairingValue.FromAccountField(GrowAccount.USER_ID)),
              new PairingSetting(
                  "GROW_PAGE_CODE", new PairingValue.FromAccountField(GrowAccount.PAGE_CODE)),
              new PairingSetting("SIMULATOR_ACCOUNT_ID", new PairingValue.TheAccountId()),
              new PairingSetting("SIMULATOR_TEST_RUN_HEADER", new PairingValue.TheTestRunHeader()),
              new PairingSetting("SIMULATOR_TEST_RUN_ID", new PairingValue.TheTestRunId())));

  private GrowPairing() {}
}
