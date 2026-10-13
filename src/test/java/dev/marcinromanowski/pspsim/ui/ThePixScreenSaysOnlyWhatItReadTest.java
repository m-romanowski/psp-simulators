package dev.marcinromanowski.pspsim.ui;

import static dev.marcinromanowski.pspsim.TheObjectsAnObjectNames.theObjectsNamed;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.AccountRemovalPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderConfigurationPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectIdentity;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectKind;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

class ThePixScreenSaysOnlyWhatItReadTest {

  private static final Instant NOW = Instant.parse("2026-09-30T10:00:00Z");

  private static final String A_RUN = "a-run";

  private static final String THE_EMPTY_RUN =
      "This test run holds no Pix of this provider. That is an empty run and not a failed read";

  private static final String THE_READ_THAT_STOPPED_SHORT = "did not reach the end of the run";

  private static final String THE_HEADING_THAT_SPEAKS_FOR_THE_RUN = "The Pix of this test run";

  private static final int A_HUNDRED_OBJECTS = 100;

  private static final String THE_PIX_WAITING = "pix-waiting";

  private static final String THE_MOMENT_AUTRA_ANSWERED = "2026-09-30T10:03:00Z";

  @Test
  void a_run_with_no_pix_in_it_that_was_read_to_the_end_is_the_empty_run_the_screen_says_it_is() {
    String drawn = thePixScreenOf(new ProviderModuleDouble("autra"));

    assertThat(drawn).contains(THE_EMPTY_RUN).contains(THE_HEADING_THAT_SPEAKS_FOR_THE_RUN);
    assertThat(drawn).doesNotContain(THE_READ_THAT_STOPPED_SHORT);
  }

  @Test
  void a_run_the_screen_did_not_finish_reading_is_not_drawn_as_a_run_with_no_pix_in_it() {
    String drawn = thePixScreenOf(holding(A_HUNDRED_OBJECTS + 1));

    assertThat(drawn)
        .as(
            "the reassurance is the falsehood: a read that stopped before the end of the run"
                + " cannot tell a run with no Pix in it from a run whose Pix it never reached")
        .doesNotContain(THE_EMPTY_RUN);
    assertThat(drawn)
        .contains(THE_READ_THAT_STOPPED_SHORT)
        .contains("This screen read " + A_HUNDRED_OBJECTS + " object(s)");
  }

  @Test
  void a_read_that_stopped_short_is_confessed_on_a_run_whose_pix_it_did_reach() {
    String drawn = thePixScreenOf(aPixAmong(A_HUNDRED_OBJECTS));

    assertThat(drawn)
        .as(
            "a screen that listed what it reached under a heading about the run spoke for a run"
                + " it had not read")
        .doesNotContain(THE_HEADING_THAT_SPEAKS_FOR_THE_RUN);
    assertThat(drawn).contains(THE_PIX_WAITING).contains(THE_READ_THAT_STOPPED_SHORT);
  }

  @Test
  void a_run_read_to_its_end_draws_its_pix_under_the_heading_that_speaks_for_the_run() {
    String drawn = thePixScreenOf(aPixAmong(0));

    assertThat(drawn)
        .contains(THE_PIX_WAITING)
        .contains(THE_HEADING_THAT_SPEAKS_FOR_THE_RUN)
        .doesNotContain(THE_READ_THAT_STOPPED_SHORT);
  }

  @Test
  void a_payload_that_still_works_says_when_it_stops_working_and_how_far_off_that_is() {
    String drawn = thePixScreenOf(aPixAmong(0).stillAwaitingThePayerOn(THE_PIX_WAITING));

    assertThat(drawn)
        .contains("when the payload stops working")
        .contains(THE_MOMENT_AUTRA_ANSWERED)
        .contains("in 3 minutes");
  }

  @Test
  void a_pix_that_takes_no_payer_any_more_is_not_said_to_have_a_deadline_still_to_come() {
    String drawn = thePixScreenOf(aPixAmong(0));

    assertThat(drawn)
        .as(
            "a Pix taken through PAID and settled read \"when the payload stops working ... in 3"
                + " minutes\", and settling is what stopped the payload working: autra publishes"
                + " SETTLED and EXPIRED as terminal states")
        .doesNotContain("when the payload stops working")
        .doesNotContain("in 3 minutes");
    assertThat(drawn)
        .contains(THE_MOMENT_AUTRA_ANSWERED)
        .contains("the payload stopped working whether or not");
  }

  @Test
  void each_row_says_which_word_is_the_providers_and_which_is_this_simulators() {
    String drawn = thePixScreenOf(aPixAmong(0));

    assertThat(drawn)
        .as(
            "a sentence above the list claiming every status below is written twice was drawn on"
                + " an empty run too, and nothing held a later field to carrying both words")
        .contains("as autra reports it")
        .contains("as this simulator normalizes it");
    assertThat(thePixScreenOf(new ProviderModuleDouble("autra")))
        .doesNotContain("as autra reports it");
  }

  private static String thePixScreenOf(ProviderModuleDouble module) {
    Model model = new ConcurrentModel();
    String view = aPageServing(module).render("autra", "pix", A_RUN, model);
    return ScreensThisTestDraws.drawn(view, model, theScreensContributed(), "autra");
  }

  private static ContributedPage aPageServing(ProviderModuleDouble module) {
    return new ContributedPage(
        theScreensContributed(),
        new ProviderRegistry(List.of(module)),
        new OneConfiguredAccount(),
        aClockAt(NOW),
        "default");
  }

  private static ContributedScreens theScreensContributed() {
    return new ContributedScreens(
        List.of(new ContributedScreen("autra", "pix", "Pix", RailGroup.DAILY_WORK, 15)));
  }

  private static SimulatorClock aClockAt(Instant moment) {
    return testRunId -> moment;
  }

  private static ProviderModuleDouble aPixAmong(int cardObjects) {
    ProviderModuleDouble module = new ProviderModuleDouble("autra");
    module.holding(
        new ProviderObjectState(
            ProviderObjectIdentity.of(THE_PIX_WAITING),
            new ProviderObjectKind("a Pix", "what this double stands in for"),
            "PENDING",
            NormalizedStatus.PROCESSING,
            Amount.of("99.90"),
            "BRL",
            Ledger.empty(),
            false,
            false,
            false,
            theObjectsNamed("qrCode", "0002010102", "expiresAt", "2026-09-30T10:03:00Z"),
            ObjectOrigin.PROVIDER_API,
            NOW));
    for (int row = 0; row < cardObjects; row++) {
      module.holding(aCardObjectNumbered(row));
    }
    return module;
  }

  private static ProviderModuleDouble holding(int objects) {
    ProviderModuleDouble module = new ProviderModuleDouble("autra");
    for (int row = 0; row < objects; row++) {
      module.holding(aCardObjectNumbered(row));
    }
    return module;
  }

  private static ProviderObjectState aCardObjectNumbered(int row) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of("card-" + row),
        new ProviderObjectKind("a sale on a stored card", "what this double stands in for"),
        "APPROVED",
        NormalizedStatus.SUCCEEDED,
        Amount.of("10.00"),
        "BRL",
        Ledger.empty(),
        false,
        false,
        false,
        Map.of(),
        ObjectOrigin.PROVIDER_API,
        NOW);
  }

  private static final class OneConfiguredAccount implements ProviderAccounts {

    @Override
    public void merge(
        ProviderId provider,
        TestRunId testRunId,
        List<String> accountIds,
        List<WebhookTarget> webhookTargets,
        ProviderConfigurationPlan plan) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(
        ProviderId provider, TestRunId testRunId, String accountId, AccountRemovalPlan plan) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<String> identitiesOf(ProviderId provider, TestRunId testRunId) {
      return List.of("merchant-1");
    }

    @Override
    public List<WebhookTarget> webhookTargetsOf(ProviderId provider, TestRunId testRunId) {
      return List.of();
    }
  }
}
