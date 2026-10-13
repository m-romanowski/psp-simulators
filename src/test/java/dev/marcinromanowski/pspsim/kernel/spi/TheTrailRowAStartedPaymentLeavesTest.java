package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.audit.AuditedAction;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TheTrailRowAStartedPaymentLeavesTest {

  private static final ProviderId APROVIDER = ProviderId.of("aprovider");

  private static final TestRunId RUN = TestRunId.of("a-run");

  private static final String ACCOUNT = "merchant-1";

  private static final String A_HOLD = "aHold";

  private static final String THE_ROUTE_A_SUITE_STARTS_ONE_ON =
      "/__simulator/aprovider/originations";

  private static final String THE_ROUTE_A_SCREEN_STARTS_ONE_ON = "/ui/objects/start";

  private static final int THE_STATUS_A_SUITE_IS_ANSWERED_WITH = 201;

  private static final int THE_STATUS_A_SCREEN_IS_ANSWERED_WITH = 302;

  private static final String WHAT_A_HOSTED_PAGE_LEAVES_BEHIND = "a hosted page to send a payer to";

  @Test
  void a_started_payment_is_recorded_with_the_way_the_account_the_values_and_the_object() {
    Map<String, String> typed = new LinkedHashMap<>();
    typed.put("sum", "10.00");

    AuditedAction row =
        TheTrailRowAStartedPaymentLeaves.of(
            APROVIDER,
            THE_ROUTE_A_SUITE_STARTS_ONE_ON,
            asked(typed, ObjectOrigin.CONTROL_API),
            new OriginationOutcome.Originated("770001", WHAT_A_HOSTED_PAGE_LEAVES_BEHIND, "scn_1"),
            THE_STATUS_A_SUITE_IS_ANSWERED_WITH);

    assertThat(row.provider()).isEqualTo(APROVIDER);
    assertThat(row.testRunId()).isEqualTo(RUN);
    assertThat(row.origin()).isEqualTo(ObjectOrigin.CONTROL_API);
    assertThat(row.method()).isEqualTo("POST");
    assertThat(row.path()).isEqualTo(THE_ROUTE_A_SUITE_STARTS_ONE_ON);
    assertThat(row.httpStatus()).isEqualTo(THE_STATUS_A_SUITE_IS_ANSWERED_WITH);
    assertThat(row.providerObjectId()).isEqualTo("770001");
    assertThat(row.accountId()).isEqualTo(ACCOUNT);
    assertThat(row.scenarioId()).isEqualTo("scn_1");
    assertThat(row.facts())
        .containsExactly(
            Map.entry(TheTrailRowAStartedPaymentLeaves.STARTED_AS, List.of(A_HOLD)),
            Map.entry(TheTrailRowAStartedPaymentLeaves.ACCOUNT_ID, List.of(ACCOUNT)),
            Map.entry("sum", List.of("10.00")),
            Map.entry(TheTrailRowAStartedPaymentLeaves.PROVIDER_OBJECT_ID, List.of("770001")));
  }

  @Test
  void one_screen_and_one_suite_starting_the_same_payment_are_recorded_the_same_way_throughout() {
    OriginationOutcome refusedByTheProvider =
        new OriginationOutcome.RefusedByTheProvider("803 no such page");

    AuditedAction clicked =
        TheTrailRowAStartedPaymentLeaves.of(
            APROVIDER,
            THE_ROUTE_A_SCREEN_STARTS_ONE_ON,
            asked(Map.of(), ObjectOrigin.UI),
            refusedByTheProvider,
            THE_STATUS_A_SCREEN_IS_ANSWERED_WITH);
    AuditedAction called =
        TheTrailRowAStartedPaymentLeaves.of(
            APROVIDER,
            THE_ROUTE_A_SUITE_STARTS_ONE_ON,
            asked(Map.of(), ObjectOrigin.CONTROL_API),
            refusedByTheProvider,
            THE_STATUS_A_SUITE_IS_ANSWERED_WITH);

    assertThat(clicked.httpStatus())
        .as(
            "a tester compares a step they clicked against the same step their suite made, and"
                + " the screen used to file a provider refusal under one status and the control"
                + " api under another")
        .isEqualTo(called.httpStatus())
        .isEqualTo(422);
    assertThat(clicked.facts()).isEqualTo(called.facts());
    assertThat(clicked.refusal()).isEqualTo(called.refusal());
    assertThat(clicked.facts())
        .containsEntry(
            TheTrailRowAStartedPaymentLeaves.REFUSED_BECAUSE, List.of("803 no such page"))
        .doesNotContainKey(TheTrailRowAStartedPaymentLeaves.PROVIDER_OBJECT_ID);
  }

  @Test
  void a_payment_a_rule_of_this_run_answered_instead_names_that_rule_in_the_column_it_belongs_in() {
    AuditedAction row = rowOf(new OriginationOutcome.AnsweredByAScenarioInstead("scn_2"));

    assertThat(row.scenarioId()).isEqualTo("scn_2");
    assertThat(row.httpStatus()).isEqualTo(422);
    assertThat(row.facts())
        .as(
            "the rule that decided a call is a column of every row of this trail, and the screen"
                + " used to write it into a fact of its own that no row of the control api"
                + " carried, so a suite filtering the trail by that fact found half of them")
        .doesNotContainKey("answeredByAScenarioInstead")
        .containsEntry(
            TheTrailRowAStartedPaymentLeaves.REFUSED_BECAUSE,
            List.of(
                TheTrailRowAStartedPaymentLeaves.A_RULE_OF_THIS_RUN_ANSWERED_IT_INSTEAD + "scn_2"));
  }

  @Test
  void a_provider_that_starts_no_payment_of_that_kind_is_recorded_as_a_request_it_rejected() {
    AuditedAction row = rowOf(new OriginationOutcome.NotOffered("it publishes no such call"));

    assertThat(row.httpStatus())
        .as(
            "a way of starting a payment this provider publishes nowhere is a request this"
                + " simulator rejects, which is what the control api always answered, and the"
                + " screen filed the same answer as a refusal of the provider's own")
        .isEqualTo(400);
    assertThat(row.facts())
        .containsEntry(
            TheTrailRowAStartedPaymentLeaves.REFUSED_BECAUSE, List.of("it publishes no such call"))
        .doesNotContainKey("notOffered");
  }

  @Test
  void a_payment_asked_for_on_an_account_this_run_no_longer_configures_is_recorded_as_not_found() {
    AuditedAction row = rowOf(new OriginationOutcome.NoSuchAccount());

    assertThat(row.httpStatus()).isEqualTo(404);
    assertThat(row.facts())
        .as(
            "a row a tester finds after a reset took the account away mid-run says why the call"
                + " was turned away, because a not-found status beside the payment it named"
                + " leaves them to guess between an account that is gone and a payment that"
                + " never existed")
        .containsEntry(
            TheTrailRowAStartedPaymentLeaves.REFUSED_BECAUSE,
            List.of(TheTrailRowAStartedPaymentLeaves.THIS_RUN_CONFIGURES_NO_SUCH_ACCOUNT));
    assertThat(row.refusal())
        .isEqualTo(
            RefusalSubject.noObjectWasNamed(
                TheTrailRowAStartedPaymentLeaves.THE_PAYMENT_IT_ASKED_THIS_PROVIDER_TO_START));
  }

  @Test
  void the_fields_of_a_started_payment_are_recorded_in_the_order_the_request_carries_them() {
    LinkedHashMap<String, String> backToFront = new LinkedHashMap<>();
    backToFront.put("notifyUrl", "https://merchant.example/hook");
    backToFront.put("phone", "0505503144");
    backToFront.put("fullName", "Israel Israeli");
    backToFront.put("cancelUrl", "https://merchant.example/cancel");
    backToFront.put("successUrl", "https://merchant.example/thanks");
    backToFront.put("description", "a seat in row F");
    backToFront.put("sum", "120.50");

    AuditedAction row =
        TheTrailRowAStartedPaymentLeaves.of(
            APROVIDER,
            THE_ROUTE_A_SUITE_STARTS_ONE_ON,
            asked(backToFront, ObjectOrigin.CONTROL_API),
            new OriginationOutcome.Originated("770001", WHAT_A_HOSTED_PAGE_LEAVES_BEHIND, null),
            THE_STATUS_A_SUITE_IS_ANSWERED_WITH);

    assertThat(row.facts().keySet())
        .as(
            "the trail lists what a tester typed under one key per field, and a tester compares"
                + " that list against the answer beside it, so the fields are recorded in the"
                + " order the request carries them and in no other")
        .startsWith(
            TheTrailRowAStartedPaymentLeaves.STARTED_AS,
            TheTrailRowAStartedPaymentLeaves.ACCOUNT_ID)
        .containsSequence(backToFront.keySet().toArray(new String[0]));
  }

  @Test
  void a_request_turned_away_before_the_provider_is_recorded_with_what_it_was_turned_away_with() {
    AuditedAction row =
        TheTrailRowAStartedPaymentLeaves.ofARequestTurnedAwayBeforeTheProviderWasReached(
            APROVIDER,
            TestRunId.UNKNOWN,
            ObjectOrigin.UI,
            THE_ROUTE_A_SCREEN_STARTS_ONE_ON,
            A_HOLD,
            ACCOUNT,
            "nothing is sent for sum");

    assertThat(row.testRunId()).isEqualTo(TestRunId.UNKNOWN);
    assertThat(row.origin()).isEqualTo(ObjectOrigin.UI);
    assertThat(row.method()).isEqualTo("POST");
    assertThat(row.httpStatus()).isEqualTo(400);
    assertThat(row.accountId()).isEqualTo(ACCOUNT);
    assertThat(row.providerObjectId()).isNull();
    assertThat(row.verdict()).isInstanceOf(ActionVerdict.Refused.class);
    assertThat(row.facts())
        .containsExactly(
            Map.entry(TheTrailRowAStartedPaymentLeaves.STARTED_AS, List.of(A_HOLD)),
            Map.entry(TheTrailRowAStartedPaymentLeaves.ACCOUNT_ID, List.of(ACCOUNT)),
            Map.entry(
                TheTrailRowAStartedPaymentLeaves.REFUSED_BECAUSE,
                List.of("nothing is sent for sum")));
  }

  @Test
  void a_payment_that_started_is_the_one_outcome_no_refusal_is_recorded_for() {
    assertThat(
            TheTrailRowAStartedPaymentLeaves.theVerdictOf(
                new OriginationOutcome.Originated("770001", WHAT_A_HOSTED_PAGE_LEAVES_BEHIND, null),
                THE_STATUS_A_SCREEN_IS_ANSWERED_WITH))
        .isEqualTo(ActionVerdict.applied(THE_STATUS_A_SCREEN_IS_ANSWERED_WITH));
  }

  private static AuditedAction rowOf(OriginationOutcome outcome) {
    return TheTrailRowAStartedPaymentLeaves.of(
        APROVIDER,
        THE_ROUTE_A_SUITE_STARTS_ONE_ON,
        asked(Map.of(), ObjectOrigin.CONTROL_API),
        outcome,
        THE_STATUS_A_SUITE_IS_ANSWERED_WITH);
  }

  private static OriginationRequest asked(Map<String, String> values, ObjectOrigin askedBy) {
    return OriginationRequest.of(RUN, ACCOUNT, A_HOLD, values, askedBy);
  }
}
