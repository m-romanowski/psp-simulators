package dev.marcinromanowski.pspsim.kernel.spi;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class AccountProperty {

  private static final String ACCOUNTS = "accounts[";

  private static final String CONFIGURATION = "].configuration.";

  private static final String IDENTITY = "].accountId";

  static final String ACCOUNT_ID = "accountId";

  private static final Pattern A_CONFIGURED_FIELD =
      Pattern.compile("^accounts\\[[0-9]+]\\.configuration\\.(\\S+)");

  private static final Pattern AN_ACCOUNT_IDENTITY =
      Pattern.compile("^accounts\\[[0-9]+]\\.accountId(\\s|$)");

  private AccountProperty() {}

  public static String prefixOf(int index) {
    return ACCOUNTS + index + CONFIGURATION;
  }

  public static String identityOf(int index) {
    return ACCOUNTS + index + IDENTITY;
  }

  public static String theRefusalWithTheFieldNamed(String refusal, String name) {
    Matcher configured = A_CONFIGURED_FIELD.matcher(refusal);
    if (configured.find()) {
      return name + refusal.substring(configured.end());
    }
    Matcher identity = AN_ACCOUNT_IDENTITY.matcher(refusal);
    if (identity.find()) {
      return name + refusal.substring(identity.start(1));
    }
    return refusal;
  }

  public static @Nullable String theFieldNamedBy(String refusal) {
    Matcher configured = A_CONFIGURED_FIELD.matcher(refusal);
    if (configured.find()) {
      return configured.group(1);
    }
    return AN_ACCOUNT_IDENTITY.matcher(refusal).find() ? ACCOUNT_ID : null;
  }
}
