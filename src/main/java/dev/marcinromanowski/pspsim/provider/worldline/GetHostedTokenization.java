package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class GetHostedTokenization {

  static final String NO_SESSION_ANSWERS_TO_THAT_ID =
      "hostedTokenizationId names no hosted tokenization session of this merchant, or names one"
          + " past the three hours provider worldline gives a session: \"Sessions have a maximum"
          + " life span of 3 hours. This means that you can only retrieve this information while"
          + " the session has not timed-out\". "
          + WorldlineAnswers.NO_ERRORS_ID_IS_PUBLISHED_FOR_AN_UNKNOWN_HOSTED_TOKENIZATION;

  static final String A_SESSION_NO_PAYER_HAS_FINISHED_ANSWERS_AN_EMPTY_OBJECT =
      "provider worldline publishes getHostedTokenizationResponse with two members, token and"
          + " tokenStatus, and requires neither, and it publishes no example of the answer a"
          + " session whose payer has not finished gives. Neither member exists before a payer"
          + " has finished, so this simulator answers 200 with an empty JSON object, which is the"
          + " narrowest body the published schema admits. That is an inference and not a"
          + " published answer";

  private final WorldlineTokenizations sessions;
  private final WorldlineAnswers answers;
  private final ScenarioEngine scenarios;

  GetHostedTokenization(
      WorldlineTokenizations sessions, WorldlineAnswers answers, ScenarioEngine scenarios) {
    this.sessions = sessions;
    this.answers = answers;
    this.scenarios = scenarios;
  }

  WorldlineOperationResult handle(
      Map<String, List<String>> headers,
      TestRunId testRunId,
      WorldlineMerchant merchant,
      String hostedTokenizationId,
      Instant now) {
    Optional<WorldlineTokenization> found =
        sessions
            .findById(testRunId, hostedTokenizationId)
            .filter(session -> session.accountId().equals(merchant.accountId()));
    if (found.isEmpty()) {
      return answers.noSuchHostedTokenization(NO_SESSION_ANSWERS_TO_THAT_ID);
    }
    WorldlineTokenization session = found.get();
    if (WorldlineStatus.EXPIRED.equals(session.statusAt(now))) {
      if (session.isAwaitingThePayer()) {
        sessions.moveFromAwaitingThePayerTo(session.expiredAt(now));
      }
      return answers.noSuchHostedTokenization(NO_SESSION_ANSWERS_TO_THAT_ID);
    }
    return WorldlineOperationResult.decidedBy(
        scenarios.decide(requestOf(headers, testRunId, merchant, session)),
        () -> read(session),
        code ->
            answers.declinedOnARouteThatPublishesNoDecline(
                code, CreateHostedTokenization.NO_DECLINE_IS_PUBLISHED_FOR_THIS_ROUTE));
  }

  private static ScenarioRequest requestOf(
      Map<String, List<String>> headers,
      TestRunId testRunId,
      WorldlineMerchant merchant,
      WorldlineTokenization session) {
    return new ScenarioRequest(
        WorldlineApi.WORLDLINE,
        testRunId,
        WorldlineStoryKey.ofHostedTokenization(session.hostedTokenizationId()),
        merchant.accountId(),
        null,
        session.hostedTokenizationId(),
        null,
        null,
        null,
        null,
        WorldlineOperations.GET_HOSTED_TOKENIZATION,
        headers);
  }

  private WorldlineOperationResult read(WorldlineTokenization session) {
    return WorldlineOperationResult.succeeded(
        session.hostedTokenizationId(), WorldlineBody.of(WorldlineAnswers.OK, theBodyOf(session)));
  }

  static WorldlineJson.JsonObject theBodyOf(WorldlineTokenization session) {
    String token = session.token();
    if (token == null) {
      return WorldlineJson.empty();
    }
    Map<String, WorldlineJson> answer = new LinkedHashMap<>();
    answer.put(WorldlineFields.TOKEN, theTokenOf(session, token));
    answer.put(WorldlineFields.TOKEN_STATUS, WorldlineJson.text(session.nativeStatus()));
    return WorldlineJson.object(answer);
  }

  private static WorldlineJson theTokenOf(WorldlineTokenization session, String token) {
    Map<String, WorldlineJson> cardWithoutCvv = new LinkedHashMap<>();
    cardWithoutCvv.put(
        WorldlineFields.CARD_NUMBER, WorldlineJson.text(String.valueOf(session.cardNumber())));
    cardWithoutCvv.put(
        WorldlineFields.EXPIRY_DATE, WorldlineJson.text(String.valueOf(session.expiryDate())));
    Map<String, WorldlineJson> data = new LinkedHashMap<>();
    data.put(WorldlineFields.CARD_WITHOUT_CVV, WorldlineJson.object(cardWithoutCvv));
    Map<String, WorldlineJson> card = new LinkedHashMap<>();
    card.put(WorldlineFields.DATA, WorldlineJson.object(data));
    Map<String, WorldlineJson> tokenObject = new LinkedHashMap<>();
    tokenObject.put(WorldlineFields.ID, WorldlineJson.text(token));
    tokenObject.put(WorldlineFields.IS_TEMPORARY, WorldlineJson.flag(false));
    tokenObject.put(WorldlineFields.CARD, WorldlineJson.object(card));
    return WorldlineJson.object(tokenObject);
  }
}
