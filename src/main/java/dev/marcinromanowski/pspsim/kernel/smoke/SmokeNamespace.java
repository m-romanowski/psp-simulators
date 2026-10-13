package dev.marcinromanowski.pspsim.kernel.smoke;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunHeader;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import org.jspecify.annotations.Nullable;

public final class SmokeNamespace implements CheckTransport {

  public static final TestRunId RUN = TestRunId.of("smoke");

  public static final String FIELD = "testRunId";

  public static final String CONTROL_ROUTES = "/__simulator/";

  static final String A_CHECK_NAMES_NO_RUN_OF_ITS_OWN =
      "a compatibility check may not name a test run; the namespace stamps every request";

  private static final String JSON = "application/json";

  private final CheckTransport wire;

  public SmokeNamespace(CheckTransport wire) {
    this.wire = wire;
  }

  @Override
  public CheckResponse send(CheckRequest request) {
    return wire.send(stamped(refused(request)));
  }

  private static CheckRequest refused(CheckRequest request) {
    String body = request.body();
    if (request.path().contains(FIELD) || (body != null && body.contains(FIELD))) {
      throw new IllegalStateException(A_CHECK_NAMES_NO_RUN_OF_ITS_OWN);
    }
    return request;
  }

  private static CheckRequest stamped(CheckRequest request) {
    return new CheckRequest(request.method(), pathOf(request), request.headers(), bodyOf(request))
        .carrying(TestRunHeader.NAME, RUN.value());
  }

  private static String pathOf(CheckRequest request) {
    if (!request.path().startsWith(CONTROL_ROUTES)) {
      return request.path();
    }
    String separator = request.path().contains("?") ? "&" : "?";
    return request.path() + separator + FIELD + "=" + RUN.value();
  }

  private static @Nullable String bodyOf(CheckRequest request) {
    String body = request.body();
    if (!request.path().startsWith(CONTROL_ROUTES)) {
      return body;
    }
    if (body == null || !JSON.equals(request.headers().get("Content-Type"))) {
      return body;
    }
    String trimmed = body.strip();
    if (!trimmed.startsWith("{")) {
      return body;
    }
    String stamp = "\"" + FIELD + "\":\"" + RUN.value() + "\"";
    String rest = trimmed.substring(1).strip();
    return rest.startsWith("}") ? "{" + stamp + "}" : "{" + stamp + "," + rest;
  }
}
