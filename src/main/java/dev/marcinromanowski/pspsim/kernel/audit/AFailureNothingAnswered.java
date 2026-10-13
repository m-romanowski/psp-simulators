package dev.marcinromanowski.pspsim.kernel.audit;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;

public record AFailureNothingAnswered(
    ProviderId provider,
    TestRunId testRunId,
    ObjectOrigin origin,
    String method,
    String path,
    int httpStatus,
    List<String> causes) {

  public static final int LOWEST_SERVER_ERROR = 500;

  public AFailureNothingAnswered {
    if (httpStatus < LOWEST_SERVER_ERROR) {
      throw new IllegalArgumentException(
          "a failure nothing on the way to the operation answered is recorded under the status"
              + " that nothing chose, which is a server error, and "
              + httpStatus
              + " is a status something decided");
    }
    causes = List.copyOf(causes);
  }
}
