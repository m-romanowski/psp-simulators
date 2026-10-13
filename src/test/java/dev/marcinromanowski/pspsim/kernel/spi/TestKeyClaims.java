package dev.marcinromanowski.pspsim.kernel.spi;

public final class TestKeyClaims {

  public static final KeyClaim NEVER_HELD =
      new KeyClaim.NeverHeldByARequest("this provider claims nothing before an operation runs");

  public static final KeyClaim CLAIMED =
      new KeyClaim.ClaimedBeforeTheOperationRuns(
          "this provider claims the key before the operation runs and refuses a request that"
              + " arrives while it is held");

  private TestKeyClaims() {}
}
