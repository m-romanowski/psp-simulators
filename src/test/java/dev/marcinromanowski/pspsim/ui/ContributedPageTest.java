package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

class ContributedPageTest {

  private static final Instant NOW = Instant.parse("2026-09-30T10:00:00Z");

  private static final String A_RUN = "a-run";

  private static final int A_HUNDRED_OBJECTS = 100;

  @Test
  void a_contributed_screen_is_rendered_from_the_page_that_was_put_there() {
    Model model = new ConcurrentModel();

    String view = aPageServing(aPixScreen()).render("autra", "pix", A_RUN, model);

    assertThat(view).isEqualTo("extensions/autra/pix");
    assertThat(model.getAttribute("screen")).isEqualTo("x/autra/pix");
    assertThat(model.getAttribute("path")).isEqualTo("/ui/x/autra/pix");
    assertThat(model.getAttribute("provider")).isEqualTo("autra");
    assertThat(model.getAttribute("testRunId")).isEqualTo(A_RUN);
    assertThat(model.getAttribute("providers")).isEqualTo(List.of("autra"));
  }

  @Test
  void a_contributed_screen_is_handed_the_objects_of_its_run_and_the_time_it_is_read_at() {
    Model model = new ConcurrentModel();

    aPageServing(aPixScreen()).render("autra", "pix", A_RUN, model);

    assertThat(model.getAttribute("objects")).isEqualTo(List.of());
    assertThat(model.getAttribute("accounts")).isEqualTo(List.of("merchant-1"));
    assertThat(model.getAttribute("now")).isEqualTo(NOW);
  }

  @Test
  void a_contributed_screen_is_handed_each_object_with_what_a_tester_may_do_to_it() {
    Model model = new ConcurrentModel();

    aPageServing(aPixScreen(), holdingOneObject()).render("autra", "pix", A_RUN, model);

    assertThat((List<?>) model.getAttribute("objects"))
        .singleElement()
        .satisfies(
            row -> {
              ContributedObject listed = (ContributedObject) row;
              assertThat(listed.providerObjectId()).isEqualTo("pix-1");
              assertThat(listed.object().relatedObjects()).containsEntry("qrCode", "0002010102");
              assertThat(listed.followUps()).isEmpty();
              assertThat(listed.payerStep().offersAPayerStep())
                  .as(
                      "a contributed screen is handed the payer step its provider offers, because a"
                          + " screen that could not run it would be a screen a tester has to leave")
                  .isFalse();
            });
  }

  @Test
  void a_contributed_screen_is_told_the_provider_is_not_a_choice_a_screen_of_one_provider_offers() {
    Model model = new ConcurrentModel();

    aPageServing(aPixScreen()).render("autra", "pix", A_RUN, model);

    assertThat(model.getAttribute(ContributedPage.THE_SCREEN_IS_ONE_PROVIDERS_OWN))
        .as(
            "the provider of a contributed screen is the folder its file sits in, so a switcher"
                + " that took another one would land on this same screen with the choice thrown"
                + " away; the shell is told not to offer it")
        .isEqualTo(true);
  }

  @Test
  void a_contributed_screen_is_handed_the_run_clock_in_words_so_a_deadline_reads_as_one() {
    Model model = new ConcurrentModel();

    aPageServing(aPixScreen()).render("autra", "pix", A_RUN, model);

    assertThat(model.getAttribute(ContributedPage.A_MOMENT_IN_WORDS))
        .isEqualTo(new AMomentInWords(NOW));
  }

  @Test
  void a_row_carries_the_normalized_word_beside_the_providers_own_so_both_can_be_printed() {
    Model model = new ConcurrentModel();

    aPageServing(aPixScreen(), holdingOneObject()).render("autra", "pix", A_RUN, model);

    assertThat((List<?>) model.getAttribute("objects"))
        .singleElement()
        .satisfies(
            row ->
                assertThat(((ContributedObject) row).normalizedStatus())
                    .isEqualTo(
                        new StatusBadge(
                            NormalizedStatus.PROCESSING.name(), StatusBadge.Tone.PENDING)));
  }

  @Test
  void an_empty_run_is_reported_as_one_the_screen_read_to_the_end_of() {
    Model model = new ConcurrentModel();

    aPageServing(aPixScreen()).render("autra", "pix", A_RUN, model);

    assertThat(model.getAttribute(ContributedPage.THE_WHOLE_RUN_WAS_READ)).isEqualTo(true);
  }

  @Test
  void a_run_exactly_as_wide_as_one_read_carries_is_reported_as_a_run_the_screen_read_whole() {
    Model model = new ConcurrentModel();

    aPageServing(aPixScreen(), holding(A_HUNDRED_OBJECTS)).render("autra", "pix", A_RUN, model);

    assertThat((List<?>) model.getAttribute("objects")).hasSize(A_HUNDRED_OBJECTS);
    assertThat(model.getAttribute(ContributedPage.THE_WHOLE_RUN_WAS_READ))
        .as(
            "a read that ends on the last object of the run reached the end of it, and a run"
                + " confessed as half-read when it was read whole is as untrue as the other way"
                + " round")
        .isEqualTo(true);
  }

  @Test
  void a_run_wider_than_one_read_carries_is_read_as_far_as_it_goes_and_said_to_be_wider() {
    Model model = new ConcurrentModel();

    aPageServing(aPixScreen(), holding(A_HUNDRED_OBJECTS + 1)).render("autra", "pix", A_RUN, model);

    assertThat((List<?>) model.getAttribute("objects")).hasSize(A_HUNDRED_OBJECTS);
    assertThat(model.getAttribute(ContributedPage.THE_WHOLE_RUN_WAS_READ))
        .as(
            "a screen that claimed anything about the run out of a read it never finished states"
                + " a fact it does not hold")
        .isEqualTo(false);
  }

  @Test
  void the_objects_one_read_carries_are_few_enough_for_a_tester_to_be_handed_in_one_page() {
    assertThat(ContributedPage.AS_MANY_AS_ONE_READ_CARRIES)
        .as(
            "every object on a contributed screen is drawn with its follow-ups, its payer step and"
                + " a form for each: measured against a running simulator, a run of 1001 objects"
                + " drew 5,576,656 bytes and 1003 forms in 1.26s under the four-page read, where"
                + " one window draws 568,434 bytes and 102 forms in 0.15s whatever the run holds")
        .isLessThanOrEqualTo(A_HUNDRED_OBJECTS);
    assertThat(ContributedPage.ONE_MORE_THAN_A_READ_CARRIES)
        .as("a read wider than the SPI admits is refused by the query it is asked through")
        .isLessThanOrEqualTo(ProviderObjectQuery.LARGEST_LIMIT);
  }

  @Test
  void a_read_the_provider_refused_is_not_reported_as_a_run_the_screen_read_whole() {
    Model model = new ConcurrentModel();

    aPageServing(aPixScreen(), holding(A_HUNDRED_OBJECTS).refusingToReadTheRunAtAll())
        .render("autra", "pix", A_RUN, model);

    assertThat((List<?>) model.getAttribute("objects")).isEmpty();
    assertThat(model.getAttribute(ContributedPage.THE_WHOLE_RUN_WAS_READ))
        .as("a read the provider would not answer is a failed read and not an empty run")
        .isEqualTo(false);
  }

  @Test
  void a_screen_no_form_was_answered_on_is_still_handed_the_values_a_form_draws_from() {
    Model model = new ConcurrentModel();

    aPageServing(aPixScreen()).render("autra", "pix", A_RUN, model);

    assertThat(model.getAttribute(WhatWasTyped.ATTRIBUTE))
        .as(
            "a contributed file reads this on every input it draws, so a screen reached without a"
                + " refusal behind it has to be handed the empty one rather than nothing")
        .isEqualTo(WhatWasTyped.nothing());
  }

  @Test
  void what_a_refused_form_carried_back_is_handed_on_to_the_screen_as_it_was_typed() {
    Model model = new ConcurrentModel();
    WhatWasTyped carried = WhatWasTyped.theFieldsOf(Map.of("amount", "abc"));
    model.addAttribute(WhatWasTyped.ATTRIBUTE, carried);

    aPageServing(aPixScreen()).render("autra", "pix", A_RUN, model);

    assertThat(model.getAttribute(WhatWasTyped.ATTRIBUTE)).isSameAs(carried);
  }

  @Test
  void the_payer_step_a_refused_form_had_picked_reaches_the_row_it_was_picked_on() {
    Model model = new ConcurrentModel();
    model.addAttribute(
        WhatWasTyped.ATTRIBUTE, WhatWasTyped.thePayerStepChosenFor("pix-1", "PAID", "visa"));

    aPageServing(aPixScreen(), holdingOneObject().stillAwaitingThePayerOn("pix-1"))
        .render("autra", "pix", A_RUN, model);

    assertThat((List<?>) model.getAttribute("objects"))
        .singleElement()
        .satisfies(
            row ->
                assertThat(((ContributedObject) row).payerStep().outcomes())
                    .as(
                        "a step a tester picked that comes back unticked is a step they must pick"
                            + " again before they can confirm it")
                    .anySatisfy(
                        met -> {
                          assertThat(met.outcome()).isEqualTo("PAID");
                          assertThat(met.chosen()).isTrue();
                        }));
  }

  @Test
  void a_run_this_simulator_cannot_name_is_refused_on_the_contributed_screen_itself() {
    Model model = new ConcurrentModel();

    String view = aPageServing(aPixScreen()).render("autra", "pix", "a run with spaces", model);

    assertThat(view).isEqualTo("extensions/autra/pix");
    assertThat(model.getAttribute("refusal")).isNotNull();
    assertThat(model.getAttribute("objects")).isNull();
  }

  @Test
  void a_page_no_contributor_put_there_is_not_read_off_the_disk_at_all() {
    assertThatThrownBy(
            () -> aPageServing(aPixScreen()).render("autra", "qr", A_RUN, new ConcurrentModel()))
        .isInstanceOf(UnknownContributedScreenException.class)
        .hasMessageContaining("src/main/resources/templates/extensions/autra/qr.html")
        .hasMessageContaining("/ui/x/autra/pix");
  }

  @Test
  void the_route_hands_the_whole_decision_to_the_page_and_decides_nothing_of_its_own() {
    Model model = new ConcurrentModel();

    String view =
        new ContributedScreenController(aPageServing(aPixScreen()))
            .contributedScreen("autra", "pix", A_RUN, model);

    assertThat(view).isEqualTo("extensions/autra/pix");
  }

  private static ContributedScreen aPixScreen() {
    return new ContributedScreen("autra", "pix", "Pix", RailGroup.DAILY_WORK, 15);
  }

  private static ProviderModuleDouble holding(int objects) {
    ProviderModuleDouble module = new ProviderModuleDouble("autra");
    for (int row = 0; row < objects; row++) {
      module.holding(
          new ProviderObjectState(
              ProviderObjectIdentity.of("pix-" + row),
              new ProviderObjectKind("a Pix", "what this double stands in for"),
              "PENDING",
              NormalizedStatus.PROCESSING,
              Amount.of("99.90"),
              "BRL",
              Ledger.empty(),
              false,
              false,
              false,
              Map.of("qrCode", "0002010102"),
              ObjectOrigin.PROVIDER_API,
              NOW));
    }
    return module;
  }

  private static ProviderModuleDouble holdingOneObject() {
    return new ProviderModuleDouble("autra")
        .holding(
            new ProviderObjectState(
                ProviderObjectIdentity.of("pix-1"),
                new ProviderObjectKind("a Pix", "what this double stands in for"),
                "PENDING",
                NormalizedStatus.PROCESSING,
                Amount.of("99.90"),
                "BRL",
                Ledger.empty(),
                false,
                false,
                false,
                Map.of("qrCode", "0002010102"),
                ObjectOrigin.PROVIDER_API,
                NOW));
  }

  private static ContributedPage aPageServing(ContributedScreen contributed) {
    return aPageServing(contributed, new ProviderModuleDouble("autra"));
  }

  private static ContributedPage aPageServing(
      ContributedScreen contributed, ProviderModuleDouble module) {
    return new ContributedPage(
        new ContributedScreens(List.of(contributed)),
        new ProviderRegistry(List.of(module)),
        new OneConfiguredAccount(),
        new AClockThatDoesNotMove(),
        "default");
  }

  private static final class AClockThatDoesNotMove implements SimulatorClock {

    @Override
    public Instant now(TestRunId testRunId) {
      return NOW;
    }
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
