package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRefusal;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurationAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredWebhookCall;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.PaymentMethodFixtureAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.ScenarioAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.TheTrailRowAStartedPaymentLeaves;
import dev.marcinromanowski.pspsim.kernel.spi.UnknownProviderObjectException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookNotSchedulableException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScreenRefusalsTest {

  private static final String THE_CALLBACK =
      DeclaredWebhookCall.THE_CALLBACK_IT_ASKED_THIS_PROVIDER_TO_SCHEDULE;

  private static final String THE_PAYMENT =
      TheTrailRowAStartedPaymentLeaves.THE_PAYMENT_IT_ASKED_THIS_PROVIDER_TO_START;

  private static final String THE_COMPLETION =
      HostedCompletionOutcome.THE_COMPLETION_IT_ASKED_THIS_PROVIDER_TO_PERFORM;

  @Test
  void a_callback_the_provider_would_not_schedule_for_that_object_is_refused_naming_the_callback() {
    assertThat(
            DeliveryForm.theRefusalOfAScheduling(
                new WebhookNotSchedulableException("no event for a released hold")))
        .isEqualTo(ActionVerdict.refused(RefusalSubject.noObjectWasNamed(THE_CALLBACK)));
  }

  @Test
  void a_callback_for_an_object_no_run_holds_is_not_found_naming_the_field_that_named_it() {
    assertThat(
            DeliveryForm.theRefusalOfAScheduling(
                new UnknownProviderObjectException("no such object")))
        .isEqualTo(
            ActionVerdict.notFound(
                RefusalSubject.noObjectAnsweredTo(ObjectScreen.PROVIDER_OBJECT_ID)));
  }

  @Test
  void a_callback_this_simulator_could_not_read_is_rejected_naming_the_callback() {
    assertThat(
            DeliveryForm.theRefusalOfAScheduling(
                new IllegalArgumentException("providerObjectId is required")))
        .isEqualTo(ActionVerdict.rejected(RefusalSubject.noObjectWasNamed(THE_CALLBACK)));
  }

  @Test
  void a_send_that_scheduled_nothing_is_not_found_the_way_the_control_api_already_answers_it() {
    assertThat(DeliveryForm.theRefusalOfASendThatScheduledNothing())
        .isEqualTo(ActionVerdict.notFound(RefusalSubject.noObjectWasNamed(THE_CALLBACK)));
  }

  @Test
  void a_retry_of_a_delivery_this_run_does_not_hold_names_the_field_it_looked_under() {
    assertThat(DeliveryForm.theRefusalOfARetryOfNoDeliveryOfThisRun())
        .isEqualTo(
            ActionVerdict.notFound(
                RefusalSubject.noObjectAnsweredTo(DeclaredWebhookCall.WEBHOOK_JOB_ID)));
  }

  @Test
  void an_unconfirmed_callback_a_composer_refusal_and_a_target_refusal_are_all_rejections() {
    assertThat(DeliveryForm.theRefusalOfAnUnconfirmedCallback())
        .isEqualTo(ActionVerdict.rejected(RefusalSubject.noObjectWasNamed(THE_CALLBACK)));
    assertThat(new DeliveryForm.Composed.Refused("no object").theRefusalItReports())
        .isEqualTo(ActionVerdict.rejected(RefusalSubject.noObjectWasNamed(THE_CALLBACK)));
    assertThat(new DeliveryForm.Posting.Refused("no target").theRefusalItReports())
        .isEqualTo(ActionVerdict.rejected(RefusalSubject.noObjectWasNamed(THE_CALLBACK)));
  }

  @Test
  void a_refused_send_and_a_refused_retry_each_record_what_the_request_named_and_the_refusal() {
    assertThat(DeliveryForm.factsOfARefusedSend("pp_1", "PAID", "no"))
        .containsEntry(ObjectScreen.PROVIDER_OBJECT_ID, List.of("pp_1"))
        .containsEntry("eventType", List.of("PAID"))
        .containsEntry(ActionFacts.REFUSED_BECAUSE, List.of("no"));
    assertThat(DeliveryForm.factsOfARefusedRetry("whk_1", "https://m.test/hook", "no"))
        .containsEntry(DeclaredWebhookCall.WEBHOOK_JOB_ID, List.of("whk_1"))
        .containsEntry("targetUrl", List.of("https://m.test/hook"))
        .containsEntry(ActionFacts.REFUSED_BECAUSE, List.of("no"));
  }

  @Test
  void a_completion_this_screen_would_not_take_unconfirmed_is_rejected_naming_the_completion() {
    assertThat(HostedCompletionForm.theRefusalOfAnUnconfirmedCompletion())
        .as(
            "asking a tester to confirm is the one refusal of a completion that belongs to this"
                + " screen alone, because no suite is asked to confirm anything; a completion"
                + " this simulator could not read is recorded by the composer both surfaces"
                + " write through, which rejects it in the same words for both")
        .isEqualTo(ActionVerdict.rejected(RefusalSubject.noObjectWasNamed(THE_COMPLETION)));
  }

  @Test
  void a_refused_completion_records_the_outcome_the_card_and_the_refusal_it_was_answered_with() {
    assertThat(
            HostedCompletionForm.theFactsOfACompletionThisScreenTurnedAway(
                "PAID", "sandbox-4580", "already completed"))
        .containsEntry("outcome", List.of("PAID"))
        .containsEntry("card", List.of("sandbox-4580"))
        .containsEntry(ActionFacts.REFUSED_BECAUSE, List.of("already completed"));
  }

  @Test
  void a_payment_the_provider_or_a_rule_would_not_start_is_refused_naming_the_payment() {
    assertThat(
            OriginationForm.theRefusalItReports(
                new OriginationOutcome.RefusedByTheProvider("declined")))
        .isEqualTo(ActionVerdict.refused(RefusalSubject.noObjectWasNamed(THE_PAYMENT)));
    assertThat(
            OriginationForm.theRefusalItReports(
                new OriginationOutcome.AnsweredByAScenarioInstead("scn_1")))
        .isEqualTo(ActionVerdict.refused(RefusalSubject.noObjectWasNamed(THE_PAYMENT)));
    assertThat(OriginationForm.theRefusalItReports(new OriginationOutcome.NotOffered("no start")))
        .as(
            "a provider that starts no payment of the kind that was asked for was asked for"
                + " something it publishes nowhere, which the control api has always filed as a"
                + " rejected request, and the same answer filed under two statuses left a tester"
                + " unable to compare a step they clicked with the same step a suite made")
        .isEqualTo(ActionVerdict.rejected(RefusalSubject.noObjectWasNamed(THE_PAYMENT)));
  }

  @Test
  void a_payment_named_on_an_account_the_run_no_longer_configures_is_not_found() {
    assertThat(OriginationForm.theRefusalItReports(new OriginationOutcome.NoSuchAccount()))
        .isEqualTo(ActionVerdict.notFound(RefusalSubject.noObjectWasNamed(THE_PAYMENT)));
  }

  @Test
  void a_payment_that_started_reports_no_refusal_at_all() {
    assertThat(
            OriginationForm.theRefusalItReports(
                new OriginationOutcome.Originated("pp_1", "a hosted page")))
        .isNull();
  }

  @Test
  void a_run_configuring_no_account_is_refused_and_an_unreadable_origination_is_rejected() {
    assertThat(OriginationForm.theRefusalOfARunThatConfiguresNoAccount())
        .isEqualTo(ActionVerdict.refused(RefusalSubject.noObjectWasNamed(THE_PAYMENT)));
    assertThat(OriginationForm.theRefusalOfAnOriginationThisSimulatorCannotRead())
        .isEqualTo(ActionVerdict.rejected(RefusalSubject.noObjectWasNamed(THE_PAYMENT)));
  }

  @Test
  void a_refused_origination_records_the_account_the_way_it_was_started_and_the_refusal() {
    assertThat(
            OriginationForm.theFactsOfARequestTurnedAway("merchant-1", "HOSTED_PAGE", "no account"))
        .containsEntry("accountId", List.of("merchant-1"))
        .containsEntry("startedAs", List.of("HOSTED_PAGE"))
        .containsEntry(ActionFacts.REFUSED_BECAUSE, List.of("no account"));
  }

  @Test
  void an_unconfirmed_release_names_the_route_alias_and_an_unconfirmed_removal_the_account() {
    assertThat(ConfigurationScreen.theRefusalOfAnUnconfirmedRelease())
        .isEqualTo(
            ActionVerdict.rejected(
                RefusalSubject.noObjectWasNamed(
                    ConfigurationAdmission.THE_ROUTE_ALIAS_IT_ASKED_THIS_PROVIDER_TO_RELEASE)));
    assertThat(ConfigurationScreen.theRefusalOfAnUnconfirmedRemoval())
        .isEqualTo(
            ActionVerdict.rejected(
                RefusalSubject.noObjectWasNamed(
                    ConfigurationAdmission.THE_ACCOUNT_IT_ASKED_THIS_PROVIDER_TO_FORGET)));
  }

  @Test
  void a_callback_target_named_for_no_account_is_rejected_naming_the_configuration() {
    assertThat(AccountForm.theRefusalOfACallbackTargetForNoAccount())
        .isEqualTo(
            ActionVerdict.rejected(
                RefusalSubject.noObjectWasNamed(
                    ConfigurationAdmission.THE_CONFIGURATION_IT_ASKED_THIS_PROVIDER_TO_HOLD)));
  }

  @Test
  void
      a_fixture_this_simulator_cannot_read_names_the_seeding_and_an_unconfirmed_removal_the_forgetting() {
    assertThat(PaymentMethodForm.theRefusalOfAFixtureThisSimulatorCannotRead())
        .isEqualTo(
            ActionVerdict.rejected(
                RefusalSubject.noObjectWasNamed(
                    PaymentMethodFixtureAdmission.THE_FIXTURE_IT_ASKED_THIS_PROVIDER_TO_SEED)));
    assertThat(PaymentMethodForm.theRefusalOfAnUnconfirmedRemoval())
        .isEqualTo(
            ActionVerdict.rejected(
                RefusalSubject.noObjectWasNamed(
                    PaymentMethodFixtureAdmission.THE_FIXTURE_IT_ASKED_THIS_PROVIDER_TO_FORGET)));
  }

  @Test
  void a_rule_and_a_preset_this_simulator_cannot_read_are_both_refused_naming_the_rule_to_hold() {
    ActionVerdict.Refused naming =
        ActionVerdict.refused(
            RefusalSubject.noObjectWasNamed(
                ScenarioAdmission.THE_SCENARIO_RULE_IT_ASKED_THIS_PROVIDER_TO_HOLD));
    assertThat(
            new ScenarioForm.Read.Unreadable(
                    List.of(new ScenarioRefusal("delayMs", "not a number")))
                .theRefusalItReports())
        .isEqualTo(naming);
    assertThat(
            new ScenarioForm.Chosen.Unreadable(
                    List.of(new ScenarioRefusal("preset", "not published")))
                .theRefusalItReports())
        .isEqualTo(naming);
  }

  @Test
  void a_refused_rule_records_the_properties_it_would_not_accept_and_the_words_of_each() {
    assertThat(
            ScenarioForm.factsOfARefusal(
                "cancel-hold-success", List.of(new ScenarioRefusal("action.type", "unknown"))))
        .as(
            "a row naming the properties alone tells a tester which fields were refused and"
                + " never what was wrong with them, which is the half they came to the trail"
                + " for")
        .containsEntry("preset", List.of("cancel-hold-success"))
        .containsEntry(ActionFacts.REFUSED_PROPERTIES, List.of("action.type"))
        .containsEntry(
            ActionFacts.REFUSED_BECAUSE, List.of("action.type cannot be applied: unknown"));
    assertThat(ScenarioForm.factsOfARejection(null, "priority must be a whole number"))
        .containsEntry(ActionFacts.REFUSED_BECAUSE, List.of("priority must be a whole number"))
        .doesNotContainKey("preset");
  }

  @Test
  void an_action_the_screens_refused_records_what_was_asked_for_and_the_words_it_was_refused_in() {
    assertThat(ObjectScreen.factsOf("CAPTURE", "12.00", "tick confirm"))
        .containsEntry("action", List.of("CAPTURE"))
        .containsEntry("amount", List.of("12.00"))
        .containsEntry(ActionFacts.REFUSED_BECAUSE, List.of("tick confirm"));
  }

  @Test
  void every_arm_of_a_refusal_subject_is_read_back_on_a_screen_in_words_a_tester_can_act_on() {
    assertThat(AuditScreen.whatARowRefused(null)).isNull();
    assertThat(AuditScreen.whatARowRefused(RefusalSubject.theObject("pi_1")))
        .isEqualTo("the object pi_1");
    assertThat(AuditScreen.whatARowRefused(RefusalSubject.noObjectAnsweredTo("providerObjectId")))
        .isEqualTo("no object of this run answered to providerObjectId");
    assertThat(AuditScreen.whatARowRefused(RefusalSubject.severalObjectsAnsweredTo("orderId", 3)))
        .isEqualTo("3 objects of this run answered to orderId");
    assertThat(AuditScreen.whatARowRefused(RefusalSubject.anObjectItWillNotName("it answers 200")))
        .isEqualTo("an object this provider will not name: it answers 200");
    assertThat(AuditScreen.whatARowRefused(RefusalSubject.noObjectWasNamed("a scenario rule")))
        .isEqualTo("a scenario rule");
  }
}
