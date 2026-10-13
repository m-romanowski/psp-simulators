package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.scenario.StoryKey;

final class AutraStoryKey {

  private static final String OF_A_PAYMENT = "payment";

  private static final String OF_AN_ACQUIRING_SESSION = "acquiring-session";

  private static final String OF_A_PIX = "pix";

  private static final String OF_A_PIX_SESSION = "pix-session";

  private static final String OF_A_CARD_SESSION = "card-session";

  private AutraStoryKey() {}

  static String ofPayment(String paymentId) {
    return StoryKey.inSpace(OF_A_PAYMENT, paymentId);
  }

  static String ofSession(String documentId) {
    return StoryKey.inSpace(OF_AN_ACQUIRING_SESSION, documentId);
  }

  static String ofPix(String pixId) {
    return StoryKey.inSpace(OF_A_PIX, pixId);
  }

  static String ofPixSession(String documentId) {
    return StoryKey.inSpace(OF_A_PIX_SESSION, documentId);
  }

  static String ofCardSession(String cardSessionId) {
    return StoryKey.inSpace(OF_A_CARD_SESSION, cardSessionId);
  }

  static String theLongestSessionKeyOf(String documentId) {
    return ofSession(documentId);
  }
}
