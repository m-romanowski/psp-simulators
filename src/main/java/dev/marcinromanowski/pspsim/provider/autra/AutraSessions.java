package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.Optional;

interface AutraSessions {

  boolean mint(AutraAccessToken token);

  Optional<AutraAccessToken> accessToken(String value);

  boolean open(AutraSession session);

  Optional<AutraSession> session(TestRunId testRunId, String transactionalToken);

  int clear(TestRunId testRunId);

  int clearAll();
}
