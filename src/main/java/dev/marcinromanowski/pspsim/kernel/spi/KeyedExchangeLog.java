package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.idempotency.FingerprintedRequest;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;
import dev.marcinromanowski.pspsim.kernel.idempotency.RequestFingerprint;
import dev.marcinromanowski.pspsim.kernel.testrun.FingerprintKeys;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;

public final class KeyedExchangeLog {

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
    ExchangeVerdict decided = observed.verdict();
    KeyedAnswer answer = observed.answer();
    KeyedExchangeRow row =
        new KeyedExchangeRow(
            observed.testRunId(),
            observed.accountId(),
            observed.keyName(),
            observed.keyValue(),
            observed.operation(),
            fingerprintOf(observed.testRunId(), observed.request()),
            decided.recordedWhereNoRequestClaimedTheKey(),
            decided.recordedAfterTheRequestThatClaimedTheKey(role),
            answer == null ? null : answer.httpStatus(),
            answer == null ? null : answer.contentType(),
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
