package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import org.junit.jupiter.api.Test;

class ObjectActionControlTest {

  private static final TestRunId RUN = TestRunId.of("an-action-a-module-declares");

  private static final String AN_OBJECT = "pay-1";

  private static final String WHAT_IT_HAS_INSTEAD =
      "this module publishes that route and this slice does not serve it, and what it has in place"
          + " of it is the deletion branch of the same route";

  @Test
  void an_action_the_declaration_does_not_offer_is_refused_in_the_modules_words_and_never_asked() {
    ProviderDouble module =
        new ProviderDouble()
            .holding(AN_OBJECT)
            .offeringNo(ObjectAction.REFUND, WHAT_IT_HAS_INSTEAD);

    ObjectActionOutcome outcome = ObjectActionControl.perform(module, aRefund());

    assertThat(outcome).isEqualTo(new ObjectActionOutcome.NotOffered(WHAT_IT_HAS_INSTEAD));
    assertThat(module.theActionsAskedOfIt())
        .as("a module is never asked to perform what its own declaration says it does not offer")
        .isEmpty();
  }

  @Test
  void an_action_the_declaration_offers_is_asked_of_the_module_itself() {
    ProviderDouble module =
        new ProviderDouble()
            .holding(AN_OBJECT)
            .offeringNo(ObjectAction.CANCEL, WHAT_IT_HAS_INSTEAD);

    ObjectActionOutcome outcome = ObjectActionControl.perform(module, aRefund());

    assertThat(outcome).isEqualTo(new ObjectActionOutcome.NoSuchObject());
    assertThat(module.theActionsAskedOfIt()).containsExactly(aRefund());
  }

  private static ObjectActionRequest aRefund() {
    return ObjectActionRequest.of(RUN, AN_OBJECT, ObjectAction.REFUND, "40.00", ObjectOrigin.UI);
  }
}
