package dev.marcinromanowski.pspsim.kernel.http;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import dev.marcinromanowski.pspsim.RecordedActions;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.TheCausesOfAFailure;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderDouble;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.WhatAProviderAnswersOffItsRoutes;
import dev.marcinromanowski.pspsim.kernel.spi.WireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class WhoeverWasAskedTest {

  private static final String A_WIRE_ROUTE_OF_THE_PROVIDER = "/cgi-bin/cgi_link";

  private static final String AN_ADDRESS_THE_SURFACE_OWNS = "/__simulator/gateway/config";

  private static final String AN_ADDRESS_NOBODY_OWNS = "/zzz/nothingHere";

  private static final String THE_SCREENS = "/ui/nothingHere";

  private static final String WHAT_THE_PLATFORM_EXPOSES = "/actuator/nothingHere";

  private static final String A_NAMESPACE_A_SURFACE_CLAIMS_FOR_ITSELF = "/nobodys-land";

  private static final String THE_FRONT_DOOR = "/";

  private static final byte[] THE_PROVIDERS_OWN_BYTES =
      "<html>no ACTION, no RC</html>".getBytes(StandardCharsets.UTF_8);

  private static final int A_STATUS_THE_PROVIDER_CHOSE = 503;

  private final ProviderDouble gateway =
      new ProviderDouble("gateway")
          .answering(
              List.of("/cgi-bin"),
              new ProviderWireAnswer.InThisProvidersOwnWords(
                  A_STATUS_THE_PROVIDER_CHOSE, "text/html", THE_PROVIDERS_OWN_BYTES));

  private final ProviderRegistry registry = new ProviderRegistry(List.of(gateway));

  private final RecordedActions trail = new RecordedActions();

  private final ASurfaceDouble surface =
      new ASurfaceDouble("/__simulator").namingTheProvider(gateway.id());

  private final WhoeverWasAsked asked =
      new WhoeverWasAsked(
          new WhatAProviderAnswersOffItsRoutes(registry),
          registry,
          List.of(surface),
          trail.asTrail());

  @Test
  void a_failure_on_a_provider_route_is_answered_in_that_providers_words_and_no_surface_is_asked() {
    Optional<WireAnswer> answered = asked.answers(failedAt(A_WIRE_ROUTE_OF_THE_PROVIDER, null));

    assertThat(answered).isPresent();
    assertThat(answered.get().body()).isEqualTo(THE_PROVIDERS_OWN_BYTES);
    assertThat(answered.get().httpStatus()).isEqualTo(A_STATUS_THE_PROVIDER_CHOSE);
    assertThat(surface.theFaultsItWasAskedAbout())
        .as(
            "the provider that publishes the address owns the answer, and asking a surface of"
                + " this simulator after it would let this simulator's own envelope win on a"
                + " route an application reads against the vendor's contract")
        .isEmpty();
  }

  @Test
  void a_failure_on_an_address_a_surface_of_this_simulator_owns_is_answered_in_its_own_words() {
    Optional<WireAnswer> answered = asked.answers(failedAt(AN_ADDRESS_THE_SURFACE_OWNS, null));

    assertThat(answered).isPresent();
    assertThat(answered.get().body()).isEqualTo(ASurfaceDouble.ITS_OWN_BYTES);
    assertThat(answered.get().contentType()).isEqualTo("application/json");
    assertThat(surface.theFaultsItWasAskedAbout())
        .containsExactly(WireFault.A_FAILURE_NOTHING_ELSE_ANSWERED);
  }

  @Test
  void a_surface_that_owns_the_address_and_publishes_no_answer_for_this_fault_answers_nothing() {
    ASurfaceDouble silent = new ASurfaceDouble("/__simulator").publishingNoAnswerForAnything();
    WhoeverWasAsked withASilentSurface =
        new WhoeverWasAsked(
            new WhatAProviderAnswersOffItsRoutes(registry),
            registry,
            List.of(silent),
            trail.asTrail());

    assertThat(withASilentSurface.answers(failedAt(AN_ADDRESS_THE_SURFACE_OWNS, null)))
        .as(
            "the control API publishes an answer for a failure nothing planned for and none for"
                + " an address it does not serve, and inventing one here would change an answer"
                + " this slice did not decide")
        .isEmpty();
    assertThat(silent.theFaultsItWasAskedAbout())
        .containsExactly(WireFault.A_FAILURE_NOTHING_ELSE_ANSWERED);
  }

  @Test
  void an_address_no_provider_and_no_surface_owns_is_answered_with_no_body_at_all() {
    for (WireFault fault : WireFault.values()) {
      Optional<WireAnswer> answered =
          asked.answers(new AFailedRequest(fault, "GET", AN_ADDRESS_NOBODY_OWNS, 404, null, null));

      assertThat(answered)
          .as(
              "%s at an address nobody claims fell through to the framework, which published a"
                  + " shape no provider and no surface of this build answers in",
              fault)
          .isPresent();
      assertThat(answered.get().body()).as("%s", fault).isEmpty();
      assertThat(answered.get().contentType()).as("%s", fault).isEmpty();
      assertThat(answered.get().httpStatus()).as("%s", fault).isEqualTo(404);
    }
    assertThat(surface.theFaultsItWasAskedAbout()).isEmpty();
  }

  @Test
  void the_answer_at_an_address_nobody_claims_carries_the_status_nothing_else_chose() {
    Optional<WireAnswer> answered =
        asked.answers(
            new AFailedRequest(
                WireFault.A_FAILURE_NOTHING_ELSE_ANSWERED,
                "GET",
                AN_ADDRESS_NOBODY_OWNS,
                503,
                null,
                null));

    assertThat(answered).isPresent();
    assertThat(answered.get().httpStatus())
        .as(
            "the status is the one nothing else chose, and a status of this mechanism's own would"
                + " be a decision at an address nobody decided anything about")
        .isEqualTo(503);
  }

  @Test
  void an_address_of_this_simulators_own_that_no_surface_owns_keeps_the_answer_it_had() {
    for (String address : List.of(THE_SCREENS, WHAT_THE_PLATFORM_EXPOSES, THE_FRONT_DOOR)) {
      assertThat(asked.answers(failedAt(address, null)))
          .as(
              "%s is this simulator's own and not a surface asked the way a module is: the"
                  + " screens draw a failure on their own page, the platform endpoints answer for"
                  + " themselves, and / is the front door a tester types, which redirects to the"
                  + " screens and belongs to no provider only because no provider may claim it",
              address)
          .isEmpty();
    }
  }

  @Test
  void an_address_a_surface_owns_outside_this_simulators_own_namespaces_is_still_the_surfaces() {
    ASurfaceDouble elsewhere =
        new ASurfaceDouble(A_NAMESPACE_A_SURFACE_CLAIMS_FOR_ITSELF).publishingNoAnswerForAnything();
    WhoeverWasAsked overIt =
        new WhoeverWasAsked(
            new WhatAProviderAnswersOffItsRoutes(registry),
            registry,
            List.of(elsewhere),
            trail.asTrail());

    assertThat(overIt.answers(failedAt(A_NAMESPACE_A_SURFACE_CLAIMS_FOR_ITSELF + "/x", null)))
        .as(
            "a surface that was asked and published nothing for this fault decided that, and"
                + " answering over it would overrule a decision this slice did not take")
        .isEmpty();
  }

  @Test
  void a_failure_nothing_answered_on_a_provider_route_is_recorded_under_that_provider() {
    asked.record(
        failedAt(
            A_WIRE_ROUTE_OF_THE_PROVIDER,
            "run-7",
            new IllegalStateException("insert or update violates a constraint")));

    assertThat(trail.written()).hasSize(1);
    AuditRecord row = trail.written().get(0);
    assertThat(row.provider()).isEqualTo(ProviderId.of("gateway"));
    assertThat(row.origin()).isEqualTo(ObjectOrigin.PROVIDER_API);
    assertThat(row.testRunId()).isEqualTo(TestRunId.of("run-7"));
    assertThat(row.theCalledPath()).isEqualTo(A_WIRE_ROUTE_OF_THE_PROVIDER);
    assertThat(row.method()).isEqualTo("POST");
    assertThat(row.httpStatus()).isEqualTo(500);
    assertThat(row.sanitizedBody())
        .as(
            "the wire answer carries none of this, and a tester who cannot read the reason off"
                + " the trail has no way left to tell a defect of this simulator from a refusal"
                + " the provider published")
        .containsEntry(
            TheCausesOfAFailure.THE_CAUSES,
            List.of("IllegalStateException: insert or update violates a constraint"));
  }

  @Test
  void a_failure_on_an_address_a_surface_owns_is_recorded_under_the_provider_it_names() {
    asked.record(failedAt(AN_ADDRESS_THE_SURFACE_OWNS, "run-7", new IllegalStateException("x")));

    assertThat(trail.written()).hasSize(1);
    assertThat(trail.written().get(0).origin()).isEqualTo(ObjectOrigin.CONTROL_API);
    assertThat(trail.written().get(0).provider()).isEqualTo(ProviderId.of("gateway"));
  }

  @Test
  void a_surface_that_names_no_provider_leaves_the_trail_untouched_and_still_answers() {
    ASurfaceDouble namingNobody = new ASurfaceDouble("/__simulator").namingTheProvider(null);
    WhoeverWasAsked overIt =
        new WhoeverWasAsked(
            new WhatAProviderAnswersOffItsRoutes(registry),
            registry,
            List.of(namingNobody),
            trail.asTrail());

    overIt.record(failedAt("/__simulator/nothingHere", "run-7", new IllegalStateException("x")));

    assertThat(trail.written())
        .as(
            "the trail is keyed by provider, and a row filed under a provider the address never"
                + " named is a row no tester of that provider asked for")
        .isEmpty();
    assertThat(overIt.answers(failedAt("/__simulator/nothingHere", null))).isPresent();
  }

  @Test
  void an_address_nobody_owns_leaves_no_row_behind() {
    asked.record(failedAt(AN_ADDRESS_NOBODY_OWNS, "run-7", new IllegalStateException("x")));

    assertThat(trail.written()).isEmpty();
  }

  @Test
  void only_a_failure_nothing_else_answered_is_recorded() {
    for (WireFault fault :
        List.of(
            WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE,
            WireFault.A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER)) {
      asked.record(
          new AFailedRequest(
              fault,
              "GET",
              A_WIRE_ROUTE_OF_THE_PROVIDER,
              500,
              new IllegalStateException("x"),
              null));
    }

    assertThat(trail.written())
        .as(
            "a client asking for an address a provider does not serve is not a defect of this"
                + " simulator, and filing one row per mistyped address would bury the rows that"
                + " are")
        .isEmpty();
  }

  @Test
  void a_run_the_request_named_nowhere_or_named_unreadably_is_the_run_reserved_for_that() {
    asked.record(failedAt(A_WIRE_ROUTE_OF_THE_PROVIDER, null, new IllegalStateException("x")));
    asked.record(failedAt(A_WIRE_ROUTE_OF_THE_PROVIDER, "", new IllegalStateException("x")));
    asked.record(
        failedAt(
            A_WIRE_ROUTE_OF_THE_PROVIDER, "not a test run id", new IllegalStateException("x")));
    asked.record(failedAt(A_WIRE_ROUTE_OF_THE_PROVIDER, "UNKNOWN", new IllegalStateException("x")));

    assertThat(trail.written()).hasSize(4);
    for (AuditRecord row : trail.written()) {
      assertThat(row.testRunId())
          .as(
              "an error dispatch cannot read the run out of a body that was never parsed, and a"
                  + " row filed under the default run would claim a run the request never named")
          .isEqualTo(TestRunId.UNKNOWN);
    }
  }

  @Test
  void a_failure_that_raised_nothing_readable_is_still_recorded_as_a_failure() {
    asked.record(failedAt(A_WIRE_ROUTE_OF_THE_PROVIDER, "run-7", null));

    assertThat(trail.written()).hasSize(1);
    assertThat(trail.written().get(0).sanitizedBody()).isEmpty();
  }

  @Test
  void the_answer_nobody_gave_is_named_on_the_server_log_with_the_request_that_met_it() {
    WhatTheLogRemembers serverLog =
        WhatTheLogRemembers.attachedTo("dev.marcinromanowski.pspsim.kernel.http.WhoeverWasAsked");
    try {
      asked.answers(
          new AFailedRequest(
              WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE,
              "DELETE",
              AN_ADDRESS_NOBODY_OWNS,
              404,
              null,
              null));

      assertThat(serverLog.saidAt(Level.INFO))
          .as(
              "the wire carries no body, no media type and no row, so a request that met this"
                  + " answer and left no line of the log at all left a tester nothing to find:"
                  + " the framework's envelope it replaced at least echoed the path")
          .anySatisfy(
              said ->
                  assertThat(said)
                      .contains("DELETE")
                      .contains(AN_ADDRESS_NOBODY_OWNS)
                      .contains("404"));
    } finally {
      serverLog.stopReading();
    }
  }

  @Test
  void an_answer_somebody_did_give_is_not_announced_as_nobodys() {
    WhatTheLogRemembers serverLog =
        WhatTheLogRemembers.attachedTo("dev.marcinromanowski.pspsim.kernel.http.WhoeverWasAsked");
    try {
      asked.answers(failedAt(A_WIRE_ROUTE_OF_THE_PROVIDER, null));
      asked.answers(failedAt(AN_ADDRESS_THE_SURFACE_OWNS, null));
      asked.answers(failedAt(THE_SCREENS, null));

      assertThat(serverLog.saidAt(Level.INFO))
          .as(
              "a provider answered the first, a surface the second and the screens keep the"
                  + " third, and a line claiming nobody claimed them would send a tester looking"
                  + " for a typo that is not there")
          .isEmpty();
    } finally {
      serverLog.stopReading();
    }
  }

  private static AFailedRequest failedAt(String address, @Nullable String theRunItNamed) {
    return failedAt(address, theRunItNamed, null);
  }

  private static AFailedRequest failedAt(
      String address, @Nullable String theRunItNamed, @Nullable Throwable raised) {
    return new AFailedRequest(
        WireFault.A_FAILURE_NOTHING_ELSE_ANSWERED, "POST", address, 500, raised, theRunItNamed);
  }
}
