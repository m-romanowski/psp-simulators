package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;

final class AzericardRefusals {

  static final String THE_MESSAGE_WAS_REFUSED_BEFORE_ANYTHING_WAS_ADDRESSED =
      "provider azericard refuses this message on the fields it carries, before it looks for the"
          + " payment those fields would have named, so this refusal is about the message and not"
          + " about any object this test run holds";

  static final String THE_MESSAGE_REACHES_NO_TERMINAL =
      "provider azericard decides which terminal's payments a message may reach from the terminal"
          + " it names and the MAC it carries, and refuses a message that reaches no terminal of"
          + " this test run without looking for a payment at all";

  static final String THE_TRACE_NUMBER_IS_ALREADY_CLAIMED =
      "provider azericard holds a payment under the order this message repeats and answers the"
          + " repeat as a duplicate, and its published answer carries neither the gateway reference"
          + " nor the retrieval reference of the payment that holds the order";

  static final String A_SCENARIO_DECLINED_BEFORE_A_PAYMENT_WAS_ADDRESSED =
      "a scenario declined this status lookup before provider azericard looked for the payments the"
          + " order names, so the refusal reports the decline without any payment of this test run"
          + " having been addressed";

  private AzericardRefusals() {}

  static RefusalSubject theMessageWasRefused() {
    return RefusalSubject.noObjectWasNamed(THE_MESSAGE_WAS_REFUSED_BEFORE_ANYTHING_WAS_ADDRESSED);
  }

  static RefusalSubject theMessageReachesNoTerminal() {
    return RefusalSubject.noObjectWasNamed(THE_MESSAGE_REACHES_NO_TERMINAL);
  }
}
