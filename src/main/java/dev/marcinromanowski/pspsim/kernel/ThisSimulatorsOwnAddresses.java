package dev.marcinromanowski.pspsim.kernel;

import java.util.List;

public final class ThisSimulatorsOwnAddresses {

  public static final String THE_CONTROL_API = "/__simulator";

  public static final String THE_SCREENS = "/ui";

  public static final String WHAT_THE_PLATFORM_EXPOSES = "/actuator";

  public static final String THE_ADDRESS_A_TESTER_TYPES = "/";

  public static final List<String> RESERVED_FOR_THIS_SIMULATOR =
      List.of(THE_CONTROL_API, THE_SCREENS, WHAT_THE_PLATFORM_EXPOSES);

  private ThisSimulatorsOwnAddresses() {}

  public static boolean thisSimulatorAnswersIt(String path) {
    return path.equals(THE_ADDRESS_A_TESTER_TYPES) || claims(path);
  }

  public static boolean claims(String path) {
    for (String reserved : RESERVED_FOR_THIS_SIMULATOR) {
      if (isUnder(path, reserved)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isUnder(String path, String namespace) {
    return path.equals(namespace) || path.startsWith(namespace + "/");
  }
}
