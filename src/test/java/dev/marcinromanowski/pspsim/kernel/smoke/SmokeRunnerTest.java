package dev.marcinromanowski.pspsim.kernel.smoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class SmokeRunnerTest {

  private static final ProviderId PROVIDER = ProviderId.of("grow");

  private static final String OPERATION = "POST /api/light/server/1.0/createPaymentProcess";

  private static final Provenance PROVENANCE = Samples.A_PUBLISHED_EXAMPLE;

  private static final Provenance ANOTHER_SAMPLE = Samples.A_LIVE_PROBE;

  private static final ContractExpectation STATUS_IS_ONE =
      new ContractExpectation("status", Samples.published("1", PROVENANCE));

  private static final CheckTransport NOTHING_IS_SENT =
      request -> new CheckResponse(200, Map.of(), "");

  private static final ProviderId ANOTHER_PROVIDER = ProviderId.of("azericard");

  private static final CheckRunAccounts THE_CHECK_RUN_HOLDS_NONE = provider -> List.of();

  private final SmokeRunner runner =
      new SmokeRunner(aClockThatTicksOnceASecond(), THE_CHECK_RUN_HOLDS_NONE);

  @Test
  void a_check_whose_field_carries_the_published_value_holds() {
    SmokeRun run = runner.run(List.of(observing(Map.of("status", "1"))), NOTHING_IS_SENT);

    assertThat(run.verdicts())
        .singleElement()
        .isEqualTo(new SmokeVerdict.Passed(PROVIDER, OPERATION, PROVENANCE, 1));
    assertThat(run.everythingApplicableHeld()).isTrue();
  }

  @Test
  void
      a_check_whose_field_carries_another_value_fails_naming_provider_operation_section_and_field() {
    SmokeRun run = runner.run(List.of(observing(Map.of("status", "0"))), NOTHING_IS_SENT);

    assertThat(run.verdicts())
        .singleElement()
        .isEqualTo(
            new SmokeVerdict.Failed(
                PROVIDER, OPERATION, PROVENANCE, "status", FailureKind.NOT_AS_PUBLISHED));
    assertThat(run.everythingApplicableHeld()).isFalse();
  }

  @Test
  void a_check_whose_field_was_not_in_the_answer_fails_as_not_observed() {
    SmokeRun run = runner.run(List.of(observing(Map.of("err.id", "701"))), NOTHING_IS_SENT);

    assertThat(run.verdicts())
        .singleElement()
        .isEqualTo(
            new SmokeVerdict.Failed(
                PROVIDER, OPERATION, PROVENANCE, "status", FailureKind.NOT_OBSERVED));
  }

  @Test
  void a_check_that_declares_no_expectation_fails_rather_than_showing_a_tick() {
    ContractCheck assertsNothing =
        new DeclaredCheck(
            PROVIDER,
            OPERATION,
            PROVENANCE,
            Applicability.APPLIES,
            List.of(),
            transport -> Map.of());

    SmokeRun run = runner.run(List.of(assertsNothing), NOTHING_IS_SENT);

    assertThat(run.verdicts())
        .singleElement()
        .isEqualTo(
            new SmokeVerdict.Failed(
                PROVIDER, OPERATION, PROVENANCE, OPERATION, FailureKind.ASSERTS_NOTHING));
  }

  @Test
  void a_check_whose_route_did_not_answer_fails_as_unreachable() {
    ContractCheck unreachable =
        DeclaredCheck.applying(
            PROVIDER,
            OPERATION,
            PROVENANCE,
            List.of(STATUS_IS_ONE),
            transport -> {
              throw new IllegalStateException("the route a client dials did not answer");
            });

    SmokeRun run = runner.run(List.of(unreachable), NOTHING_IS_SENT);

    assertThat(run.verdicts())
        .singleElement()
        .isEqualTo(
            new SmokeVerdict.Failed(
                PROVIDER, OPERATION, PROVENANCE, OPERATION, FailureKind.UNREACHABLE));
  }

  @Test
  void a_check_the_vendor_publishes_nothing_for_is_neither_held_nor_failed() {
    SmokeRun run =
        runner.run(
            List.of(
                DeclaredCheck.notApplying(
                    PROVIDER, "POST notifyUrl", ANOTHER_SAMPLE, "nothing signs a callback")),
            NOTHING_IS_SENT);

    assertThat(run.verdicts())
        .singleElement()
        .isEqualTo(
            new SmokeVerdict.NotApplicable(
                PROVIDER, "POST notifyUrl", ANOTHER_SAMPLE, "nothing signs a callback"));
    assertThat(run.notApplicable()).isEqualTo(1);
    assertThat(run.passed()).isZero();
    assertThat(run.failed()).isZero();
  }

  @Test
  void the_first_expectation_that_does_not_hold_is_the_one_reported() {
    ContractExpectation second =
        new ContractExpectation("httpStatus", Samples.published("200", ANOTHER_SAMPLE));
    ContractCheck two =
        DeclaredCheck.applying(
            PROVIDER,
            OPERATION,
            PROVENANCE,
            List.of(STATUS_IS_ONE, second),
            transport -> Map.of("status", "0", "httpStatus", "500"));

    SmokeRun run = runner.run(List.of(two), NOTHING_IS_SENT);

    assertThat(run.verdicts())
        .singleElement()
        .isEqualTo(
            new SmokeVerdict.Failed(
                PROVIDER, OPERATION, PROVENANCE, "status", FailureKind.NOT_AS_PUBLISHED));
  }

  @Test
  void a_value_not_as_published_is_reported_with_the_sample_its_expectation_was_read_from() {
    ContractExpectation second =
        new ContractExpectation("httpStatus", Samples.published("200", ANOTHER_SAMPLE));
    ContractCheck two =
        DeclaredCheck.applying(
            PROVIDER,
            OPERATION,
            PROVENANCE,
            List.of(STATUS_IS_ONE, second),
            transport -> Map.of("status", "1", "httpStatus", "500"));

    SmokeRun run = runner.run(List.of(two), NOTHING_IS_SENT);

    assertThat(run.verdicts())
        .singleElement()
        .isEqualTo(
            new SmokeVerdict.Failed(
                PROVIDER, OPERATION, ANOTHER_SAMPLE, "httpStatus", FailureKind.NOT_AS_PUBLISHED));
  }

  @Test
  void a_missing_field_is_reported_with_the_sample_its_expectation_was_read_from() {
    ContractExpectation second =
        new ContractExpectation("httpStatus", Samples.published("200", ANOTHER_SAMPLE));
    ContractCheck two =
        DeclaredCheck.applying(
            PROVIDER,
            OPERATION,
            PROVENANCE,
            List.of(STATUS_IS_ONE, second),
            transport -> Map.of("status", "1"));

    SmokeRun run = runner.run(List.of(two), NOTHING_IS_SENT);

    assertThat(run.verdicts())
        .singleElement()
        .isEqualTo(
            new SmokeVerdict.Failed(
                PROVIDER, OPERATION, ANOTHER_SAMPLE, "httpStatus", FailureKind.NOT_OBSERVED));
  }

  @Test
  void a_run_records_how_long_the_checks_took() {
    SmokeRun run = runner.run(List.of(observing(Map.of("status", "1"))), NOTHING_IS_SENT);

    assertThat(run.startedAt()).isEqualTo(Instant.parse("2026-09-24T09:00:00Z"));
    assertThat(run.took()).isEqualTo(Duration.ofSeconds(1));
  }

  @Test
  void after_a_check_every_account_the_check_run_holds_for_its_provider_is_removed_by_name() {
    CannedTransport transport = aTransportThatRemovesAnything();
    SmokeRunner removing =
        new SmokeRunner(
            aClockThatTicksOnceASecond(),
            provider ->
                provider.equals(PROVIDER) ? List.of("merchant-1", "a merchant") : List.of("other"));

    removing.run(List.of(observing(Map.of("status", "1"))), transport);

    assertThat(transport.sent())
        .containsExactly(
            new CheckRequest(
                "DELETE", "/__simulator/grow/config/accounts/merchant-1", Map.of(), null),
            new CheckRequest(
                "DELETE", "/__simulator/grow/config/accounts/a%20merchant", Map.of(), null));
  }

  @Test
  void the_accounts_are_removed_after_each_check_before_the_next_one_configures_its_own() {
    CannedTransport transport = aTransportThatRemovesAnything().answering("GET", "/probe", 200, "");
    ContractCheck probing =
        DeclaredCheck.applying(
            ANOTHER_PROVIDER,
            OPERATION,
            PROVENANCE,
            List.of(STATUS_IS_ONE),
            sender -> {
              sender.send(CheckRequest.get("/probe"));
              return Map.of("status", "1");
            });
    SmokeRunner removing =
        new SmokeRunner(aClockThatTicksOnceASecond(), provider -> List.of(provider.value()));

    removing.run(List.of(observing(Map.of("status", "1")), probing), transport);

    assertThat(transport.sent())
        .extracting(request -> request.method() + " " + request.path())
        .containsExactly(
            "DELETE /__simulator/grow/config/accounts/grow",
            "GET /probe",
            "DELETE /__simulator/azericard/config/accounts/azericard");
  }

  @Test
  void a_check_whose_route_did_not_answer_still_has_its_accounts_removed() {
    CannedTransport transport = aTransportThatRemovesAnything();
    ContractCheck unreachable =
        DeclaredCheck.applying(
            PROVIDER,
            OPERATION,
            PROVENANCE,
            List.of(STATUS_IS_ONE),
            sender -> {
              throw new IllegalStateException("the route a client dials did not answer");
            });
    SmokeRunner removing =
        new SmokeRunner(aClockThatTicksOnceASecond(), provider -> List.of("merchant-1"));

    SmokeRun run = removing.run(List.of(unreachable), transport);

    assertThat(run.failed()).isEqualTo(1);
    assertThat(transport.sent())
        .extracting(CheckRequest::path)
        .containsExactly("/__simulator/grow/config/accounts/merchant-1");
  }

  @Test
  void a_second_run_while_one_is_inside_a_check_is_refused_at_once_and_touches_nothing()
      throws Exception {
    CannedTransport transport = aTransportThatRemovesAnything();
    SmokeRunner exclusive =
        new SmokeRunner(aClockThatTicksOnceASecond(), provider -> List.of("merchant-1"));
    CountDownLatch inside = new CountDownLatch(1);
    CountDownLatch finish = new CountDownLatch(1);
    ContractCheck held =
        DeclaredCheck.applying(
            PROVIDER,
            OPERATION,
            PROVENANCE,
            List.of(STATUS_IS_ONE),
            sender -> {
              inside.countDown();
              awaitOrFail(finish);
              return Map.of("status", "1");
            });

    CompletableFuture<SmokeRun> first =
        CompletableFuture.supplyAsync(() -> exclusive.run(List.of(held), transport));
    awaitOrFail(inside);

    assertThatThrownBy(() -> exclusive.run(List.of(observing(Map.of("status", "1"))), transport))
        .isInstanceOf(ChecksAlreadyRunningException.class)
        .hasMessage(ChecksAlreadyRunningException.THE_CHECKS_ARE_ALREADY_RUNNING);
    assertThat(transport.sent()).isEmpty();

    finish.countDown();
    SmokeRun run = first.get(10, TimeUnit.SECONDS);

    assertThat(run.verdicts())
        .singleElement()
        .isEqualTo(new SmokeVerdict.Passed(PROVIDER, OPERATION, PROVENANCE, 1));
    assertThat(transport.sent())
        .extracting(CheckRequest::path)
        .containsExactly("/__simulator/grow/config/accounts/merchant-1");
  }

  @Test
  void a_run_that_finished_leaves_the_runner_free_for_the_next_press() {
    runner.run(List.of(observing(Map.of("status", "1"))), NOTHING_IS_SENT);

    SmokeRun again = runner.run(List.of(observing(Map.of("status", "1"))), NOTHING_IS_SENT);

    assertThat(again.passed()).isEqualTo(1);
  }

  @Test
  void a_run_whose_cleanup_could_not_be_sent_still_leaves_the_runner_free_for_the_next_press() {
    SmokeRunner removing =
        new SmokeRunner(aClockThatTicksOnceASecond(), provider -> List.of("merchant-1"));
    CannedTransport answersNoRemoval = new CannedTransport();

    assertThatThrownBy(
            () -> removing.run(List.of(observing(Map.of("status", "1"))), answersNoRemoval))
        .isInstanceOf(IllegalStateException.class)
        .isNotInstanceOf(ChecksAlreadyRunningException.class);

    SmokeRun again =
        removing.run(List.of(observing(Map.of("status", "1"))), aTransportThatRemovesAnything());
    assertThat(again.passed()).isEqualTo(1);
  }

  private static void awaitOrFail(CountDownLatch latch) {
    try {
      assertThat(latch.await(10, TimeUnit.SECONDS)).as("the latch opened").isTrue();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  private static CannedTransport aTransportThatRemovesAnything() {
    return new CannedTransport().answering("DELETE", "/__simulator/", 200, "{}");
  }

  private static ContractCheck observing(Map<String, String> observations) {
    return DeclaredCheck.applying(
        PROVIDER, OPERATION, PROVENANCE, List.of(STATUS_IS_ONE), transport -> observations);
  }

  private static Supplier<Instant> aClockThatTicksOnceASecond() {
    Deque<Instant> ticks = new ArrayDeque<>();
    ticks.add(Instant.parse("2026-09-24T09:00:00Z"));
    ticks.add(Instant.parse("2026-09-24T09:00:01Z"));
    return () -> ticks.isEmpty() ? Instant.parse("2026-09-24T09:00:02Z") : ticks.removeFirst();
  }
}
