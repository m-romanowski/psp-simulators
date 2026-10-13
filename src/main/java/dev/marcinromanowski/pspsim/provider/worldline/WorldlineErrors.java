package dev.marcinromanowski.pspsim.provider.worldline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class WorldlineErrors {

  private WorldlineErrors() {}

  static final String DIRECT_PLATFORM_ERROR = "DIRECT_PLATFORM_ERROR";

  static final String PAYMENT_PLATFORM_ERROR = "PAYMENT_PLATFORM_ERROR";

  static final String IO_ERROR = "IO_ERROR";

  static final String UNKNOWN_PAYMENT_ID = "UNKNOWN_PAYMENT_ID";

  static final String UNKNOWN_TOKEN_ID = "UNKNOWN_TOKEN_ID";

  static final String INVALID_VALUE = "INVALID_VALUE";

  static final String ACCESS_TO_MERCHANT_NOT_ALLOWED = "ACCESS_TO_MERCHANT_NOT_ALLOWED";

  static final String ACTION_NOT_ALLOWED_ON_TRANSACTION = "ACTION_NOT_ALLOWED_ON_TRANSACTION";

  static final String PAYMENT_PRODUCT_NOT_AVAILABLE = "PAYMENT_PRODUCT_NOT_AVAILABLE";

  static final String UNKNOWN_PAYMENT_ID_ERROR_CODE = "50001130";

  static final String UNKNOWN_PAYMENT_ID_DEPRECATED_CODE = "1002";

  static final String INVALID_VALUE_ERROR_CODE = "50001111";

  static final String INVALID_VALUE_DEPRECATED_CODE = "1008";

  static final String THE_CARD_WAS_DECLINED = "30921001";

  static final String THE_THREE_D_SECURE_CHECK_FAILED = "40001134";

  static final String THE_THREE_D_SECURE_CHECK_IS_MISSING = "40001137";

  static final String THE_BRAND_DOES_NOT_MATCH_THE_PAN = "50001054";

  static final List<String> THE_PUBLISHED_DECLINE_CODES =
      List.of(
          THE_CARD_WAS_DECLINED,
          THE_THREE_D_SECURE_CHECK_FAILED,
          THE_THREE_D_SECURE_CHECK_IS_MISSING,
          THE_BRAND_DOES_NOT_MATCH_THE_PAN);

  static final String NO_ERROR_CODE_IS_PUBLISHED_FOR_THIS_ID =
      "provider worldline publishes an errors.id for this case and no errors.errorCode for it"
          + " anywhere, and its own schema requires errorCode on aPIError. This simulator answers"
          + " the published id without an errorCode rather than drawing an eight-digit number of"
          + " its own, because an invented code is indistinguishable from a vendor code to every"
          + " client that logs one, while a missing required member is visibly this simulator's"
          + " gap";

  static final String THE_DECLINE_CODES =
      "provider worldline answers a refusal as errorId plus errors[], and what a rule may name as"
          + " a provider code depends on which operation the rule reaches, because this contract"
          + " puts a refusal in two different places. On POST /v2/{merchantId}/payments a decline"
          + " is HTTP 402 carrying a paymentErrorResponse, and the code is an eight-digit"
          + " errors.errorCode: the published ones are "
          + String.join(", ", THE_PUBLISHED_DECLINE_CODES)
          + ". On the capture and the cancel a refusal is not an error body at all but a published"
          + " status — \"This status is returned when your acquirer refuses the cancellation"
          + " request. The payment reverts to its previous state.\" — so the code a rule names"
          + " there is the numeric statusOutput.statusCode the vendor publishes for it, "
          + WorldlineStatus.CODE_REJECTED_CAPTURE
          + " for REJECTED_CAPTURE and "
          + WorldlineStatus.CODE_CANCELLATION_REJECTED
          + " for CANCELLATION_REJECTED. Neither of those two is terminal: the vendor states that"
          + " a refused capture leaves \"The global status of the transaction ... in"
          + " statusOutput.statusCode=5\" and that a refused cancellation leaves \"The payment"
          + " remains authorised.\"";

  static String theCodesAScenarioMayName() {
    List<String> named = new ArrayList<>(THE_PUBLISHED_DECLINE_CODES);
    named.add(Integer.toString(WorldlineStatus.CODE_REJECTED_CAPTURE));
    named.add(Integer.toString(WorldlineStatus.CODE_CANCELLATION_REJECTED));
    return String.join("|", named);
  }

  static boolean isAMaintenanceRefusalCode(String providerCode) {
    return Integer.toString(WorldlineStatus.CODE_REJECTED_CAPTURE).equals(providerCode)
        || Integer.toString(WorldlineStatus.CODE_CANCELLATION_REJECTED).equals(providerCode);
  }

  static WorldlineJson.JsonObject item(
      @Nullable String errorCode,
      String category,
      @Nullable String deprecatedCode,
      int httpStatusCode,
      @Nullable String id,
      String message,
      @Nullable String propertyName,
      boolean retriable) {
    Map<String, WorldlineJson> member = new LinkedHashMap<>();
    if (errorCode != null) {
      member.put(WorldlineFields.ERROR_CODE, WorldlineJson.text(errorCode));
    }
    member.put(WorldlineFields.CATEGORY, WorldlineJson.text(category));
    if (deprecatedCode != null) {
      member.put(WorldlineFields.CODE, WorldlineJson.text(deprecatedCode));
    }
    member.put(
        WorldlineFields.HTTP_STATUS_CODE, WorldlineJson.number(Integer.toString(httpStatusCode)));
    if (id != null) {
      member.put(WorldlineFields.ID, WorldlineJson.text(id));
    }
    member.put(WorldlineFields.MESSAGE, WorldlineJson.text(message));
    if (propertyName != null) {
      member.put(WorldlineFields.PROPERTY_NAME, WorldlineJson.text(propertyName));
    }
    member.put(WorldlineFields.RETRIABLE, WorldlineJson.flag(retriable));
    return WorldlineJson.object(member);
  }

  static WorldlineJson.JsonObject envelope(String errorId, WorldlineJson.JsonObject item) {
    Map<String, WorldlineJson> envelope = new LinkedHashMap<>();
    envelope.put(WorldlineFields.ERROR_ID, WorldlineJson.text(errorId));
    envelope.put(WorldlineFields.ERRORS, WorldlineJson.array(List.<WorldlineJson>of(item)));
    return WorldlineJson.object(envelope);
  }

  static WorldlineJson.JsonObject paymentEnvelope(
      String errorId, WorldlineJson.JsonObject item, @Nullable WorldlineJson paymentResult) {
    Map<String, WorldlineJson> envelope = new LinkedHashMap<>();
    envelope.put(WorldlineFields.ERROR_ID, WorldlineJson.text(errorId));
    envelope.put(WorldlineFields.ERRORS, WorldlineJson.array(List.<WorldlineJson>of(item)));
    if (paymentResult != null) {
      envelope.put(WorldlineFields.PAYMENT_RESULT, paymentResult);
    }
    return WorldlineJson.object(envelope);
  }
}
