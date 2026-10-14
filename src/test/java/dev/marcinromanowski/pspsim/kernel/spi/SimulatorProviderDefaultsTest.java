package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckJson;
import dev.marcinromanowski.pspsim.kernel.smoke.CountingIds;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulatorProviderDefaultsTest {

  private static final TestRunId RUN = TestRunId.of("run-a");

  @Test
  void a_module_that_declares_no_contract_check_answers_none_rather_than_nothing_at_all() {
    SimulatorProvider declaringNoCheck = new ProviderDouble();

    assertThat(declaringNoCheck.compatibilityChecks(new CheckJson(), new CountingIds())).isEmpty();
  }

  @Test
  void a_module_that_batches_nothing_of_its_own_answers_many_identifiers_one_at_a_time() {
    SimulatorProvider batchingNothing = new ProviderDouble().holding("pay-1").holding("pay-2");

    List<ProviderObjectState> held =
        batchingNothing.states(RUN, List.of("pay-1", "nothing-of-this-run", "pay-2"));

    assertThat(held)
        .as(
            "a provider added later that never learns to answer in bulk keeps working on the core"
                + " minimum, and what it answers for many identifiers is what it answers for one")
        .containsExactly(
            batchingNothing.state(RUN, "pay-1").orElseThrow(),
            batchingNothing.state(RUN, "pay-2").orElseThrow());
  }

  @Test
  void a_module_that_batches_nothing_of_its_own_answers_in_the_order_it_was_asked_in() {
    SimulatorProvider batchingNothing =
        new ProviderDouble().holding("pay-1").holding("pay-2").holding("pay-3");

    List<ProviderObjectState> held = batchingNothing.states(RUN, List.of("pay-3", "pay-1"));

    assertThat(held)
        .as(TheOrderOneAnswerIsIn.ONE_ANSWER_IS_IN_THE_ORDER_IT_WAS_ASKED_IN)
        .containsExactly(
            batchingNothing.state(RUN, "pay-3").orElseThrow(),
            batchingNothing.state(RUN, "pay-1").orElseThrow());
  }

  @Test
  void a_module_that_batches_nothing_of_its_own_reads_one_object_once_under_both_its_names() {
    ProviderDouble batchingNothing =
        new ProviderDouble().holding("pay-1").mintingAFurtherIdentifierOn("pay-1", "pay-1-00002");

    List<ProviderObjectState> held = batchingNothing.states(RUN, List.of("pay-1", "pay-1-00002"));

    assertThat(held)
        .as(
            "an answer carrying that object once per name would print the same object as two of"
                + " the objects this run holds")
        .hasSize(1);
    assertThat(held.get(0).identity().answersTo("pay-1-00002")).isTrue();
    assertThat(batchingNothing.theIdentifiersItWasReadFor())
        .as(TheOrderOneAnswerIsIn.ONE_OBJECT_IS_READ_ONCE_HOWEVER_MANY_OF_ITS_NAMES_ARE_ASKED)
        .containsExactly("pay-1");
  }

  @Test
  void a_module_that_batches_nothing_of_its_own_reads_an_identifier_it_holds_nothing_under_once() {
    ProviderDouble batchingNothing = new ProviderDouble().holding("pay-1");

    assertThat(batchingNothing.states(RUN, List.of("nothing-of-this-run", "pay-1"))).hasSize(1);
    assertThat(batchingNothing.theIdentifiersItWasReadFor())
        .as(
            "an identifier this run holds nothing under is answered by no object, so there is"
                + " nothing already holding it and the read of it is the only way to find that"
                + " out")
        .containsExactly("nothing-of-this-run", "pay-1");
  }

  @Test
  void a_module_asked_for_no_identifier_at_all_reads_nothing_and_answers_nothing() {
    SimulatorProvider batchingNothing = new ProviderDouble().holding("pay-1");

    assertThat(batchingNothing.states(RUN, List.of())).isEmpty();
  }

  @Test
  void an_object_asked_for_under_a_later_identifier_is_answered_as_the_object_it_is() {
    SimulatorProvider mintingAnother =
        new ProviderDouble().holding("pay-1").mintingAFurtherIdentifierOn("pay-1", "pay-1-00002");

    List<ProviderObjectState> held = mintingAnother.states(RUN, List.of("pay-1-00002"));

    assertThat(held)
        .as(
            "a provider whose object answers to more than one identifier reads it back under a"
                + " later one, and the answer carries the object rather than the identifier it"
                + " was asked for, so no caller has to guess which of the two keys it holds")
        .containsExactly(mintingAnother.state(RUN, "pay-1-00002").orElseThrow());
    assertThat(held.get(0).providerObjectId())
        .isEqualTo(mintingAnother.state(RUN, "pay-1").orElseThrow().providerObjectId());
    assertThat(held.get(0).identity().answersTo("pay-1-00002")).isTrue();
    assertThat(held.get(0).identity().isTheCanonicalMember("pay-1-00002")).isFalse();
  }

  @Test
  void a_module_that_declares_no_follow_up_offers_none_on_any_object() {
    SimulatorProvider declaringNoFollowUp = new ProviderDouble().holding("pay-1");

    assertThat(declaringNoFollowUp.followUps(TestRunId.of("run-a"), "pay-1", Instant.EPOCH))
        .isEmpty();
  }

  @Test
  void
      a_module_that_declares_no_follow_up_refuses_one_asked_directly_in_the_same_words_as_the_core() {
    SimulatorProvider declaringNoFollowUp = new ProviderDouble().holding("pay-1");
    FollowUpRequest settle =
        FollowUpRequest.of(TestRunId.of("run-a"), "pay-1", "settle", ObjectOrigin.CONTROL_API);

    assertThatThrownBy(() -> declaringNoFollowUp.followUp(settle, Instant.EPOCH))
        .isInstanceOf(FollowUpNotOfferedException.class)
        .hasMessage(FollowUpControl.theObjectDoesNotOfferIt(ProviderDouble.ID, "settle"));
  }
}
