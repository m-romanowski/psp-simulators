package dev.marcinromanowski.pspsim.kernel.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class StoryKeySurvivingARenameTest {

  private static final ProviderId PROVIDER = ProviderId.of("twokeys");

  private static final TestRunId RUN = TestRunId.of("renaming-run");

  private static final String THE_FLOW = StoryKey.inSpace("payment", "pay_1");

  private static final String ANOTHER_FLOW = StoryKey.inSpace("payment", "pay_other");

  private static final String CREATED_UNDER = "pay_1";

  private static final String MINTED_BY_THE_CAPTURE = "pay_2";

  private static final String CAPTURE = "capture";

  private static final String REFUND = "refund";

  private static final String CAPTURED = "Captured";

  private final InMemoryScenarioRules rules = new InMemoryScenarioRules();

  private final ScenarioEngine engine = new ScenarioEngine(rules);

  @Test
  void a_capture_then_refund_story_takes_its_second_step_although_the_object_was_renamed() {
    rules.add(step(CAPTURE, ScenarioStory.INITIAL_STATE, CAPTURED));
    rules.add(step(REFUND, CAPTURED, null));

    ScenarioDecision captured = engine.decide(request(THE_FLOW, CREATED_UNDER, CAPTURE));
    ScenarioDecision refunded = engine.decide(request(THE_FLOW, MINTED_BY_THE_CAPTURE, REFUND));

    assertThat(captured).isInstanceOf(ScenarioDecision.Proceed.class);
    assertThat(refunded).isInstanceOf(ScenarioDecision.Proceed.class);
    assertThat(rules.storyStatesOf(PROVIDER, RUN, new StoryKey(StoryKeying.FLOW, THE_FLOW)))
        .containsEntry("checkout", CAPTURED);
  }

  @Test
  void a_refund_of_a_renamed_object_takes_no_step_of_a_story_that_never_captured_it() {
    rules.add(step(CAPTURE, ScenarioStory.INITIAL_STATE, CAPTURED));
    rules.add(step(REFUND, CAPTURED, null));

    engine.decide(request(THE_FLOW, CREATED_UNDER, CAPTURE));

    assertThat(engine.decide(request(ANOTHER_FLOW, MINTED_BY_THE_CAPTURE, REFUND)))
        .isEqualTo(new ScenarioDecision.NoScenario());
  }

  @Test
  void the_identifier_a_request_carries_is_no_part_of_the_key_the_story_stands_on() {
    rules.add(step(CAPTURE, ScenarioStory.INITIAL_STATE, CAPTURED));

    engine.decide(request(THE_FLOW, MINTED_BY_THE_CAPTURE, CAPTURE));

    assertThat(rules.storyStatesOf(PROVIDER, RUN, new StoryKey(StoryKeying.FLOW, THE_FLOW)))
        .containsEntry("checkout", CAPTURED);
    assertThat(
            rules.storyStatesOf(
                PROVIDER,
                RUN,
                new StoryKey(StoryKeying.FLOW, StoryKey.inSpace("payment", "pay_2"))))
        .isEmpty();
  }

  private NewScenarioRule step(String operation, String whenState, @Nullable String thenState) {
    return new NewScenarioRule(
        PROVIDER,
        RUN,
        0,
        ScenarioMatch.anyRequest().naming(operation),
        new ScenarioAction.FixedSuccess(),
        new ScenarioStory("checkout", StoryKeying.FLOW, whenState, thenState));
  }

  private static ScenarioRequest request(
      String storyKey, String providerObjectId, String operation) {
    return new ScenarioRequest(
        PROVIDER,
        RUN,
        storyKey,
        "merchant-1",
        null,
        providerObjectId,
        null,
        null,
        null,
        null,
        operation,
        Map.<String, List<String>>of());
  }
}
