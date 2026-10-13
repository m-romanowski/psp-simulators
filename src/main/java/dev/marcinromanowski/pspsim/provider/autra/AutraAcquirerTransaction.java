package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class AutraAcquirerTransaction {

  private AutraAcquirerTransaction() {}

  static final int DIGITS = 12;

  private static final String PRIMARY = "trackingNumber";

  private static final String KEPT_FOR_COMPATIBILITY = "nsu";

  static @Nullable String asTheAcquirerReportsIt(AutraMerchant merchant, IdGenerator ids) {
    return merchant.acquirerReport().isReported() ? ids.digits(DIGITS) : null;
  }

  static void underBothPublishedNames(Map<String, AutraJson> answer, AutraPayment payment) {
    String reported = payment.trackingNumber();
    if (reported == null) {
      return;
    }
    answer.put(PRIMARY, AutraJson.text(reported));
    answer.put(KEPT_FOR_COMPATIBILITY, AutraJson.text(reported));
  }

  static void underThePrimaryNameAlone(Map<String, AutraJson> answer, AutraPayment payment) {
    String reported = payment.trackingNumber();
    if (reported == null) {
      return;
    }
    answer.put(PRIMARY, AutraJson.text(reported));
  }

  static void asTheInspectorNamesIt(Map<String, String> related, AutraPayment payment) {
    String reported = payment.trackingNumber();
    if (reported != null) {
      related.put(PRIMARY, reported);
    }
  }
}
