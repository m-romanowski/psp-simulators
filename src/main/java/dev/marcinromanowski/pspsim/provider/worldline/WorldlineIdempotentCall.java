package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.idempotency.FingerprintedRequest;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedObservation;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

final class WorldlineIdempotentCall {

  static final String A_KEY_ON_AN_UNSUPPORTED_CALL_IS_IGNORED =
      "provider worldline publishes exactly which operations honour an idempotence key —"
          + " CreatePayment, CancelPayment, RefundPayment, CapturePayment, CompletePayment,"
          + " CreatePayout and SubsequentPayment — and publishes what happens to a key sent on"
          + " any other call: \"If an idempotence key is sent for a call that does not support"
          + " idempotence, the RESTful Server API will ignore the key and treat the request as a"
          + " first request\". The two hosted tokenization routes of this slice are among the"
          + " calls that do not support it, so a key on one of them is read, ignored and not"
          + " recorded as a keyed exchange at all";

  static final String THE_KEY_IS_STILL_RUNNING =
      "a request under this idempotence key is still being processed. Provider worldline"
          + " publishes this case and this status: \"For requests in progress: A response with an"
          + " HTTP status code of 409 (Conflict) will indicate that the request is currently"
          + " being processed.\"";

  private WorldlineIdempotentCall() {}

  static WorldlineOperationResult under(
      WorldlineIdempotenceKeys keys,
      WorldlineAnswers answers,
      WorldlineApiCall call,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      WorldlineMerchant merchant,
      String operation,
      Instant now,
      Supplier<WorldlineOperationResult> run) {
    if (WorldlineIdempotence.carriesTwoSpellingsThatDisagree(headers)) {
      return answers.refusedValue(
          WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT,
          WorldlineIdempotence.TWO_SPELLINGS_ONE_KEY);
    }
    Optional<WorldlineIdempotence.Sent> sent = WorldlineIdempotence.keyIn(headers);
    if (sent.isEmpty()) {
      return run.get();
    }
    WorldlineIdempotence.Sent key = sent.get();
    if (WorldlineIdempotence.isTooLong(key.value())) {
      return answers.refusedValue(key.headerName(), WorldlineIdempotence.THE_KEY_IS_TOO_LONG);
    }
    if (WorldlineIdempotence.isNotAscii(key.value())) {
      return answers.refusedValue(key.headerName(), WorldlineIdempotence.THE_KEY_IS_NOT_ASCII);
    }
    FingerprintedRequest request =
        new FingerprintedRequest(
            call.method(), operation, call.body().map(WorldlineJson::flatten).orElseGet(Map::of));
    String fingerprint = keys.fingerprintOf(testRunId, request);
    WorldlineIdempotenceClaim claim =
        keys.claim(
            testRunId,
            merchant.accountId(),
            key.value(),
            key.headerName(),
            operation,
            fingerprint,
            now);
    return switch (claim) {
      case WorldlineIdempotenceClaim.Fresh _ ->
          theFirstRequestUnder(keys, testRunId, merchant, key, operation, request, now, run);
      case WorldlineIdempotenceClaim.Replayed replayed ->
          theStoredAnswerOf(keys, testRunId, merchant, key, operation, request, now, replayed);
      case WorldlineIdempotenceClaim.HeldByARequestStillRunning _ ->
          theRefusalWhileTheKeyIsHeld(
              keys, answers, testRunId, merchant, key, operation, request, now);
    };
  }

  private static WorldlineOperationResult theFirstRequestUnder(
      WorldlineIdempotenceKeys keys,
      TestRunId testRunId,
      WorldlineMerchant merchant,
      WorldlineIdempotence.Sent key,
      String operation,
      FingerprintedRequest request,
      Instant now,
      Supplier<WorldlineOperationResult> run) {
    WorldlineOperationResult answered;
    try {
      answered = run.get();
    } catch (RuntimeException unanswerable) {
      keys.released(testRunId, merchant.accountId(), key.value());
      throw unanswerable;
    }
    if (answered.outcome() == ProviderOutcome.UNKNOWN) {
      keys.released(testRunId, merchant.accountId(), key.value());
      return answered;
    }
    WorldlineBody body = answered.body();
    try {
      record(
          keys,
          testRunId,
          merchant,
          key,
          operation,
          request,
          KeyedOutcome.RAN_THE_OPERATION,
          body.asAKeyedAnswer(),
          answered.providerObjectId(),
          answered.outcome() == ProviderOutcome.SUCCESS,
          now);
    } finally {
      keys.completed(
          testRunId,
          merchant.accountId(),
          key.value(),
          new WorldlineStoredAnswer(
              body.httpStatus(),
              String.valueOf(body.contentType()),
              body.text(),
              answered.providerObjectId()));
    }
    return answered;
  }

  private static WorldlineOperationResult theStoredAnswerOf(
      WorldlineIdempotenceKeys keys,
      TestRunId testRunId,
      WorldlineMerchant merchant,
      WorldlineIdempotence.Sent key,
      String operation,
      FingerprintedRequest request,
      Instant now,
      WorldlineIdempotenceClaim.Replayed replayed) {
    WorldlineStoredAnswer answer = replayed.answer();
    Map<String, List<String>> replayHeaders = new LinkedHashMap<>();
    replayHeaders.put(
        WorldlineFields.IDEMPOTENCE_REQUEST_TIMESTAMP,
        List.of(WorldlineIdempotence.theTimestampHeaderValueOf(replayed.theFirstRequestWasAt())));
    String created = answer.providerObjectId();
    if (answer.httpStatus() == WorldlineAnswers.CREATED && created != null) {
      replayHeaders.put(
          WorldlineFields.LOCATION,
          List.of(WorldlineApiController.thePaymentResourceOf(merchant.merchantId(), created)));
    }
    replayHeaders.put(WorldlineFields.CONTENT_TYPE, List.of(answer.contentType()));
    WorldlineBody body =
        WorldlineBody.of(
            answer.httpStatus(),
            WorldlineJsonReader.objectIn(answer.body()).orElseGet(WorldlineJson::empty),
            replayHeaders);
    record(
        keys,
        testRunId,
        merchant,
        key,
        operation,
        request,
        KeyedOutcome.REPLAYED,
        body.asAKeyedAnswer(),
        answer.providerObjectId(),
        false,
        now);
    return WorldlineOperationResult.succeeded(answer.providerObjectId(), body);
  }

  private static WorldlineOperationResult theRefusalWhileTheKeyIsHeld(
      WorldlineIdempotenceKeys keys,
      WorldlineAnswers answers,
      TestRunId testRunId,
      WorldlineMerchant merchant,
      WorldlineIdempotence.Sent key,
      String operation,
      FingerprintedRequest request,
      Instant now) {
    WorldlineOperationResult refused = answers.refusedWhileTheKeyWasHeld(THE_KEY_IS_STILL_RUNNING);
    record(
        keys,
        testRunId,
        merchant,
        key,
        operation,
        request,
        KeyedOutcome.REFUSED_WHILE_THE_KEY_WAS_HELD,
        refused.body().asAKeyedAnswer(),
        null,
        false,
        now);
    return refused;
  }

  private static void record(
      WorldlineIdempotenceKeys keys,
      TestRunId testRunId,
      WorldlineMerchant merchant,
      WorldlineIdempotence.Sent key,
      String operation,
      FingerprintedRequest request,
      KeyedOutcome outcome,
      @Nullable KeyedAnswer answer,
      @Nullable String providerObjectId,
      boolean stateChanged,
      Instant now) {
    keys.observed(
        new KeyedObservation(
            testRunId,
            merchant.accountId(),
            WorldlineKeyMechanisms.IDEMPOTENCE_KEY,
            key.value(),
            operation,
            outcome,
            request,
            answer,
            providerObjectId,
            stateChanged,
            null,
            now));
  }
}
