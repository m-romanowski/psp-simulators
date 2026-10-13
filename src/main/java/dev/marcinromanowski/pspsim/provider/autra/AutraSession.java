package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;

record AutraSession(
    TestRunId testRunId,
    String transactionalToken,
    String accountId,
    String documentId,
    Instant createdAt) {}
