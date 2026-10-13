package dev.marcinromanowski.pspsim.kernel.audit;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record AuditedAction(
    ProviderId provider,
    TestRunId testRunId,
    ObjectOrigin origin,
    String method,
    String path,
    ActionVerdict verdict,
    @Nullable String providerObjectId,
    Map<String, List<String>> facts,
    @Nullable String scenarioId,
    @Nullable String accountId) {

  public static final String A_ROW_THAT_TURNED_A_CALLER_AWAY_SAYS_WHY =
      "a row recording a call this simulator turned away must carry the words it was turned away"
          + " in, because the status and the subject tell a tester which thing was refused and"
          + " never why, and the trail is where they look once the answer is off their screen: ";

  public AuditedAction {
    accountId = TheAccountARowNames.theIdentityOrNone(accountId);
    if (origin == ObjectOrigin.PROVIDER_API) {
      throw new IllegalArgumentException(
          "a provider request is recorded as the exchange it is, an inbound row and the outbound"
              + " row that answered it, so it is not an action of a control surface");
    }
    if (verdict instanceof ActionVerdict.Refused refused) {
      providerObjectId = refused.subject().theObjectItNames().orElse(null);
      if (theWordsItWasTurnedAwayIn(facts).isBlank()) {
        throw new IllegalArgumentException(A_ROW_THAT_TURNED_A_CALLER_AWAY_SAYS_WHY + path);
      }
    }
    facts = Collections.unmodifiableMap(new LinkedHashMap<>(facts));
  }

  private static String theWordsItWasTurnedAwayIn(Map<String, List<String>> facts) {
    List<String> said = facts.get(ActionFacts.REFUSED_BECAUSE);
    return said == null || said.isEmpty() ? "" : String.join("", said).strip();
  }

  public int httpStatus() {
    return verdict.httpStatus();
  }

  public @Nullable RefusalSubject refusal() {
    return verdict instanceof ActionVerdict.Refused refused ? refused.subject() : null;
  }

  public AuditedAction decidedBy(@Nullable String rule) {
    return new AuditedAction(
        provider,
        testRunId,
        origin,
        method,
        path,
        verdict,
        providerObjectId,
        facts,
        rule,
        accountId);
  }

  public AuditedAction theAccountItActedFor(@Nullable String accountId) {
    return new AuditedAction(
        provider,
        testRunId,
        origin,
        method,
        path,
        verdict,
        providerObjectId,
        facts,
        scenarioId,
        accountId);
  }

  public static AuditedAction applied(
      ProviderId provider,
      TestRunId testRunId,
      ObjectOrigin origin,
      String method,
      String path,
      ActionVerdict.Applied verdict,
      Map<String, List<String>> facts) {
    return applied(provider, testRunId, origin, method, path, verdict, null, facts);
  }

  public static AuditedAction applied(
      ProviderId provider,
      TestRunId testRunId,
      ObjectOrigin origin,
      String method,
      String path,
      ActionVerdict.Applied verdict,
      @Nullable String providerObjectId,
      Map<String, List<String>> facts) {
    return new AuditedAction(
        provider, testRunId, origin, method, path, verdict, providerObjectId, facts, null, null);
  }

  public static AuditedAction refusing(
      ProviderId provider,
      TestRunId testRunId,
      ObjectOrigin origin,
      String method,
      String path,
      ActionVerdict.Refused verdict,
      Map<String, List<String>> facts) {
    return new AuditedAction(
        provider, testRunId, origin, method, path, verdict, null, facts, null, null);
  }
}
