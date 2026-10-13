package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.AccountRemovalPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderConfigurationPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

class TheWayOffAScreenOfOneProvidersOwnTest {

  private static final Instant NOW = Instant.parse("2026-09-30T10:00:00Z");

  private static final String A_RUN = "a-run";

  @Test
  void the_link_that_offers_the_provider_choice_carries_the_run_the_tester_is_looking_at() {
    assertThat(aScreenOfOneProvidersOwn(A_RUN))
        .as(
            "a link that dropped the test run landed a tester on the default run with nothing"
                + " said, which is the whole of getting lost: the choice is offered on the"
                + " Payments screen, and it has to be offered there for this run")
        .contains("href=\"/ui/objects?testRunId=" + A_RUN + "\"");
  }

  @Test
  void that_link_claims_nothing_about_screens_other_than_the_one_it_leads_to() {
    assertThat(aScreenOfOneProvidersOwn(A_RUN))
        .as(
            "the tooltip claimed every screen this simulator carries for all providers offers the"
                + " choice, which /openapi/index.html does not: it is a rail destination with no"
                + " shell at all, and a screen's tooltip is no place to speak for the application")
        .doesNotContain("every screen this simulator carries")
        .contains("the choice is on the Payments screen, where this link goes");
  }

  private static String aScreenOfOneProvidersOwn(String testRunId) {
    Model model = new ConcurrentModel();
    ContributedScreens contributed = theScreensContributed();
    String view =
        new ContributedPage(
                contributed,
                new ProviderRegistry(List.of(new ProviderModuleDouble("autra"))),
                new NoAccountOfAnyProvider(),
                aClockAt(NOW),
                "default")
            .render("autra", "pix", testRunId, model);
    return ScreensThisTestDraws.drawn(view, model, contributed, "autra");
  }

  private static ContributedScreens theScreensContributed() {
    return new ContributedScreens(
        List.of(new ContributedScreen("autra", "pix", "Pix", RailGroup.DAILY_WORK, 15)));
  }

  private static SimulatorClock aClockAt(Instant moment) {
    return testRunId -> moment;
  }

  private static final class NoAccountOfAnyProvider implements ProviderAccounts {

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
      return List.of();
    }

    @Override
    public List<WebhookTarget> webhookTargetsOf(ProviderId provider, TestRunId testRunId) {
      return List.of();
    }
  }
}
