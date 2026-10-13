package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRefusal;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record WhatTheScenariosScreenCameBackWith(
    Form form,
    ScenarioForm rule,
    @Nullable String flow,
    @Nullable String thePriorityTheFlowWasAskedWith,
    @Nullable String document,
    List<ScenarioRefusal> refusals) {

  public static final String ATTRIBUTE = "submitted";

  public static final String THE_FLOW_FORM_IS_HEADED = "Start from a ready-made flow";

  public static final String THE_HAND_WRITTEN_FORM_IS_HEADED = "Write one rule by hand";

  public static final String THE_DOCUMENT_FORM_IS_HEADED = "Write rules back from a document";

  public static final String THE_FLOW_ITSELF = "preset";

  public static final List<String> THE_BOXES_A_READY_MADE_FLOW_IS_ASKED_FOR_WITH =
      List.of(THE_FLOW_ITSELF, "priority");

  public static final String NOTHING_WAS_WRITTEN = "Nothing was written. ";

  public static final String THIS_PROVIDER_DOES_NOT_EXPRESS_THE_FLOW_THAT_WAS_CHOSEN =
      NOTHING_WAS_WRITTEN
          + "The property named here is the ready-made flow itself, and this provider does not"
          + " express the one you chose, so no value corrected beside it writes that flow: the"
          + " chooser in the form headed "
          + THE_FLOW_FORM_IS_HEADED
          + " below still has that flow chosen and says, for every flow it offers, whether this"
          + " provider can express it, so choose one it can, or write the rule you need by hand in"
          + " the form headed "
          + THE_HAND_WRITTEN_FORM_IS_HEADED
          + " under it.";

  public static final String A_BOX_ON_THE_FLOW_FORM_HOLDS_WHAT_WAS_NAMED =
      NOTHING_WAS_WRITTEN
          + "The form headed "
          + THE_FLOW_FORM_IS_HEADED
          + " below still holds what you typed, with the flow you chose still chosen, and the"
          + " property named here is a box on it, so correct it there and ask for that flow again.";

  public static final String NO_BOX_ON_THE_FLOW_FORM_HOLDS_WHAT_WAS_NAMED =
      NOTHING_WAS_WRITTEN
          + "The property named here is no box on the form headed "
          + THE_FLOW_FORM_IS_HEADED
          + " below: it belongs to a rule this provider expands that flow into, which is this"
          + " provider's own answer rather than anything you typed. The flow you chose is still"
          + " chosen there, so choose another flow, or write the rule you need by hand in the form"
          + " headed "
          + THE_HAND_WRITTEN_FORM_IS_HEADED
          + " under it.";

  public static final String A_BOX_ON_THE_HAND_WRITTEN_FORM_NAMES_WHAT_WAS_NAMED =
      NOTHING_WAS_WRITTEN
          + "The form headed "
          + THE_HAND_WRITTEN_FORM_IS_HEADED
          + " below is open and still holds what you typed, and every box on it names beside it"
          + " the property a refusal calls it, so find the property named here and correct it"
          + " there. A property named here that form draws no box for is a fact this provider"
          + " never carries, and that form lists the facts it never carries.";

  public static final String THE_PROPERTY_NAMED_IS_A_FIELD_OF_THE_DOCUMENT =
      NOTHING_WAS_WRITTEN
          + "The property named here is a field of the document you pasted rather than a box on"
          + " this screen, and the form headed "
          + THE_DOCUMENT_FORM_IS_HEADED
          + " below still holds what you pasted, so correct that property inside the document and"
          + " write it back again.";

  public static final String A_READY_MADE_FLOW_WAS_NOT_WRITTEN =
      "This ready-made flow was not written";

  public static final String ONE_RULE_WAS_NOT_WRITTEN = "This rule was not written";

  public static final String THE_RULES_OF_A_DOCUMENT_WERE_NOT_WRITTEN =
      "These rules were not written";

  public enum Form {
    NOTHING_WAS_SUBMITTED,
    A_READY_MADE_FLOW,
    ONE_RULE_BY_HAND,
    A_DOCUMENT
  }

  public static final String A_REFUSAL_NAMES_THE_FORM_IT_CAME_FROM =
      "the sentence this screen prints beside a refusal is chosen by the form that was submitted,"
          + " so a refusal carrying no form would print no sentence at all and leave a tester"
          + " reading a property with nowhere to correct it; these refusals named no form: ";

  public WhatTheScenariosScreenCameBackWith {
    refusals = List.copyOf(refusals);
    if (form == Form.NOTHING_WAS_SUBMITTED && !refusals.isEmpty()) {
      throw new IllegalArgumentException(
          A_REFUSAL_NAMES_THE_FORM_IT_CAME_FROM + ScenarioRefusal.theWordsOf(refusals));
    }
  }

  public static WhatTheScenariosScreenCameBackWith nothing() {
    return new WhatTheScenariosScreenCameBackWith(
        Form.NOTHING_WAS_SUBMITTED, ScenarioForm.empty(), null, null, null, List.of());
  }

  public static WhatTheScenariosScreenCameBackWith aRuleWrittenByHand(ScenarioForm rule) {
    return new WhatTheScenariosScreenCameBackWith(
        Form.ONE_RULE_BY_HAND, rule, null, null, null, List.of());
  }

  public static WhatTheScenariosScreenCameBackWith aReadyMadeFlow(
      @Nullable String flow, @Nullable String priority) {
    return new WhatTheScenariosScreenCameBackWith(
        Form.A_READY_MADE_FLOW, ScenarioForm.empty(), flow, priority, null, List.of());
  }

  public static WhatTheScenariosScreenCameBackWith aDocument(@Nullable String document) {
    return new WhatTheScenariosScreenCameBackWith(
        Form.A_DOCUMENT, ScenarioForm.empty(), null, null, document, List.of());
  }

  public WhatTheScenariosScreenCameBackWith andTheseRefusals(List<ScenarioRefusal> refused) {
    return new WhatTheScenariosScreenCameBackWith(
        form, rule, flow, thePriorityTheFlowWasAskedWith, document, refused);
  }

  public boolean nothingWasWritten() {
    return !refusals.isEmpty();
  }

  public boolean theHandWrittenRuleFormIsTheOneToCorrect() {
    return nothingWasWritten() && form == Form.ONE_RULE_BY_HAND;
  }

  public boolean itIsTheFlow(String offered) {
    return offered.equals(flow);
  }

  public String whatWasNotWritten() {
    return switch (form) {
      case NOTHING_WAS_SUBMITTED -> "";
      case A_READY_MADE_FLOW -> A_READY_MADE_FLOW_WAS_NOT_WRITTEN;
      case ONE_RULE_BY_HAND -> ONE_RULE_WAS_NOT_WRITTEN;
      case A_DOCUMENT -> THE_RULES_OF_A_DOCUMENT_WERE_NOT_WRITTEN;
    };
  }

  public String whatToDoNext() {
    return switch (form) {
      case NOTHING_WAS_SUBMITTED -> "";
      case A_READY_MADE_FLOW -> whatToDoAboutAFlowThisScreenDidNotWrite();
      case ONE_RULE_BY_HAND -> A_BOX_ON_THE_HAND_WRITTEN_FORM_NAMES_WHAT_WAS_NAMED;
      case A_DOCUMENT -> THE_PROPERTY_NAMED_IS_A_FIELD_OF_THE_DOCUMENT;
    };
  }

  private String whatToDoAboutAFlowThisScreenDidNotWrite() {
    if (theFlowItselfIsNamed()) {
      return THIS_PROVIDER_DOES_NOT_EXPRESS_THE_FLOW_THAT_WAS_CHOSEN;
    }
    if (everyPropertyNamedIsABoxOnTheFlowForm()) {
      return A_BOX_ON_THE_FLOW_FORM_HOLDS_WHAT_WAS_NAMED;
    }
    return NO_BOX_ON_THE_FLOW_FORM_HOLDS_WHAT_WAS_NAMED;
  }

  private boolean theFlowItselfIsNamed() {
    for (ScenarioRefusal refusal : refusals) {
      if (THE_FLOW_ITSELF.equals(refusal.property())) {
        return true;
      }
    }
    return false;
  }

  private boolean everyPropertyNamedIsABoxOnTheFlowForm() {
    for (ScenarioRefusal refusal : refusals) {
      if (!THE_BOXES_A_READY_MADE_FLOW_IS_ASKED_FOR_WITH.contains(refusal.property())) {
        return false;
      }
    }
    return true;
  }
}
