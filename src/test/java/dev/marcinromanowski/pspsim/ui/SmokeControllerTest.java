package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckResponse;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckTransport;
import dev.marcinromanowski.pspsim.kernel.smoke.ChecksAlreadyRunningException;
import dev.marcinromanowski.pspsim.kernel.smoke.CompatibilityChecks;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractCheck;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractExpectation;
import dev.marcinromanowski.pspsim.kernel.smoke.DeclaredCheck;
import dev.marcinromanowski.pspsim.kernel.smoke.Samples;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeResults;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeRunner;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

class SmokeControllerTest {

  private static final ProviderId GROW = ProviderId.of("grow");

  private final CountDownLatch inside = new CountDownLatch(1);

  private final CountDownLatch finish = new CountDownLatch(1);

  private final SmokeResults results = new SmokeResults();

  private final SmokeController screen =
      new SmokeController(
          new ProviderRegistry(List.of()),
          new CompatibilityChecks(
              List.of(new CompatibilityChecks.ProviderChecks("grow", List.of(aHeldCheck())))),
          new SmokeRunner(Instant::now, provider -> List.of()),
          results,
          nothingIsSent(),
          "default");

  @Test
  void a_run_name_typed_into_the_bar_of_this_screen_comes_back_refused_on_this_screen() {
    SmokeController servingGrow =
        new SmokeController(
            new ProviderRegistry(List.of(new ProviderModuleDouble("grow"))),
            new CompatibilityChecks(List.of()),
            new SmokeRunner(Instant::now, provider -> List.of()),
            new SmokeResults(),
            nothingIsSent(),
            "default");
    ExtendedModelMap model = new ExtendedModelMap();

    assertThat(servingGrow.smoke("grow", "a run", model))
        .as(
            "this screen used to take whatever the bar held in silence, and the sweep over the"
                + " screens dropped it rather than failing")
        .isEqualTo("smoke");
    assertThat(model.get("refusal"))
        .isEqualTo(
            "testRunId must match [A-Za-z0-9][A-Za-z0-9._-]{0,63}, which is at most 64 characters,"
                + " the first a letter or a digit and the rest a letter, a digit, a dot, a hyphen"
                + " or an underscore, for example checkout-smoke");
  }

  @Test
  void a_press_while_the_checks_are_running_is_refused_on_the_screen_and_starts_nothing()
      throws Exception {
    CompletableFuture<String> first =
        CompletableFuture.supplyAsync(
            () -> screen.run(null, null, new ExtendedModelMap(), new RedirectAttributesModelMap()));
    awaitOrFail(inside);

    ExtendedModelMap refusedOn = new ExtendedModelMap();
    String second = screen.run(null, null, refusedOn, new RedirectAttributesModelMap());

    assertThat(second).isEqualTo("smoke");
    assertThat(refusedOn.get("refusal"))
        .isEqualTo(ChecksAlreadyRunningException.THE_CHECKS_ARE_ALREADY_RUNNING);
    assertThat(results.last()).isEmpty();

    finish.countDown();
    assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo("redirect:/ui/smoke");
    assertThat(results.last()).hasValueSatisfying(run -> assertThat(run.passed()).isEqualTo(1));
  }

  @Test
  void a_press_nobody_else_is_running_starts_the_checks_and_shows_no_refusal() {
    finish.countDown();
    ExtendedModelMap model = new ExtendedModelMap();

    assertThat(screen.run(null, null, model, new RedirectAttributesModelMap()))
        .isEqualTo("redirect:/ui/smoke");
    assertThat(model.get("refusal")).isNull();
    assertThat(results.last()).isPresent();
  }

  private ContractCheck aHeldCheck() {
    return DeclaredCheck.applying(
        GROW,
        "POST /api/light/server/1.0/createPaymentProcess",
        Samples.A_PUBLISHED_EXAMPLE,
        List.of(
            new ContractExpectation("status", Samples.published("1", Samples.A_PUBLISHED_EXAMPLE))),
        transport -> {
          inside.countDown();
          awaitOrFail(finish);
          return Map.of("status", "1");
        });
  }

  private static CheckTransport nothingIsSent() {
    return request -> new CheckResponse(200, Map.of(), "");
  }

  private static void awaitOrFail(CountDownLatch latch) {
    try {
      assertThat(latch.await(10, TimeUnit.SECONDS)).as("the latch opened").isTrue();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }
}
