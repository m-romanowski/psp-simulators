package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

record TranzilaChallenge(
    String trackId,
    TestRunId testRunId,
    String accountId,
    String terminalName,
    long transactionId,
    TranzilaTxnType type,
    @Nullable Integer verifyMode,
    String amount,
    String currencyCode,
    String token,
    TranzilaCard card,
    String responseCvv,
    String responseId,
    @Nullable String dcDisable,
    boolean forceTxnOnFail,
    String originalRequest,
    @Nullable String redirectUrl,
    SequencedMap<String, String> redirectParams,
    @Nullable TranzilaHostedFieldsEcho hostedFields,
    ObjectOrigin origin,
    Instant createdAt,
    @Nullable Verdict payerVerdict,
    @Nullable Instant answeredAt,
    @Nullable Instant completedAt) {

  static final String ID_LEAD = "3ds_";

  static final int ID_HEX = 40;

  enum Verdict {
    PASSED,

    FAILED
  }

  TranzilaChallenge {
    redirectParams = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(redirectParams));
  }

  boolean isAnswered() {
    return payerVerdict != null;
  }

  boolean isCompleted() {
    return completedAt != null;
  }

  boolean passed() {
    return payerVerdict == Verdict.PASSED;
  }

  boolean isOnTheHostedFields() {
    return hostedFields != null;
  }

  TranzilaChallenge answered(Verdict verdict, Instant at) {
    return new TranzilaChallenge(
        trackId,
        testRunId,
        accountId,
        terminalName,
        transactionId,
        type,
        verifyMode,
        amount,
        currencyCode,
        token,
        card,
        responseCvv,
        responseId,
        dcDisable,
        forceTxnOnFail,
        originalRequest,
        redirectUrl,
        redirectParams,
        hostedFields,
        origin,
        createdAt,
        verdict,
        at,
        completedAt);
  }

  TranzilaChallenge completedAs(long made, Instant at) {
    return new TranzilaChallenge(
        trackId,
        testRunId,
        accountId,
        terminalName,
        made,
        type,
        verifyMode,
        amount,
        currencyCode,
        token,
        card,
        responseCvv,
        responseId,
        dcDisable,
        forceTxnOnFail,
        originalRequest,
        redirectUrl,
        redirectParams,
        hostedFields,
        origin,
        createdAt,
        payerVerdict,
        answeredAt,
        at);
  }

  String theOperation() {
    Integer mode = verifyMode;
    return mode == null ? type.wire() : type.wire() + " " + mode;
  }
}
