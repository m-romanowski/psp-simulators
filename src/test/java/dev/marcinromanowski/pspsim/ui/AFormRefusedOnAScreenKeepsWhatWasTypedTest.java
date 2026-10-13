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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

class AFormRefusedOnAScreenKeepsWhatWasTypedTest {

  private static final Instant NOW = Instant.parse("2026-09-30T10:00:00Z");

  private static final String A_RUN = "a-run";

  private static final String THE_PIX = "pix_waiting";

  private static final Pattern THE_RADIOS_A_READER_FINDS_TICKED =
      Pattern.compile("<input[^>]*\\schecked[^>]*>");

  @Test
  void the_values_a_refused_start_was_filled_with_are_drawn_in_the_form_again() {
    String drawn =
        thePixScreenAfter(
            WhatWasTyped.theFieldsOf(
                Map.of(
                    "amount",
                    "abc",
                    "currency",
                    "BRL",
                    "description",
                    "Pedido 7",
                    "orderId",
                    "pedido-abc-123")));

    assertThat(drawn)
        .as(
            "the redirect carried the refusal and three facts about the selection, and the"
                + " typed values were dropped: a tester read why their start was refused over"
                + " four empty fields and had to fill them all again")
        .contains("name=\"field.amount\"")
        .contains("value=\"abc\"")
        .contains("value=\"BRL\"")
        .contains("value=\"Pedido 7\"")
        .contains("value=\"pedido-abc-123\"");
  }

  @Test
  void a_form_nobody_has_answered_yet_is_drawn_with_its_fields_empty() {
    String drawn = thePixScreenAfter(WhatWasTyped.nothing());

    assertThat(drawn).contains("name=\"field.amount\"").doesNotContain("value=\"abc\"");
    assertThat(theRadiosTickedOn(drawn)).isEmpty();
  }

  @Test
  void the_payer_step_a_refused_completion_had_picked_is_ticked_again() {
    String drawn =
        thePixScreenAfter(WhatWasTyped.thePayerStepChosenFor(THE_PIX, "ABANDONED", null));

    assertThat(theRadiosTickedOn(drawn))
        .as(
            "an unconfirmed payer step came back with no radio ticked at all, so the tester had"
                + " to work out again which outcome they had picked before confirming it")
        .singleElement()
        .satisfies(ticked -> assertThat(ticked).contains("value=\"ABANDONED\""));
  }

  private static List<String> theRadiosTickedOn(String markup) {
    List<String> ticked = new ArrayList<>();
    Matcher found = THE_RADIOS_A_READER_FINDS_TICKED.matcher(markup);
    while (found.find()) {
      ticked.add(found.group());
    }
    return List.copyOf(ticked);
  }

  private static String thePixScreenAfter(WhatWasTyped typed) {
    Model model = new ConcurrentModel();
    model.addAttribute(WhatWasTyped.ATTRIBUTE, typed);
    ContributedScreens contributed = theScreensContributed();
    String view =
        new ContributedPage(
                contributed,
                new ProviderRegistry(List.of(aPixWaitingForItsPayer())),
                new OneConfiguredAccount(),
                aClockAt(NOW),
                "default")
            .render("autra", "pix", A_RUN, model);
    return ScreensThisTestDraws.drawn(view, model, contributed, "autra");
  }

  private static ProviderModuleDouble aPixWaitingForItsPayer() {
    return new ProviderModuleDouble("autra")
        .holding(
            new ProviderObjectState(
                ProviderObjectIdentity.of(THE_PIX),
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
                NOW))
        .stillAwaitingThePayerOn(THE_PIX);
  }

  private static ContributedScreens theScreensContributed() {
    return new ContributedScreens(
        List.of(new ContributedScreen("autra", "pix", "Pix", RailGroup.DAILY_WORK, 15)));
  }

  private static SimulatorClock aClockAt(Instant moment) {
    return testRunId -> moment;
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
