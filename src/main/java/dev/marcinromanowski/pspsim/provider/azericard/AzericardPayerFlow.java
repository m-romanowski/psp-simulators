package dev.marcinromanowski.pspsim.provider.azericard;

import java.util.List;
import org.jspecify.annotations.Nullable;

final class AzericardPayerFlow {

  static final List<String> A_BROWSER_CAN_FOLLOW = List.of("http://", "https://");

  private AzericardPayerFlow() {}

  static @Nullable String callbackTargetOf(AzericardMessage message) {
    return message.backref();
  }

  static @Nullable String returnTargetOf(AzericardMessage message) {
    String merchantUrl = message.merchantUrl();
    if (merchantUrl == null) {
      return null;
    }
    for (String scheme : A_BROWSER_CAN_FOLLOW) {
      if (merchantUrl.startsWith(scheme)) {
        return merchantUrl;
      }
    }
    return null;
  }
}
