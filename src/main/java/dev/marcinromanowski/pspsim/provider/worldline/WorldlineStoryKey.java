package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.scenario.StoryKey;

final class WorldlineStoryKey {

  private static final String OF_A_PAYMENT = "payment";

  private static final String OF_A_HOSTED_TOKENIZATION = "hosted-tokenization";

  static final String A_MAINTENANCE_KEY_IS_THE_IDENTIFIER_THE_REQUEST_CARRIED =
      "a story about a capture or a cancel stands on the payment identifier the request carried,"
          + " and not on the identifier the payment was created under. That is deliberate and it"
          + " is what provider worldline's vocabulary declares as"
          + " AN_IDENTIFIER_THE_OBJECT_MAY_REPLACE: every maintenance operation mints a new"
          + " payment.id, so a second capture addressed by the identifier the first one minted"
          + " lands on a different key from the first. Resolving the key back to the canonical"
          + " identifier would make the key stable and would make the declaration false, so the"
          + " key is left where the request put it and the kernel refuses a flow-keyed story that"
          + " spans one of these two operations";

  private WorldlineStoryKey() {}

  static String ofPayment(String identifierTheRequestCarried) {
    return StoryKey.inSpace(OF_A_PAYMENT, identifierTheRequestCarried);
  }

  static String ofHostedTokenization(String hostedTokenizationId) {
    return StoryKey.inSpace(OF_A_HOSTED_TOKENIZATION, hostedTokenizationId);
  }
}
