package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRefusal;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class WhatTheScenariosScreenCameBackWithTest {

  private static final Pattern A_CONTROL_A_TESTER_FILLS_IN = Pattern.compile("name=\"([^\"]+)\"");

  private static final ScenarioRefusal THE_PRIORITY_BOX =
      new ScenarioRefusal("priority", "must be a whole number");

  private static final ScenarioRefusal THE_FLOW_ITSELF =
      new ScenarioRefusal(
          "preset", "this provider reaches its outcome in the answer to the request that made it");

  private static final ScenarioRefusal A_RULE_THE_PROVIDER_EXPANDED_THE_FLOW_INTO =
      new ScenarioRefusal(
          "action.settlesAfterMs", "this simulator will not hold an answer back that long");

  @Test
  void a_flow_refused_over_a_box_on_the_flow_form_sends_the_tester_to_that_box() {
    WhatTheScenariosScreenCameBackWith asked =
        WhatTheScenariosScreenCameBackWith.aReadyMadeFlow("card-add-success", "high")
            .andTheseRefusals(List.of(THE_PRIORITY_BOX));

    assertThat(asked.nothingWasWritten()).isTrue();
    assertThat(asked.whatWasNotWritten())
        .isEqualTo(WhatTheScenariosScreenCameBackWith.A_READY_MADE_FLOW_WAS_NOT_WRITTEN);
    assertThat(asked.whatToDoNext())
        .as(
            "the hand-written rule form was named instead, so a tester who corrected the box that"
                + " sentence pointed at wrote one rule by hand rather than asking for the flow"
                + " again")
        .isEqualTo(WhatTheScenariosScreenCameBackWith.A_BOX_ON_THE_FLOW_FORM_HOLDS_WHAT_WAS_NAMED);
    assertThat(asked.theHandWrittenRuleFormIsTheOneToCorrect()).isFalse();
    assertThat(asked.thePriorityTheFlowWasAskedWith()).isEqualTo("high");
    assertThat(asked.rule())
        .as("a flow form holds no hand-written rule, so no box of that form draws what was typed")
        .isEqualTo(ScenarioForm.empty());
  }

  @Test
  void a_flow_this_provider_cannot_express_sends_the_tester_to_the_chooser_that_says_so() {
    WhatTheScenariosScreenCameBackWith asked =
        WhatTheScenariosScreenCameBackWith.aReadyMadeFlow("webhook-out-of-order", "")
            .andTheseRefusals(List.of(THE_FLOW_ITSELF));

    assertThat(asked.whatToDoNext())
        .as(
            "a refusal of the flow itself is satisfied by choosing another flow and by nothing a"
                + " tester types, and the chooser is the only place that says which flows this"
                + " provider cannot express")
        .isEqualTo(
            WhatTheScenariosScreenCameBackWith
                .THIS_PROVIDER_DOES_NOT_EXPRESS_THE_FLOW_THAT_WAS_CHOSEN);
    assertThat(asked.itIsTheFlow("webhook-out-of-order")).isTrue();
    assertThat(asked.itIsTheFlow("card-add-success")).isFalse();
  }

  @Test
  void a_flow_refused_over_a_rule_the_provider_expanded_it_into_promises_no_box_at_all() {
    WhatTheScenariosScreenCameBackWith asked =
        WhatTheScenariosScreenCameBackWith.aReadyMadeFlow("psp-5xx-then-success", "0")
            .andTheseRefusals(
                List.of(THE_PRIORITY_BOX, A_RULE_THE_PROVIDER_EXPANDED_THE_FLOW_INTO));

    assertThat(asked.whatToDoNext())
        .as(
            "a property this form draws no box for is this provider's own answer for that flow,"
                + " and a sentence promising a box for it leaves a tester looking for one that is"
                + " not there")
        .isEqualTo(WhatTheScenariosScreenCameBackWith.NO_BOX_ON_THE_FLOW_FORM_HOLDS_WHAT_WAS_NAMED);
  }

  @Test
  void a_rule_refused_by_hand_opens_the_hand_written_form_and_keeps_what_was_typed() {
    ScenarioForm typed = aRuleWhosePriorityReads("urgent");
    WhatTheScenariosScreenCameBackWith asked =
        WhatTheScenariosScreenCameBackWith.aRuleWrittenByHand(typed)
            .andTheseRefusals(List.of(THE_PRIORITY_BOX));

    assertThat(asked.whatWasNotWritten())
        .isEqualTo(WhatTheScenariosScreenCameBackWith.ONE_RULE_WAS_NOT_WRITTEN);
    assertThat(asked.whatToDoNext())
        .isEqualTo(
            WhatTheScenariosScreenCameBackWith.A_BOX_ON_THE_HAND_WRITTEN_FORM_NAMES_WHAT_WAS_NAMED);
    assertThat(asked.theHandWrittenRuleFormIsTheOneToCorrect()).isTrue();
    assertThat(asked.rule()).isSameAs(typed);
    assertThat(asked.itIsTheFlow("card-add-success"))
        .as("no flow was chosen, so the chooser comes back with none of its flows chosen")
        .isFalse();
  }

  @Test
  void a_document_refused_sends_the_tester_into_the_document_it_pasted_rather_than_a_box() {
    WhatTheScenariosScreenCameBackWith asked =
        WhatTheScenariosScreenCameBackWith.aDocument("{\"provider\":\"grow\"}")
            .andTheseRefusals(List.of(new ScenarioRefusal("provider", "must name this provider")));

    assertThat(asked.whatWasNotWritten())
        .isEqualTo(WhatTheScenariosScreenCameBackWith.THE_RULES_OF_A_DOCUMENT_WERE_NOT_WRITTEN);
    assertThat(asked.whatToDoNext())
        .isEqualTo(
            WhatTheScenariosScreenCameBackWith.THE_PROPERTY_NAMED_IS_A_FIELD_OF_THE_DOCUMENT);
    assertThat(asked.theHandWrittenRuleFormIsTheOneToCorrect()).isFalse();
    assertThat(asked.document()).isEqualTo("{\"provider\":\"grow\"}");
  }

  @Test
  void a_screen_nobody_has_submitted_a_form_on_says_nothing_was_not_written() {
    WhatTheScenariosScreenCameBackWith nothing = WhatTheScenariosScreenCameBackWith.nothing();

    assertThat(nothing.nothingWasWritten()).isFalse();
    assertThat(nothing.whatWasNotWritten()).isEmpty();
    assertThat(nothing.whatToDoNext()).isEmpty();
    assertThat(nothing.theHandWrittenRuleFormIsTheOneToCorrect()).isFalse();
    assertThat(nothing.rule()).isEqualTo(ScenarioForm.empty());
    assertThat(nothing.document()).isNull();
  }

  @Test
  void a_refusal_carrying_no_form_is_refused_rather_than_drawn_with_no_sentence_beside_it() {
    WhatTheScenariosScreenCameBackWith nothing = WhatTheScenariosScreenCameBackWith.nothing();
    List<ScenarioRefusal> refused = List.of(THE_PRIORITY_BOX);

    assertThatThrownBy(() -> nothing.andTheseRefusals(refused))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            WhatTheScenariosScreenCameBackWith.A_REFUSAL_NAMES_THE_FORM_IT_CAME_FROM)
        .hasMessageContaining("priority");
  }

  @Test
  void the_boxes_the_sentence_counts_as_correctable_are_the_ones_the_flow_form_draws() {
    assertThat(theBoxesTheFlowFormOnTheShippedScreenDraws())
        .as(
            "the sentence beside a refusal of this form promises a box for every property it"
                + " names, and a box this list claims the form draws while the form draws none"
                + " leaves a tester looking for one that is not there")
        .containsExactlyInAnyOrderElementsOf(
            WhatTheScenariosScreenCameBackWith.THE_BOXES_A_READY_MADE_FLOW_IS_ASKED_FOR_WITH);
  }

  private static List<String> theBoxesTheFlowFormOnTheShippedScreenDraws() {
    String markup = theScreenThisBuildShips();
    int heading = markup.indexOf("THE_FLOW_FORM_IS_HEADED");
    assertThat(heading)
        .as("the shipped screen heads its flow form with the one constant that names it")
        .isNotNegative();
    int opened = markup.indexOf("<form", heading);
    int closed = markup.indexOf("</form>", opened);
    assertThat(opened).isNotNegative();
    assertThat(closed).isGreaterThan(opened);
    List<String> drawn = new ArrayList<>();
    Matcher named = A_CONTROL_A_TESTER_FILLS_IN.matcher(markup.substring(opened, closed));
    while (named.find()) {
      drawn.add(named.group(1));
    }
    return List.copyOf(drawn);
  }

  private static String theScreenThisBuildShips() {
    try {
      return Files.readString(Path.of("src", "main", "resources", "templates", "scenarios.html"));
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }

  private static ScenarioForm aRuleWhosePriorityReads(String priority) {
    return new ScenarioForm(
        priority, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null);
  }
}
