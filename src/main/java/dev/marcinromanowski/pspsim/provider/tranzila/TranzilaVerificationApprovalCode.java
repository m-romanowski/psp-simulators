package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import java.util.List;
import org.jspecify.annotations.Nullable;

enum TranzilaVerificationApprovalCode {
  AS_THE_CODE_TABLE_PUBLISHES_IT(TranzilaResponseCodes.OPERATION_COMPLETED),

  AS_THE_VENDOR_ANSWERED_IT(TranzilaResponseCodes.APPROVED);

  static final String SETTING = "tranzila.verification-approval-code";

  static final String WHY_THERE_ARE_TWO =
      "provider tranzila answers an approved J2 or J5 with a code its own publications do not"
          + " agree on. The published code table glosses 777 as \"operation completed (success"
          + " code for operations in which no transaction is recorded, including J2 and J5)\", and"
          + " the vendor's written answer to a merchant questionnaire says 000";

  private final String code;

  TranzilaVerificationApprovalCode(String code) {
    this.code = code;
  }

  String code() {
    return code;
  }

  static List<AcceptedValue> acceptedValues() {
    return List.of(
        new AcceptedValue(
            AS_THE_CODE_TABLE_PUBLISHES_IT.code,
            "an approved J2 or J5 answers operation completed, as the published code table"
                + " glosses it"),
        new AcceptedValue(
            AS_THE_VENDOR_ANSWERED_IT.code,
            "an approved J2 or J5 answers transaction approved, as the vendor wrote to a"
                + " merchant"));
  }

  static TranzilaVerificationApprovalCode stored(String code) {
    for (TranzilaVerificationApprovalCode configured : values()) {
      if (configured.code.equals(code)) {
        return configured;
      }
    }
    throw new IllegalStateException(
        "the store holds " + code + " as a verification approval code, which no value names");
  }

  static TranzilaVerificationApprovalCode of(String property, @Nullable String declared) {
    if (declared == null || declared.isBlank()) {
      return AS_THE_CODE_TABLE_PUBLISHES_IT;
    }
    for (TranzilaVerificationApprovalCode configured : values()) {
      if (configured.code.equals(declared)) {
        return configured;
      }
    }
    throw new IllegalArgumentException(
        property
            + " names neither of the two approval codes this simulator answers an approved"
            + " verification with for provider tranzila. "
            + WHY_THERE_ARE_TWO
            + ". The two values are "
            + AS_THE_CODE_TABLE_PUBLISHES_IT.code
            + ", the default, and "
            + AS_THE_VENDOR_ANSWERED_IT.code);
  }
}
