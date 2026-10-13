package dev.marcinromanowski.pspsim.ui;

public final class Done {

  public static final String ATTRIBUTE = "done";

  public static final String THE_ACCOUNT_IS_CONFIGURED =
      "The account is configured for this test run.";

  public static final String THE_CLOCK_IS_PINNED = "This test run's clock is pinned.";

  public static final String THE_CLOCK_HAS_MOVED_ON = "This test run's clock has moved on.";

  public static final String THE_URL_IS_BOUND = "The URL is bound to this test run.";

  public static final String THE_URL_IS_RELEASED = "The URL is released.";

  public static final String THE_SETTINGS_ARE_SET = "The settings are set for this test run.";

  public static final String THE_PROVIDER_PERFORMED_THE_ACTION =
      "The provider performed the action on this payment.";

  public static final String THE_FOLLOW_UP_IS_PERFORMED =
      "The provider performed the follow-up on this payment.";

  public static final String THE_CALLBACK_IS_SCHEDULED = "The callback is scheduled.";

  public static final String THE_DELIVERY_WAS_ATTEMPTED = "The delivery was attempted.";

  public static final String EVERY_DUE_DELIVERY_WAS_ATTEMPTED =
      "Every delivery this test run's clock made due was attempted.";

  public static final String THE_PAYMENT_METHOD_IS_SEEDED = "The payment method is seeded.";

  public static final String THE_FIXTURE_IS_REMOVED = "The fixture is removed.";

  public static final String THE_RULES_ARE_WRITTEN = "The rules are written.";

  public static final String THE_RULE_IS_DELETED = "The rule is deleted.";

  public static final String THE_COPY_IS_WRITTEN = "The copy of the rule is written.";

  private Done() {}

  public static String thePayerStepIsTaken(String asATesterReadsIt) {
    return "This payer step is taken: " + asATesterReadsIt + ".";
  }

  public static String thePaymentExistsNow(String provider, String providerObjectId) {
    return "This payment exists now: "
        + provider
        + " gave it the id "
        + providerObjectId
        + ", and the audit trail records it as started from a screen rather than by a call from a"
        + " client application.";
  }

  public static String theTestRunIsReset(int providers, int objects, int auditRecords) {
    return "This test run is reset in every provider. It cleared "
        + objects
        + " object(s) and "
        + auditRecords
        + " audit record(s) across "
        + providers
        + " provider(s).";
  }

  public static String theTestRunIsForgotten(
      String testRunId, int registrations, DashboardScreen.Emptied emptied) {
    return "Test run "
        + testRunId
        + " is forgotten. "
        + (registrations > 0
            ? "Its registration is dropped"
            : "Nothing was registered under that name, so there was no registration to drop")
        + ", and emptying it across "
        + emptied.providers()
        + " provider(s) cleared "
        + emptied.objects()
        + " object(s) and "
        + emptied.auditRecords()
        + " audit record(s). Every other test run is untouched.";
  }

  public static String theAccountIsRemoved(String accountId) {
    return "Account "
        + accountId
        + " is removed. Its provider row, its callback targets and its identity are gone; the"
        + " test run and every other account of it are untouched, and a callback still waiting"
        + " on the removed account is now acknowledged by the merchant's 2xx like any other.";
  }
}
