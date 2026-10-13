package dev.marcinromanowski.pspsim.control;

import dev.marcinromanowski.pspsim.control.ControlPayloads.ControlError;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.ThisSimulatorsOwnAddresses;
import dev.marcinromanowski.pspsim.kernel.http.ASurfaceOfThisSimulator;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.WireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public final class TheControlApiInItsOwnWords implements ASurfaceOfThisSimulator {

  public static final String WHERE_THE_CONTROL_API_LIVES =
      ThisSimulatorsOwnAddresses.THE_CONTROL_API;

  static final String CONTENT_TYPE = "application/json";

  static final int WHAT_NOTHING_DECIDED = 500;

  static final String WHAT_IT_SAYS =
      "the simulator failed on its own control API and nothing on the way to the operation"
          + " answered the failure. This is a defect of the simulator, not of the request, and"
          + " whether the operation took effect cannot be read from this answer. The reasons are"
          + " in the audit trail of this provider, as a row of origin CONTROL_API carrying"
          + " causes, under the test run the request named in its query or under the reserved run"
          + " UNKNOWN when it named none there.";

  private final ProviderRegistry providers;
  private final ObjectMapper json;

  public TheControlApiInItsOwnWords(ProviderRegistry providers, ObjectMapper json) {
    this.providers = providers;
    this.json = json;
  }

  @Override
  public boolean owns(String address) {
    return ThisSimulatorsOwnAddresses.isUnder(address, WHERE_THE_CONTROL_API_LIVES);
  }

  @Override
  public ObjectOrigin whatReachesIt() {
    return ObjectOrigin.CONTROL_API;
  }

  @Override
  public Optional<ProviderId> theProviderItsAddressNames(String address) {
    return providers.find(theSegmentAfterTheNamespace(address)).map(SimulatorProvider::id);
  }

  @Override
  public Optional<WireAnswer> theAnswerTo(WireFault fault) {
    if (fault != WireFault.A_FAILURE_NOTHING_ELSE_ANSWERED) {
      return Optional.empty();
    }
    return Optional.of(
        new WireAnswer(
            WHAT_NOTHING_DECIDED,
            CONTENT_TYPE,
            json.writeValueAsBytes(new ControlError(WHAT_IT_SAYS))));
  }

  static String theSegmentAfterTheNamespace(String address) {
    if (!address.startsWith(WHERE_THE_CONTROL_API_LIVES + "/")) {
      return "";
    }
    String rest = address.substring(WHERE_THE_CONTROL_API_LIVES.length() + 1);
    int ends = rest.indexOf('/');
    return ends < 0 ? rest : rest.substring(0, ends);
  }
}
