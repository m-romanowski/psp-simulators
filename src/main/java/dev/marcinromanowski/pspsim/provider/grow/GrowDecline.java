package dev.marcinromanowski.pspsim.provider.grow;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

enum GrowDecline {
  EXPIRED_CARD("expiredCard", 103, "כרטיס פג תוקף.", GrowDeclineFinality.FINAL),

  STOLEN_CARD("stolenCard", 103, "כרטיס גנוב - עסקה לא מאושרת", GrowDeclineFinality.FINAL),

  BLOCKED_CARD("blockedCard", 103, "כרטיס חסום - עסקה לא מאושרת", GrowDeclineFinality.FINAL),

  CREDIT_COMPANY_DID_NOT_APPROVE(
      "creditCompanyDidNotApprove",
      103,
      "חברת האשראי לא אשרה את העסקה",
      GrowDeclineFinality.NOT_FINAL),

  CARD_INACTIVE_OR_EXPIRED(
      "cardInactiveOrExpired",
      451,
      "כרטיס האשראי אינו פעיל/פג תוקף, נא לפנות ללקוח לקבלת כרטיס חדש",
      GrowDeclineFinality.FINAL),

  CARD_BLOCKED_BY_THE_CREDIT_COMPANY(
      "cardBlockedByTheCreditCompany",
      452,
      "חברת האשראי חסמה את הכרטיס, נא לפנות ללקוח לבדיקה",
      GrowDeclineFinality.FINAL),

  SYSTEM_BLOCK("systemBlock", 453, "חסימת מערכת", GrowDeclineFinality.FINAL);

  private final String scenarioCode;
  private final int id;
  private final String message;
  private final GrowDeclineFinality finality;

  GrowDecline(String scenarioCode, int id, String message, GrowDeclineFinality finality) {
    this.scenarioCode = scenarioCode;
    this.id = id;
    this.message = message;
    this.finality = finality;
  }

  GrowError error() {
    return new GrowError(id, message);
  }

  static Optional<GrowDecline> of(String scenarioCode) {
    for (GrowDecline decline : values()) {
      if (decline.scenarioCode.equals(scenarioCode)) {
        return Optional.of(decline);
      }
    }
    return Optional.empty();
  }

  static String theReasonsAScenarioMayName() {
    List<String> names = new ArrayList<>();
    for (GrowDecline decline : values()) {
      names.add(decline.scenarioCode);
    }
    return String.join("|", names);
  }

  static String theReasonsDescribed() {
    List<String> described = new ArrayList<>();
    for (GrowDecline decline : values()) {
      described.add(decline.scenarioCode + " (" + decline.finality.publishedAs() + ")");
    }
    return String.join(", ", described);
  }
}
