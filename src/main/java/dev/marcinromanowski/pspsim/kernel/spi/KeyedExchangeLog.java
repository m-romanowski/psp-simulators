package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.idempotency.FingerprintedRequest;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedOutcome;
import dev.marcinromanowski.pspsim.kernel.idempotency.RequestFingerprint;
import dev.marcinromanowski.pspsim.kernel.testrun.FingerprintKeys;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;

public final class KeyedExchangeLog {

  public static final String THE_BODY_IS = "application/json";

  private final ProviderKeyMechanisms mechanisms;
  private final FingerprintKeys keys;
  private final KeyedExchangeRows rows;
  private final SequencedRows sequenced;
  private final ObjectMapper json;

  public KeyedExchangeLog(
      ProviderKeyMechanisms mechanisms,
      FingerprintKeys keys,
      KeyedExchangeRows rows,
      SequencedRows sequenced,
      ObjectMapper json) {
    this.mechanisms = mechanisms;
    this.keys = keys;
    this.rows = rows;
    this.sequenced = sequenced;
    this.json = json;
  }

  public String fingerprintOf(TestRunId testRunId, FingerprintedRequest request) {
    return RequestFingerprint.of(keys.of(testRunId), request);
  }

  public void record(KeyedObservation observed) {
    KeyRole role = mechanisms.roleOf(observed.keyName());
    KeyedOutcome outcome = observed.outcome();
    KeyedAnswer answer = observed.answer();
    KeyedExchangeRow row =
        new KeyedExchangeRow(
            observed.testRunId(),
            observed.accountId(),
            observed.keyName(),
            observed.keyValue(),
            observed.operation(),
            fingerprintOf(observed.testRunId(), observed.request()),
            outcome.recordedWhereNoRequestRanTheOperation(),
            outcome.recordedAfterTheRequestThatRanTheOperation(role),
            answer == null ? null : answer.httpStatus(),
            answer == null ? null : THE_BODY_IS,
            withheldBodyOf(answer),
            observed.providerObjectId(),
            observed.stateChanged(),
            observed.transportFault(),
            observed.at());
    sequenced.recorded(
        "provider "
            + mechanisms.provider().value()
            + " answered a request under key "
            + observed.keyName()
            + ", and that exchange",
        () -> rows.recordedAtTheNextFreeSequence(row));
  }

  private @Nullable String withheldBodyOf(@Nullable KeyedAnswer answer) {
    if (answer == null) {
      return null;
    }
    return json.writeValueAsString(mechanisms.withholding().withheld(answer.fields()));
  }
}
