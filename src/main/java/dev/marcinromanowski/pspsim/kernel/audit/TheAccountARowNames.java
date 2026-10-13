package dev.marcinromanowski.pspsim.kernel.audit;

import java.util.List;
import org.jspecify.annotations.Nullable;

public final class TheAccountARowNames {

  public static final int LONGEST_IDENTITY_A_ROW_HOLDS = 128;

  private TheAccountARowNames() {}

  public static @Nullable String theIdentityOrNone(@Nullable String named) {
    return named == null || named.isBlank() ? null : named.strip();
  }

  public static @Nullable String amongTheAccountsTheCredentialsMatched(List<String> accountIds) {
    return accountIds.size() == 1 ? accountIds.get(0) : null;
  }

  public static void refuseAnIdentityNoRowCouldCarry(String property, String accountId) {
    if (accountId.isBlank()) {
      throw new IllegalArgumentException(
          property
              + " is blank, and a trail narrowed to a blank account would keep the rows that name"
              + " no account at all rather than the rows of one account");
    }
    if (accountId.length() > LONGEST_IDENTITY_A_ROW_HOLDS) {
      throw new IllegalArgumentException(
          property
              + " must be at most "
              + LONGEST_IDENTITY_A_ROW_HOLDS
              + " characters, which is the widest identity a row holds, so a longer one names an"
              + " account no row could carry");
    }
  }
}
