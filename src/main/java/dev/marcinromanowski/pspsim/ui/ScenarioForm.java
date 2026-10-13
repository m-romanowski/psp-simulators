package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.scenario.DeclaredAction;
import dev.marcinromanowski.pspsim.kernel.scenario.DeclaredMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.DeclaredPreset;
import dev.marcinromanowski.pspsim.kernel.scenario.DeclaredScenario;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRefusal;
import dev.marcinromanowski.pspsim.kernel.spi.ScenarioAdmission;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record ScenarioForm(
    @Nullable String priority,
    @Nullable String operation,
    @Nullable String merchantId,
    @Nullable String orderId,
    @Nullable String providerObjectId,
    @Nullable String customerId,
    @Nullable String paymentMethodId,
    @Nullable String amount,
    @Nullable String currency,
    @Nullable String eventType,
    @Nullable String headerName,
    @Nullable String headerValue,
    @Nullable String story,
    @Nullable String keyedOn,
    @Nullable String whenState,
    @Nullable String thenState,
    @Nullable String actionType,
    @Nullable String providerCode,
    @Nullable String fault,
    @Nullable String httpStatus,
    @Nullable String delayMs,
    @Nullable String settlesAfterMs) {

  public sealed interface Read {

    record Declared(DeclaredScenario scenario) implements Read {}

    record Unreadable(List<ScenarioRefusal> refusals) implements Read {

      public ActionVerdict.Refused theRefusalItReports() {
        return ActionVerdict.refused(
            RefusalSubject.noObjectWasNamed(
                ScenarioAdmission.THE_SCENARIO_RULE_IT_ASKED_THIS_PROVIDER_TO_HOLD));
      }
    }
  }

  public sealed interface Chosen {

    record Declared(DeclaredPreset preset) implements Chosen {}

    record Unreadable(List<ScenarioRefusal> refusals) implements Chosen {

      public ActionVerdict.Refused theRefusalItReports() {
        return ActionVerdict.refused(
            RefusalSubject.noObjectWasNamed(
                ScenarioAdmission.THE_SCENARIO_RULE_IT_ASKED_THIS_PROVIDER_TO_HOLD));
      }
    }
  }

  public static Map<String, List<String>> factsOfARefusal(
      @Nullable String preset, List<ScenarioRefusal> refusals) {
    List<String> properties = new ArrayList<>();
    for (ScenarioRefusal refusal : refusals) {
      properties.add(refusal.property());
    }
    return ActionFacts.none()
        .and("preset", preset)
        .andEach(ActionFacts.REFUSED_PROPERTIES, properties)
        .and(ActionFacts.REFUSED_BECAUSE, ScenarioRefusal.theWordsOf(refusals))
        .asRecorded();
  }

  public static Map<String, List<String>> factsOfARejection(
      @Nullable String preset, String message) {
    return ActionFacts.none()
        .and("preset", preset)
        .and(ActionFacts.REFUSED_BECAUSE, message)
        .asRecorded();
  }

  public static ScenarioForm empty() {
    return new ScenarioForm(
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null);
  }

  public Read read(@Nullable String testRunId) {
    List<ScenarioRefusal> refusals = new ArrayList<>();
    refuseAnythingButAWholeNumber("priority", priority, refusals);
    refuseAnythingButAWholeNumber("action.httpStatus", httpStatus, refusals);
    refuseAnythingButAWholeNumberOfMilliseconds("action.delayMs", delayMs, refusals);
    refuseAnythingButAWholeNumberOfMilliseconds("action.settlesAfterMs", settlesAfterMs, refusals);
    Map<String, String> header = headerOf(refusals);
    if (!refusals.isEmpty()) {
      return new Read.Unreadable(List.copyOf(refusals));
    }
    return new Read.Declared(
        new DeclaredScenario(
            testRunId,
            wholeNumberOf(priority),
            given(story),
            given(keyedOn),
            given(whenState),
            given(thenState),
            new DeclaredMatch(
                given(merchantId),
                given(orderId),
                given(providerObjectId),
                given(customerId),
                given(paymentMethodId),
                given(amount),
                given(currency),
                given(operation),
                given(eventType),
                header),
            new DeclaredAction(
                given(actionType),
                null,
                given(providerCode),
                given(fault),
                wholeNumberOf(httpStatus),
                null,
                millisecondsOf(delayMs),
                millisecondsOf(settlesAfterMs))));
  }

  public static Chosen readPreset(
      @Nullable String testRunId, @Nullable String preset, @Nullable String priority) {
    List<ScenarioRefusal> refusals = new ArrayList<>();
    refuseAnythingButAWholeNumber("priority", priority, refusals);
    if (!refusals.isEmpty()) {
      return new Chosen.Unreadable(List.copyOf(refusals));
    }
    return new Chosen.Declared(
        new DeclaredPreset(testRunId, given(preset), wholeNumberOf(priority), null));
  }

  private Map<String, String> headerOf(List<ScenarioRefusal> refusals) {
    String name = given(headerName);
    if (name == null) {
      if (given(headerValue) != null) {
        refusals.add(
            new ScenarioRefusal(
                "match.header",
                "names a value with no header to carry it, and a narrowing on a header the"
                    + " request never names could never fire"));
      }
      return Map.of();
    }
    Map<String, String> named = new LinkedHashMap<>();
    named.put(name, headerValue == null ? "" : headerValue);
    return Map.copyOf(named);
  }

  private static void refuseAnythingButAWholeNumber(
      String property, @Nullable String declared, List<ScenarioRefusal> refusals) {
    String given = given(declared);
    if (given == null) {
      return;
    }
    try {
      Integer.parseInt(given);
    } catch (NumberFormatException notANumber) {
      refusals.add(new ScenarioRefusal(property, "must be a whole number"));
    }
  }

  private static void refuseAnythingButAWholeNumberOfMilliseconds(
      String property, @Nullable String declared, List<ScenarioRefusal> refusals) {
    String given = given(declared);
    if (given == null) {
      return;
    }
    try {
      Long.parseLong(given);
    } catch (NumberFormatException notANumber) {
      refusals.add(new ScenarioRefusal(property, "must be a whole number of milliseconds"));
    }
  }

  private static @Nullable Integer wholeNumberOf(@Nullable String declared) {
    String given = given(declared);
    return given == null ? null : Integer.valueOf(given);
  }

  private static @Nullable Long millisecondsOf(@Nullable String declared) {
    String given = given(declared);
    return given == null ? null : Long.valueOf(given);
  }

  private static @Nullable String given(@Nullable String declared) {
    return declared == null || declared.isBlank() ? null : declared.strip();
  }
}
