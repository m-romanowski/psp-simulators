package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.idempotency.FingerprintedRequest;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedOutcome;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedObservation;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class CompleteThreeDSecure {

  static final String OPERATION = "3ds complete";

  static final Set<String> THE_FIELDS_THE_SCHEMA_PUBLISHES =
      Set.of(TranzilaThreeDSecureAnswers.TRACK_ID, TranzilaFields.TERMINAL_NAME);

  static final String NOT_A_JSON_OBJECT = "the request body is not a JSON object";

  private final TranzilaChallenges challenges;
  private final TranzilaTransactions transactions;
  private final CreateTransaction createTransaction;
  private final TranzilaExchanges exchanges;
  private final IdGenerator ids;

  CompleteThreeDSecure(
      TranzilaChallenges challenges,
      TranzilaTransactions transactions,
      CreateTransaction createTransaction,
      TranzilaExchanges exchanges,
      IdGenerator ids) {
    this.challenges = challenges;
    this.transactions = transactions;
    this.createTransaction = createTransaction;
    this.exchanges = exchanges;
    this.ids = ids;
  }

  sealed interface Reached {

    record NoChallenge(Optional<TranzilaChallenge> alreadyCompleted) implements Reached {}

    record NotAnswered(TranzilaChallenge challenge) implements Reached {}

    record AnsweredInstead(TranzilaOperationResult result) implements Reached {}

    record ADuplicate(TranzilaChallenge challenge, TranzilaTransaction original)
        implements Reached {}

    record Made(
        TranzilaChallenge challenge,
        TranzilaTransaction transaction,
        TranzilaOperationResult result)
        implements Reached {}
  }

  TranzilaOperationResult handle(
      TranzilaApiCall call, TestRunId testRunId, TranzilaTerminal terminal, Instant now) {
    Optional<TranzilaJson.JsonObject> parsed = call.body();
    if (parsed.isEmpty()) {
      return TranzilaAnswers.refusedBody(
          TranzilaErrors.INVALID_JSON_FORMAT,
          TranzilaErrors.INVALID_JSON_FORMAT_TEXT,
          NOT_A_JSON_OBJECT);
    }
    TranzilaJson.JsonObject body = parsed.get();
    for (String sent : body.members().keySet()) {
      if (!THE_FIELDS_THE_SCHEMA_PUBLISHES.contains(sent)) {
        return TranzilaAnswers.refusedSchema(sent + " is not a field of the complete schema");
      }
    }
    Optional<String> trackId = body.text(TranzilaThreeDSecureAnswers.TRACK_ID);
    if (trackId.isEmpty()) {
      return TranzilaAnswers.refusedSchema(
          TranzilaThreeDSecureAnswers.TRACK_ID + " is required and must be a string");
    }
    Optional<String> terminalName = body.text(TranzilaFields.TERMINAL_NAME);
    if (terminalName.isEmpty()) {
      return TranzilaAnswers.refusedSchema(
          TranzilaFields.TERMINAL_NAME + " is required and must be a string");
    }
    if (!terminalName.get().equals(terminal.terminalName())) {
      return TranzilaAnswers.refusedCredential(
          TranzilaErrors.AUTHORIZATION_FAILED,
          TranzilaErrors.AUTHORIZATION_FAILED_TEXT,
          CreateTransaction.ANOTHER_TERMINAL);
    }
    Reached reached = reach(call, call.headers(), testRunId, terminal, trackId.get(), false, now);
    TranzilaOperationResult result = answerOf(reached);
    observe(call, testRunId, terminal, trackId.get(), reached, result, now);
    return result;
  }

  Reached reach(
      TranzilaApiCall call,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      TranzilaTerminal terminal,
      String trackId,
      boolean onTheHostedFields,
      Instant now) {
    return transactions.together(
        () -> {
          Optional<TranzilaChallenge> locked =
              challenges
                  .lockedUntilCompleted(testRunId, terminal.accountId(), trackId)
                  .filter(held -> held.isOnTheHostedFields() == onTheHostedFields);
          if (locked.isEmpty()) {
            return new Reached.NoChallenge(
                challenges
                    .find(testRunId, trackId)
                    .filter(held -> held.accountId().equals(terminal.accountId()))
                    .filter(held -> held.isOnTheHostedFields() == onTheHostedFields)
                    .filter(TranzilaChallenge::isCompleted));
          }
          TranzilaChallenge challenge = locked.get();
          if (!challenge.isAnswered()) {
            return new Reached.NotAnswered(challenge);
          }
          String dcDisable = challenge.dcDisable();
          if (dcDisable == null) {
            return completed(call, headers, terminal, challenge, now);
          }
          TranzilaDcDisableKey key =
              new TranzilaDcDisableKey(
                  testRunId,
                  terminal.accountId(),
                  dcDisable,
                  challenge.type(),
                  challenge.verifyMode());
          return transactions.oneAtATimeUnder(
              key,
              () ->
                  transactions
                      .approvedUnderDcDisable(
                          key, now.minus(CreateTransaction.THE_DUPLICATE_CHECK_LOOKS_BACK))
                      .<Reached>map(
                          original -> {
                            challenges.complete(
                                challenge.completedAs(original.transactionId(), now));
                            return new Reached.ADuplicate(challenge, original);
                          })
                      .orElseGet(() -> completed(call, headers, terminal, challenge, now)));
        });
  }

  private Reached completed(
      TranzilaApiCall call,
      Map<String, List<String>> headers,
      TranzilaTerminal terminal,
      TranzilaChallenge challenge,
      Instant now) {
    TranzilaOperationResult result =
        createTransaction.completing(call, headers, terminal, challenge, now);
    if (result.insteadOfTheOperation() != null) {
      return new Reached.AnsweredInstead(result);
    }
    TranzilaTransaction transaction =
        Optional.ofNullable(result.providerObjectId())
            .flatMap(TranzilaIdentifiers::transactionIdIn)
            .flatMap(
                id ->
                    transactions
                        .paymentHolding(challenge.testRunId(), id.longValue())
                        .flatMap(payment -> payment.transaction(id.longValue())))
            .orElseThrow();
    TranzilaChallenge completed = challenge.completedAs(transaction.transactionId(), now);
    challenges.complete(completed);
    return new Reached.Made(completed, transaction, result);
  }

  TranzilaOperationResult answerOf(Reached reached) {
    return switch (reached) {
      case Reached.NoChallenge _ -> TranzilaThreeDSecureAnswers.noChallengeToComplete();
      case Reached.NotAnswered waiting ->
          TranzilaThreeDSecureAnswers.notAnsweredYet(waiting.challenge().trackId());
      case Reached.AnsweredInstead instead -> instead.result();
      case Reached.ADuplicate duplicate ->
          TranzilaAnswers.aDuplicateOf(
              duplicate.original(), theOriginalRequestOf(duplicate.challenge()));
      case Reached.Made made -> theAnswerOf(made);
    };
  }

  private TranzilaOperationResult theAnswerOf(Reached.Made made) {
    TranzilaOperationResult result = made.result();
    TranzilaTransaction transaction = made.transaction();
    if (!transaction.approved()) {
      return result;
    }
    TranzilaOperationResult answered =
        TranzilaThreeDSecureAnswers.completed(
            transaction,
            made.challenge().passed() ? TranzilaThreeDSecureAnswers.theAuthentication(ids) : null,
            theOriginalRequestOf(made.challenge()));
    String scenarioId = result.scenarioId();
    if (scenarioId == null) {
      return answered;
    }
    TransportFault fault = result.fault();
    return fault == null
        ? answered.proceededUnder(scenarioId)
        : answered.faultedBy(scenarioId, fault);
  }

  static TranzilaJson.JsonObject theOriginalRequestOf(TranzilaChallenge challenge) {
    return TranzilaJsonReader.objectIn(challenge.originalRequest()).orElseThrow();
  }

  private void observe(
      TranzilaApiCall call,
      TestRunId testRunId,
      TranzilaTerminal terminal,
      String trackId,
      Reached reached,
      TranzilaOperationResult result,
      Instant now) {
    @Nullable KeyedOutcome outcome =
        switch (reached) {
          case Reached.Made _, Reached.ADuplicate _ -> KeyedOutcome.RAN_THE_OPERATION;
          case Reached.NoChallenge none ->
              none.alreadyCompleted().isPresent() ? KeyedOutcome.REFUSED_AS_A_DUPLICATE : null;
          case Reached.NotAnswered _, Reached.AnsweredInstead _ -> null;
        };
    if (outcome == null || result.outcome() == ProviderOutcome.UNKNOWN) {
      return;
    }
    exchanges.observed(
        new KeyedObservation(
            testRunId,
            terminal.accountId(),
            TranzilaThreeDSecureAnswers.TRACK_ID,
            trackId,
            OPERATION,
            outcome,
            new FingerprintedRequest(
                call.method(),
                OPERATION,
                call.body().map(TranzilaJson::flatten).orElseGet(Map::of)),
            result.body().asAKeyedAnswer(),
            result.providerObjectId(),
            reached instanceof Reached.Made || reached instanceof Reached.ADuplicate,
            null,
            now));
  }
}
